// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.changes.core.data.CalendarRepository
import br.com.colman.changes.core.data.NewCalendarEvent
import br.com.colman.changes.core.model.CalendarEvent
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.EventSourceType
import br.com.colman.changes.core.model.RecordedTime
import br.com.colman.changes.core.model.RecurrenceRule
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.model.TimeZoneProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

private const val INVALID_NUMBER = -1

/**
 * Criar/editar evento manual do calendário (Seção 7.9): recorrência simples, lembrete, marcar como
 * concluído e excluir com desfazer. Doses (previstas e registradas) não passam por aqui.
 */
class EventEditViewModel(
    private val calendarRepository: CalendarRepository,
    private val clock: Clock,
    private val timeZones: TimeZoneProvider,
) : ViewModel() {

    private fun now(): LocalDateTime = clock.now().toLocalDateTime(timeZones.current())

    private val _state = MutableStateFlow(EventEditUiState(startDate = now().date, startTime = now().time))
    val state: StateFlow<EventEditUiState> = _state.asStateFlow()

    private val effectsChannel = Channel<EventEditEffect>(Channel.BUFFERED)
    val effects: Flow<EventEditEffect> = effectsChannel.receiveAsFlow()

    private var eventId: Uuid? = null
    private var loadedCompletedAt: RecordedTime? = null

    fun onEvent(event: EventEditUiEvent) {
        when (event) {
            is EventEditUiEvent.Load -> load(event.eventId, event.epochDay)
            is EventEditUiEvent.FieldChanged -> _state.update(event.apply)
            EventEditUiEvent.ToggleCompleted -> _state.update { it.copy(isCompleted = !it.isCompleted) }
            EventEditUiEvent.Save -> save()
            EventEditUiEvent.RequestDelete -> _state.update { it.copy(showDeleteConfirm = true) }
            EventEditUiEvent.CancelDelete -> _state.update { it.copy(showDeleteConfirm = false) }
            EventEditUiEvent.ConfirmDelete -> delete()
            EventEditUiEvent.UndoDelete -> undoDelete()
            EventEditUiEvent.Back -> navigateBack()
        }
    }

    private fun load(id: String?, epochDay: Long?) = viewModelScope.launch {
        val uuid = id?.let(Uuid::parse)
        eventId = uuid
        val event = uuid?.let { calendarRepository.get(it) }
        when {
            id != null && event == null ->
                _state.update { it.copy(isLoading = false, error = DomainError.NotFound("event", id)) }
            event == null -> _state.update { populatedForNew(it, epochDay) }
            else -> _state.update { populatedFrom(it, event) }
        }
    }

    private fun populatedForNew(current: EventEditUiState, epochDay: Long?): EventEditUiState {
        val date = epochDay?.let(LocalDate::fromEpochDays) ?: now().date
        return current.copy(isLoading = false, isNew = true, startDate = date, startTime = now().time)
    }

    private fun populatedFrom(current: EventEditUiState, event: CalendarEvent): EventEditUiState {
        loadedCompletedAt = event.completedAt
        val recurrence = event.recurrence
        return current.copy(
            isLoading = false,
            isNew = false,
            title = event.title,
            description = event.description.orEmpty(),
            category = event.category,
            isAllDay = event.isAllDay,
            startDate = event.start.localDate,
            startTime = event.start.localDateTime.time,
            endDate = event.end?.localDate,
            endTime = event.end?.localDateTime?.time,
            reminder = reminderOptionFor(event.reminderMinutesBefore),
            recurrenceFrequency = recurrence?.frequency.toUiFrequency(),
            recurrenceInterval = recurrence?.interval?.toString() ?: "1",
            recurrenceByDay = recurrence?.byDay.orEmpty(),
            recurrenceEndOption = recurrenceEndOptionOf(recurrence),
            recurrenceUntil = recurrence?.until,
            recurrenceCount = recurrence?.count?.toString().orEmpty(),
            isCompleted = event.completedAt != null,
        )
    }

    private fun save() = viewModelScope.launch {
        val result = persist(_state.value)
        when (result) {
            is Result.Success -> effectsChannel.send(EventEditEffect.NavigateBack)
            is Result.Failure -> _state.update { it.copy(error = result.error) }
        }
    }

    /**
     * `update` não grava conclusão (só `setCompleted` faz isso, Seção 7.9): depois de criar ou
     * atualizar com sucesso, o estado de conclusão é sempre sincronizado à parte.
     */
    private suspend fun persist(current: EventEditUiState): Result<CalendarEvent> {
        val id = eventId
        val result = if (id == null) createEvent(current) else updateEvent(id, current)
        if (result is Result.Success) {
            calendarRepository.setCompleted(result.value.id, completedInstant(current))
        }
        return result
    }

    private suspend fun createEvent(current: EventEditUiState): Result<CalendarEvent> = calendarRepository.create(
        NewCalendarEvent(
            title = current.title,
            description = current.description.ifBlank { null },
            start = startInstant(current),
            end = endInstant(current),
            isAllDay = current.isAllDay,
            category = current.category,
            reminderMinutesBefore = current.reminder.minutesBefore,
            recurrence = buildRecurrence(current),
        ),
    )

    private suspend fun updateEvent(id: Uuid, current: EventEditUiState): Result<CalendarEvent> {
        val zone = timeZones.current()
        val end = endInstant(current)
        return calendarRepository.update(
            CalendarEvent(
                id = id,
                title = current.title,
                description = current.description.ifBlank { null },
                start = RecordedTime.of(startInstant(current), zone),
                end = end?.let { RecordedTime.of(it, zone) },
                isAllDay = current.isAllDay,
                category = current.category,
                sourceType = EventSourceType.MANUAL,
                sourceId = null,
                reminderMinutesBefore = current.reminder.minutesBefore,
                recurrence = buildRecurrence(current),
                completedAt = completedInstant(current)?.let { RecordedTime.of(it, zone) },
            ),
        )
    }

    /** Dia inteiro sempre grava meia-noite local: o repositório normaliza na criação, mas não na edição. */
    private fun startInstant(current: EventEditUiState): Instant {
        val time = if (current.isAllDay) LocalTime(0, 0) else current.startTime
        return LocalDateTime(current.startDate, time).toInstant(timeZones.current())
    }

    private fun endInstant(current: EventEditUiState): Instant? {
        val endDate = current.endDate ?: return null
        val time = if (current.isAllDay) LocalTime(0, 0) else current.endTime ?: current.startTime
        return LocalDateTime(endDate, time).toInstant(timeZones.current())
    }

    /** Preserva o instante já gravado ao reafirmar uma conclusão existente, em vez de trocar por agora. */
    private fun completedInstant(current: EventEditUiState): Instant? = when {
        !current.isCompleted -> null
        loadedCompletedAt != null -> loadedCompletedAt?.instant
        else -> clock.now()
    }

    private fun buildRecurrence(current: EventEditUiState): RecurrenceRule? {
        val frequency = current.recurrenceFrequency.toRuleFrequency() ?: return null
        return RecurrenceRule(
            frequency = frequency,
            interval = current.recurrenceInterval.toIntOrNull() ?: INVALID_NUMBER,
            byDay = if (frequency == RecurrenceRule.Frequency.WEEKLY) current.recurrenceByDay else emptySet(),
            until = if (current.recurrenceEndOption == RecurrenceEndOption.ON_DATE) current.recurrenceUntil else null,
            count = if (current.recurrenceEndOption == RecurrenceEndOption.AFTER_COUNT) {
                current.recurrenceCount.toIntOrNull() ?: INVALID_NUMBER
            } else {
                null
            },
        )
    }

    private fun delete() = viewModelScope.launch {
        val id = eventId ?: return@launch
        calendarRepository.delete(id)
        _state.update { it.copy(showDeleteConfirm = false) }
        effectsChannel.send(EventEditEffect.ShowUndoDelete)
    }

    private fun undoDelete() = viewModelScope.launch {
        eventId?.let { calendarRepository.restore(it) }
    }

    private fun navigateBack() = viewModelScope.launch { effectsChannel.send(EventEditEffect.NavigateBack) }
}

private fun reminderOptionFor(minutesBefore: Int?): ReminderOption =
    ReminderOption.entries.firstOrNull { it.minutesBefore == minutesBefore } ?: ReminderOption.NONE

private fun EventRecurrenceFrequency.toRuleFrequency(): RecurrenceRule.Frequency? = when (this) {
    EventRecurrenceFrequency.NONE -> null
    EventRecurrenceFrequency.DAILY -> RecurrenceRule.Frequency.DAILY
    EventRecurrenceFrequency.WEEKLY -> RecurrenceRule.Frequency.WEEKLY
    EventRecurrenceFrequency.MONTHLY -> RecurrenceRule.Frequency.MONTHLY
    EventRecurrenceFrequency.YEARLY -> RecurrenceRule.Frequency.YEARLY
}

private fun RecurrenceRule.Frequency?.toUiFrequency(): EventRecurrenceFrequency = when (this) {
    null -> EventRecurrenceFrequency.NONE
    RecurrenceRule.Frequency.DAILY -> EventRecurrenceFrequency.DAILY
    RecurrenceRule.Frequency.WEEKLY -> EventRecurrenceFrequency.WEEKLY
    RecurrenceRule.Frequency.MONTHLY -> EventRecurrenceFrequency.MONTHLY
    RecurrenceRule.Frequency.YEARLY -> EventRecurrenceFrequency.YEARLY
}

private fun recurrenceEndOptionOf(recurrence: RecurrenceRule?): RecurrenceEndOption = when {
    recurrence?.until != null -> RecurrenceEndOption.ON_DATE
    recurrence?.count != null -> RecurrenceEndOption.AFTER_COUNT
    else -> RecurrenceEndOption.NEVER
}
