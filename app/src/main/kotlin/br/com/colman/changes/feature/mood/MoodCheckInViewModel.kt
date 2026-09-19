// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.mood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.changes.core.data.MoodRepository
import br.com.colman.changes.core.model.MoodLog
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.model.TimeZoneProvider
import br.com.colman.changes.ui.format.Formatters
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Check-in diário de saúde mental (Seção 7.7): uma entrada por dia, humor e energia obrigatórios, o
 * resto opcional. Salvar sempre chama [MoodRepository.upsert], que reaproveita o registro do dia
 * (critério 7.7.1) em vez de criar um novo. Nenhum campo deste estado é calculado a partir da nota
 * (critério 7.7.2).
 */
class MoodCheckInViewModel(
    private val moodRepository: MoodRepository,
    private val clock: Clock,
    private val timeZones: TimeZoneProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(MoodCheckInUiState())
    val state: StateFlow<MoodCheckInUiState> = _state.asStateFlow()

    private val effectsChannel = Channel<MoodCheckInEffect>(Channel.BUFFERED)
    val effects: Flow<MoodCheckInEffect> = effectsChannel.receiveAsFlow()

    private var existingId: Uuid? = null

    /** Seção 9: instantâneo do formulário no último carregamento ou salvamento, para comparar contra o atual. */
    private var loadedSnapshot: MoodCheckInUiState? = null

    fun onEvent(event: MoodCheckInUiEvent) {
        when (event) {
            is MoodCheckInUiEvent.Load -> load(event.epochDay)
            is MoodCheckInUiEvent.MoodChanged ->
                _state.update { it.copy(mood = event.value, moodError = false).withUnsavedFlag() }
            is MoodCheckInUiEvent.EnergyChanged ->
                _state.update { it.copy(energy = event.value, energyError = false).withUnsavedFlag() }
            is MoodCheckInUiEvent.FeelingChanged -> updateFeeling(event)
            is MoodCheckInUiEvent.SleepHoursChanged -> updateSleepHours(event.text)
            is MoodCheckInUiEvent.NoteChanged -> _state.update { it.copy(note = event.text).withUnsavedFlag() }
            is MoodCheckInUiEvent.TagsChanged -> _state.update { it.copy(tagsText = event.text).withUnsavedFlag() }
            MoodCheckInUiEvent.Save -> save()
        }
    }

    private fun updateSleepHours(text: String) {
        _state.update { it.copy(sleepHoursText = text, sleepHoursError = false).withUnsavedFlag() }
    }

    private fun updateFeeling(event: MoodCheckInUiEvent.FeelingChanged) {
        _state.update {
            when (event) {
                is MoodCheckInUiEvent.ReliefChanged -> it.copy(relief = event.value)
                is MoodCheckInUiEvent.IrritabilityChanged -> it.copy(irritability = event.value)
                is MoodCheckInUiEvent.EmotionalIntensityChanged -> it.copy(emotionalIntensity = event.value)
                is MoodCheckInUiEvent.AnxietyChanged -> it.copy(anxiety = event.value)
                is MoodCheckInUiEvent.DysphoriaChanged -> it.copy(dysphoria = event.value)
            }.withUnsavedFlag()
        }
    }

    private fun load(epochDay: Long?) = viewModelScope.launch {
        val date = epochDay?.let(LocalDate::fromEpochDays) ?: today()
        val existing = moodRepository.observe(date).first()
        existingId = existing?.id
        _state.value = existing.toUiState(date)
        captureBaseline()
    }

    /** Seção 9: o formulário recém carregado (ou salvo) passa a ser a referência sem alteração pendente. */
    private fun captureBaseline() {
        loadedSnapshot = _state.value.formSnapshot()
        _state.update { it.copy(hasUnsavedChanges = false) }
    }

    /** Só os campos do formulário importam para detectar alteração pendente (Seção 9). */
    private fun MoodCheckInUiState.formSnapshot(): MoodCheckInUiState = copy(
        isLoading = false,
        moodError = false,
        energyError = false,
        sleepHoursError = false,
        hasUnsavedChanges = false,
    )

    private fun MoodCheckInUiState.withUnsavedFlag(): MoodCheckInUiState {
        val loaded = loadedSnapshot ?: return copy(hasUnsavedChanges = false)
        return copy(hasUnsavedChanges = formSnapshot() != loaded)
    }

    private fun today(): LocalDate = clock.now().toLocalDateTime(timeZones.current()).date

    private fun MoodLog?.toUiState(date: LocalDate) = MoodCheckInUiState(
        isLoading = false,
        date = date,
        mood = this?.mood,
        energy = this?.energy,
        relief = this?.relief,
        irritability = this?.irritability,
        emotionalIntensity = this?.emotionalIntensity,
        anxiety = this?.anxiety,
        dysphoria = this?.dysphoria,
        sleepHoursText = this?.sleepHours?.let { Formatters.number(it) }.orEmpty(),
        note = this?.note.orEmpty(),
        tagsText = this?.tags.orEmpty().joinToString(", "),
    )

    private fun save() {
        val current = _state.value
        val mood = current.mood
        val energy = current.energy
        val sleepHours = Formatters.parseNumber(current.sleepHoursText)
        val sleepInvalid = current.sleepHoursText.isNotBlank() && sleepHours == null
        if (mood == null || energy == null || sleepInvalid) {
            _state.update {
                it.copy(moodError = mood == null, energyError = energy == null, sleepHoursError = sleepInvalid)
            }
            return
        }
        persist(current, mood, energy, sleepHours)
    }

    private fun persist(current: MoodCheckInUiState, mood: Int, energy: Int, sleepHours: Double?) {
        viewModelScope.launch {
            val draft = MoodLog(
                id = existingId ?: Uuid.random(),
                date = current.date,
                mood = mood,
                energy = energy,
                relief = current.relief,
                irritability = current.irritability,
                emotionalIntensity = current.emotionalIntensity,
                anxiety = current.anxiety,
                dysphoria = current.dysphoria,
                sleepHours = sleepHours,
                note = current.note.trim().ifBlank { null },
                tags = current.tagsText.split(","),
            )
            when (val result = moodRepository.upsert(draft)) {
                is Result.Success -> {
                    existingId = result.value.id
                    captureBaseline()
                    effectsChannel.send(MoodCheckInEffect.Saved)
                }
                is Result.Failure -> _state.update { it.copy(sleepHoursError = true) }
            }
        }
    }
}
