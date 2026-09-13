// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.vitals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.changes.core.data.CalendarRepository
import br.com.colman.changes.core.data.NewCalendarEvent
import br.com.colman.changes.core.model.CalendarCategory
import br.com.colman.changes.core.model.CalendarEvent
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.RecordedTime
import br.com.colman.changes.core.model.RecurrenceRule
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.model.TimeZoneProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

private const val STOP_TIMEOUT_MILLIS = 5_000L
private const val DAILY_INTERVAL = 1

// Único campo de `DomainError` que este formulário pode receber do `CalendarRepository`: título
// vazio. Qualquer outro (hoje só "recurrenceRule", já recusado antes por validação local) cai no
// intervalo, como fallback.
private const val FIELD_TITLE = "title"

/**
 * Rotinas de exercício (ADR 0011): eventos manuais do calendário com `category = EXERCISE` e
 * recorrência diária ou semanal. Lista, cria, edita e exclui pelo `CalendarRepository`; a agenda, o
 * calendário e os lembretes já expandem a recorrência sozinhos.
 */
class RoutinesViewModel(
    private val calendar: CalendarRepository,
    private val clock: Clock,
    private val timeZones: TimeZoneProvider,
) : ViewModel() {

    private val form = MutableStateFlow<RoutineFormUiState?>(null)
    private val effectsChannel = Channel<RoutinesEffect>(Channel.BUFFERED)
    private var lastDeletedId: Uuid? = null

    /** Evento carregado ao editar: guarda `description`, `sourceType` e `completedAt` para o `update`. */
    private var loadedEvent: CalendarEvent? = null

    val effects: Flow<RoutinesEffect> = effectsChannel.receiveAsFlow()

    val state: StateFlow<RoutinesUiState> =
        combine(calendar.observeByCategory(CalendarCategory.EXERCISE), form) { events, formState ->
            RoutinesUiState(
                isLoading = false,
                routines = events.mapNotNull { it.toRoutineUiStateOrNull() },
                form = formState,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), RoutinesUiState())

    fun onEvent(event: RoutinesUiEvent) {
        when (event) {
            RoutinesUiEvent.AddRequested -> beginAdd()
            is RoutinesUiEvent.EditRequested -> beginEdit(event.routineId)
            is RoutinesUiEvent.DeleteRequested -> delete(event.routineId)
            RoutinesUiEvent.UndoDeleteRequested -> undoDelete()
            RoutinesUiEvent.FormSaved -> save()
            RoutinesUiEvent.FormDismissed -> dismiss()
            else -> updateForm { applyFormEvent(it, event) }
        }
    }

    private fun applyFormEvent(current: RoutineFormUiState, event: RoutinesUiEvent): RoutineFormUiState =
        when (event) {
            is RoutinesUiEvent.FormActivityChanged -> current.copy(activity = event.text, activityError = false)
            is RoutinesUiEvent.FormFrequencyChanged -> current.withFrequency(event.frequency)
            is RoutinesUiEvent.FormWeeklyDayToggled -> current.withDayToggled(event.day)
            is RoutinesUiEvent.FormIntervalChanged -> current.copy(intervalText = event.text, intervalError = false)
            is RoutinesUiEvent.FormStartDateChanged -> current.copy(startDate = event.date)
            is RoutinesUiEvent.FormTimeChanged -> current.withTime(event.time)
            is RoutinesUiEvent.FormReminderChanged -> current.copy(reminder = event.reminder)
            else -> current
        }

    /** Trocar para semanal já marca o dia da semana de hoje, como pede a tela; nunca fica sem dia. */
    private fun RoutineFormUiState.withFrequency(frequency: RoutineFrequency): RoutineFormUiState {
        val days = if (frequency == RoutineFrequency.WEEKLY && weeklyDays.isEmpty()) {
            setOf(today().dayOfWeek)
        } else {
            weeklyDays
        }
        return copy(frequency = frequency, weeklyDays = days, weeklyDaysError = false)
    }

    private fun RoutineFormUiState.withDayToggled(day: DayOfWeek): RoutineFormUiState {
        val days = if (day in weeklyDays) weeklyDays - day else weeklyDays + day
        return copy(weeklyDays = days, weeklyDaysError = false)
    }

    /** Sem hora o evento vira dia inteiro e não guarda lembrete: tirar a hora também zera o lembrete. */
    private fun RoutineFormUiState.withTime(time: LocalTime?): RoutineFormUiState =
        copy(time = time, reminder = if (time == null) RoutineReminderOption.NONE else reminder)

    private fun beginAdd() {
        val startDate = today()
        form.value = RoutineFormUiState(
            editingId = null,
            activity = "",
            frequency = RoutineFrequency.DAILY,
            weeklyDays = setOf(startDate.dayOfWeek),
            intervalText = "1",
            startDate = startDate,
            time = null,
            reminder = RoutineReminderOption.NONE,
        )
        loadedEvent = null
    }

    private fun beginEdit(routineId: Uuid) = viewModelScope.launch {
        val event = calendar.get(routineId) ?: return@launch
        val recurrence = event.recurrence ?: return@launch
        val frequency = recurrence.frequency.toRoutineFrequency() ?: return@launch
        loadedEvent = event
        form.value = RoutineFormUiState(
            editingId = event.id,
            activity = event.title,
            frequency = frequency,
            weeklyDays = recurrence.byDay.ifEmpty { setOf(event.start.localDate.dayOfWeek) },
            intervalText = recurrence.interval.toString(),
            startDate = event.start.localDate,
            time = if (event.isAllDay) null else event.start.localDateTime.time,
            reminder = reminderOptionFor(event.reminderMinutesBefore),
        )
    }

    private fun updateForm(transform: (RoutineFormUiState) -> RoutineFormUiState) {
        form.value = form.value?.let(transform)
    }

    private fun dismiss() {
        form.value = null
        loadedEvent = null
    }

    private fun save() {
        val current = form.value ?: return
        val activityBlank = current.activity.isBlank()
        val weeklyDaysBlank = current.frequency == RoutineFrequency.WEEKLY && current.weeklyDays.isEmpty()
        val interval = parseInterval(current)
        val intervalInvalid = current.frequency == RoutineFrequency.WEEKLY && interval == null
        if (activityBlank || weeklyDaysBlank || intervalInvalid) {
            form.value = current.copy(
                activityError = activityBlank,
                weeklyDaysError = weeklyDaysBlank,
                intervalError = intervalInvalid,
            )
            return
        }
        viewModelScope.launch {
            val result = persist(current, interval ?: DAILY_INTERVAL)
            form.value = when (result) {
                is Result.Success -> null
                is Result.Failure -> current.withError(result.error)
            }
        }
    }

    /** Inteiro positivo; sem casa decimal, ao contrário da duração de sessão (guideline 16). */
    private fun parseInterval(current: RoutineFormUiState): Int? {
        val value = current.intervalText.toIntOrNull() ?: return null
        return value.takeIf { it > 0 }
    }

    private fun RoutineFormUiState.withError(error: DomainError): RoutineFormUiState {
        val cleared = copy(activityError = false, weeklyDaysError = false, intervalError = false)
        return if ((error as? DomainError.Invalid)?.field == FIELD_TITLE) {
            cleared.copy(activityError = true)
        } else {
            cleared.copy(intervalError = true)
        }
    }

    private suspend fun persist(draft: RoutineFormUiState, interval: Int): Result<CalendarEvent> {
        val recurrence = buildRecurrence(draft, interval)
        val start = startInstant(draft)
        val activity = draft.activity.trim()
        val reminderMinutes = draft.time?.let { draft.reminder.minutesBefore }
        val editing = loadedEvent
        return if (editing == null) {
            calendar.create(
                NewCalendarEvent(
                    title = activity,
                    description = null,
                    start = start,
                    end = null,
                    isAllDay = draft.time == null,
                    category = CalendarCategory.EXERCISE,
                    reminderMinutesBefore = reminderMinutes,
                    recurrence = recurrence,
                ),
            )
        } else {
            calendar.update(
                editing.copy(
                    title = activity,
                    start = RecordedTime.of(start, timeZones.current()),
                    isAllDay = draft.time == null,
                    reminderMinutesBefore = reminderMinutes,
                    recurrence = recurrence,
                ),
            )
        }
    }

    private fun buildRecurrence(draft: RoutineFormUiState, interval: Int): RecurrenceRule = when (draft.frequency) {
        RoutineFrequency.DAILY -> RecurrenceRule(RecurrenceRule.Frequency.DAILY, interval = DAILY_INTERVAL)
        RoutineFrequency.WEEKLY ->
            RecurrenceRule(RecurrenceRule.Frequency.WEEKLY, interval = interval, byDay = draft.weeklyDays)
    }

    /** Sem hora grava meia-noite local: o repositório normaliza na criação, mas não na edição. */
    private fun startInstant(draft: RoutineFormUiState): Instant {
        val time = draft.time ?: LocalTime(0, 0)
        return LocalDateTime(draft.startDate, time).toInstant(timeZones.current())
    }

    private fun delete(routineId: Uuid) = viewModelScope.launch {
        calendar.delete(routineId)
        lastDeletedId = routineId
        effectsChannel.send(RoutinesEffect.ShowUndoDelete)
    }

    private fun undoDelete() = viewModelScope.launch {
        lastDeletedId?.let { calendar.restore(it) }
        lastDeletedId = null
    }

    private fun today(): LocalDate = clock.now().toLocalDateTime(timeZones.current()).date

    private fun CalendarEvent.toRoutineUiStateOrNull(): RoutineUiState? {
        val recurrence = recurrence ?: return null
        val frequency = recurrence.frequency.toRoutineFrequency() ?: return null
        return RoutineUiState(
            id = id,
            activity = title,
            frequency = frequency,
            interval = recurrence.interval,
            weeklyDays = recurrence.byDay,
            time = if (isAllDay) null else start.localDateTime.time,
        )
    }
}

/** `MONTHLY`/`YEARLY` não têm campo nesta tela: um evento assim não é uma rotina para esta lista. */
private fun RecurrenceRule.Frequency.toRoutineFrequency(): RoutineFrequency? = when (this) {
    RecurrenceRule.Frequency.DAILY -> RoutineFrequency.DAILY
    RecurrenceRule.Frequency.WEEKLY -> RoutineFrequency.WEEKLY
    RecurrenceRule.Frequency.MONTHLY, RecurrenceRule.Frequency.YEARLY -> null
}

private fun reminderOptionFor(minutesBefore: Int?): RoutineReminderOption =
    RoutineReminderOption.entries.firstOrNull { it.minutesBefore == minutesBefore } ?: RoutineReminderOption.NONE
