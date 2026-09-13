// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.vitals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.changes.core.data.ExerciseRepository
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.ExerciseIntensity
import br.com.colman.changes.core.model.ExerciseSession
import br.com.colman.changes.core.model.RecordedTime
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.model.TimeZoneProvider
import br.com.colman.changes.ui.chart.ChartPoint
import br.com.colman.changes.ui.format.Formatters
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.floor
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

private const val STOP_TIMEOUT_MILLIS = 5_000L

/** Últimas semanas do resumo semanal (Seção 7.4). */
private const val WEEKS = 8

// Nomes de campo devolvidos por `ExerciseRepository.exerciseError`: mapeiam o `DomainError` para o
// campo certo do formulário. Qualquer outro (hoje só "durationMinutes") cai na duração.
private const val FIELD_ACTIVITY = "activity"
private const val FIELD_OCCURRED_AT = "occurredAt"

/**
 * Tela de Exercício (Seção 7.4): sessões livres e resumo semanal em minutos, como fato, sem meta.
 * Adicionar/editar/excluir com desfazer.
 */
class ExerciseViewModel(
    private val exercises: ExerciseRepository,
    private val clock: Clock,
    private val timeZones: TimeZoneProvider,
) : ViewModel() {

    private val form = MutableStateFlow<ExerciseFormUiState?>(null)
    private val effectsChannel = Channel<ExerciseEffect>(Channel.BUFFERED)
    private var lastDeletedId: Uuid? = null

    val effects: Flow<ExerciseEffect> = effectsChannel.receiveAsFlow()

    val state: StateFlow<ExerciseUiState> = combine(exercises.observeAll(), form) { sessions, formState ->
        val weekly = exercises.weeklyMinutes(today(), WEEKS)
        ExerciseUiState(
            isLoading = false,
            sessions = sessions.map { it.toUiState() },
            weeklyMinutes = weekly.map { WeekMinutesUiState(it.weekStart, it.minutes) },
            weeklyChartPoints = weekly.map { ChartPoint(it.weekStart.toEpochDays().toDouble(), it.minutes.toDouble()) },
            form = formState,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ExerciseUiState())

    fun onEvent(event: ExerciseUiEvent) {
        when (event) {
            ExerciseUiEvent.AddRequested -> beginAdd()
            is ExerciseUiEvent.EditRequested -> beginEdit(event.sessionId)
            is ExerciseUiEvent.DeleteRequested -> delete(event.sessionId)
            ExerciseUiEvent.UndoDeleteRequested -> undoDelete()
            ExerciseUiEvent.FormSaved -> save()
            ExerciseUiEvent.FormDismissed -> form.value = null
            else -> updateForm { applyFormEvent(it, event) }
        }
    }

    private fun applyFormEvent(
        current: ExerciseFormUiState,
        event: ExerciseUiEvent,
    ): ExerciseFormUiState = when (event) {
        is ExerciseUiEvent.FormActivityChanged -> current.copy(activity = event.text, activityError = false)
        is ExerciseUiEvent.FormDurationChanged -> current.copy(durationText = event.text, durationError = false)
        is ExerciseUiEvent.FormIntensityChanged -> current.copy(intensity = event.intensity)
        is ExerciseUiEvent.FormDateChanged -> current.copy(date = event.date, dateError = false)
        is ExerciseUiEvent.FormNotesChanged -> current.copy(notes = event.text)
        else -> current
    }

    private fun beginAdd() {
        form.value = ExerciseFormUiState(
            editingId = null,
            activity = "",
            durationText = "",
            intensity = ExerciseIntensity.LIGHT,
            date = today(),
            notes = "",
        )
    }

    private fun beginEdit(sessionId: Uuid) {
        val session = state.value.sessions.firstOrNull { it.id == sessionId } ?: return
        form.value = ExerciseFormUiState(
            editingId = session.id,
            activity = session.activity,
            durationText = session.durationMinutes.toString(),
            intensity = session.intensity,
            date = session.date,
            notes = session.notes.orEmpty(),
        )
    }

    private fun updateForm(transform: (ExerciseFormUiState) -> ExerciseFormUiState) {
        form.value = form.value?.let(transform)
    }

    private fun save() {
        val current = form.value ?: return
        val duration = parseDuration(current.durationText)
        val activityBlank = current.activity.isBlank()
        if (activityBlank || duration == null) {
            form.value = current.copy(activityError = activityBlank, durationError = duration == null)
            return
        }
        viewModelScope.launch {
            val result = persist(current, duration)
            form.value = when (result) {
                is Result.Success -> null
                is Result.Failure -> current.withError(result.error)
            }
        }
    }

    /** Minutos inteiros maiores que zero; aceita decimal zero ("30,0" ou "30.0") do teclado decimal. */
    private fun parseDuration(text: String): Int? {
        val value = Formatters.parseNumber(text) ?: return null
        if (value <= 0.0 || value != floor(value)) return null
        return value.toInt()
    }

    /**
     * Marca o campo certo do erro de domínio (Seção 5): a recusa pode vir da atividade, da duração
     * ou da data (`occurredAt` no futuro), nunca sempre da duração.
     */
    private fun ExerciseFormUiState.withError(error: DomainError): ExerciseFormUiState {
        val cleared = copy(activityError = false, durationError = false, dateError = false)
        return when ((error as? DomainError.Invalid)?.field) {
            FIELD_ACTIVITY -> cleared.copy(activityError = true)
            FIELD_OCCURRED_AT -> cleared.copy(dateError = true)
            else -> cleared.copy(durationError = true)
        }
    }

    private suspend fun persist(draft: ExerciseFormUiState, duration: Int): Result<ExerciseSession> {
        val notes = draft.notes.trim().ifBlank { null }
        val occurredAt = instantFor(draft.date)
        val activity = draft.activity.trim()
        return if (draft.editingId == null) {
            exercises.create(activity, duration, draft.intensity, occurredAt, notes)
        } else {
            exercises.update(
                ExerciseSession(
                    id = draft.editingId,
                    activity = activity,
                    durationMinutes = duration,
                    intensity = draft.intensity,
                    occurredAt = RecordedTime.of(occurredAt, timeZones.current()),
                    notes = notes,
                ),
            )
        }
    }

    private fun delete(sessionId: Uuid) = viewModelScope.launch {
        exercises.delete(sessionId)
        lastDeletedId = sessionId
        effectsChannel.send(ExerciseEffect.ShowUndoDelete)
    }

    private fun undoDelete() = viewModelScope.launch {
        lastDeletedId?.let { exercises.restore(it) }
        lastDeletedId = null
    }

    private fun today(): LocalDate = clock.now().toLocalDateTime(timeZones.current()).date

    /** Combina a data escolhida com a hora atual, evitando que "hoje" vire um instante futuro por fuso. */
    private fun instantFor(date: LocalDate): Instant {
        val currentTime = clock.now().toLocalDateTime(timeZones.current()).time
        return LocalDateTime(date, currentTime).toInstant(timeZones.current())
    }

    private fun ExerciseSession.toUiState(): ExerciseSessionUiState = ExerciseSessionUiState(
        id = id,
        activity = activity,
        durationMinutes = durationMinutes,
        intensity = intensity,
        date = occurredAt.localDate,
        notes = notes,
    )
}
