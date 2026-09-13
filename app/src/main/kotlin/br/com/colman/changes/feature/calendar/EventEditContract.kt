// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.calendar

import androidx.compose.runtime.Immutable
import br.com.colman.changes.core.model.CalendarCategory
import br.com.colman.changes.core.model.DomainError
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** Opções de lembrete oferecidas na edição de evento (Seção 7.9). */
enum class ReminderOption(val minutesBefore: Int?) {
    NONE(null),
    AT_TIME(0),
    MINUTES_15(15),
    HOUR_1(60),
    DAY_1(60 * 24),
}

/** Frequência de recorrência da tela: acrescenta `NONE` às quatro frequências do `RecurrenceRule`. */
enum class EventRecurrenceFrequency { NONE, DAILY, WEEKLY, MONTHLY, YEARLY }

/** Como a recorrência termina: nunca, numa data, ou depois de um número de vezes. */
enum class RecurrenceEndOption { NEVER, ON_DATE, AFTER_COUNT }

/**
 * Estado da tela de criar/editar evento (Seção 7.9). Nenhum campo nasce inválido: quem inicia
 * [EventEditViewModel] decide [startDate] e [startTime] default (hoje/agora, ou o dia tocado no
 * calendário).
 */
@Immutable
data class EventEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val title: String = "",
    val description: String = "",
    val category: CalendarCategory = CalendarCategory.APPOINTMENT,
    val isAllDay: Boolean = false,
    val startDate: LocalDate,
    val startTime: LocalTime,
    val endDate: LocalDate? = null,
    val endTime: LocalTime? = null,
    val reminder: ReminderOption = ReminderOption.NONE,
    val recurrenceFrequency: EventRecurrenceFrequency = EventRecurrenceFrequency.NONE,
    val recurrenceInterval: String = "1",
    val recurrenceByDay: Set<DayOfWeek> = emptySet(),
    val recurrenceEndOption: RecurrenceEndOption = RecurrenceEndOption.NEVER,
    val recurrenceUntil: LocalDate? = null,
    val recurrenceCount: String = "",
    val isCompleted: Boolean = false,
    val error: DomainError? = null,
    val showDeleteConfirm: Boolean = false,
)

sealed interface EventEditUiEvent {
    /** `eventId` nulo cria um evento novo; `epochDay` pré-preenche o dia tocado no calendário. */
    data class Load(val eventId: String?, val epochDay: Long?) : EventEditUiEvent

    /** Qualquer campo simples de formulário (texto, seleção, data, hora). */
    data class FieldChanged(val apply: (EventEditUiState) -> EventEditUiState) : EventEditUiEvent
    data object ToggleCompleted : EventEditUiEvent
    data object Save : EventEditUiEvent
    data object RequestDelete : EventEditUiEvent
    data object ConfirmDelete : EventEditUiEvent
    data object CancelDelete : EventEditUiEvent
    data object UndoDelete : EventEditUiEvent
    data object Back : EventEditUiEvent
}

sealed interface EventEditEffect {
    data object NavigateBack : EventEditEffect

    /** Emitido logo após excluir (soft delete): a `Route` mostra o snackbar de "Desfazer". */
    data object ShowUndoDelete : EventEditEffect
}
