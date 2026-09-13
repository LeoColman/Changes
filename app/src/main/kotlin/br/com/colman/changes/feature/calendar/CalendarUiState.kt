// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.calendar

import androidx.compose.runtime.Immutable
import br.com.colman.changes.core.model.CalendarCategory
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.uuid.Uuid

/** Alternância de visão da tela Calendário (Seção 7.9). */
enum class CalendarViewMode { MONTH, AGENDA }

/**
 * Tipo de item mostrado como marcador na grade do mês (Seção 7.9). Não é [CalendarCategory], que
 * descreve só a natureza de um evento manual.
 */
enum class CalendarItemKind { PLANNED_DOSE, LOGGED_DOSE, EVENT, MILESTONE }

/** Item unificado da agenda, já resolvido para exibição (rótulo de marco incluso). */
@Immutable
sealed interface CalendarItemUiState {
    val date: LocalDate

    /** Dose prevista de um regime ativo, derivada em tempo de consulta (nunca persistida). */
    data class PlannedDoseItem(val regimenId: Uuid, override val date: LocalDate, val time: LocalTime?) :
        CalendarItemUiState

    data class LoggedDoseItem(val doseLogId: Uuid, override val date: LocalDate, val time: LocalTime?) :
        CalendarItemUiState

    data class EventItem(
        val eventId: Uuid,
        val title: String,
        val category: CalendarCategory,
        override val date: LocalDate,
        val time: LocalTime?,
        val isCompleted: Boolean,
    ) : CalendarItemUiState

    /** Início da janela típica de uma mudança do dataset clínico (camada desligável). */
    data class MilestoneItem(val changeTypeCode: String, val label: String, override val date: LocalDate) :
        CalendarItemUiState
}

/** Um dia da grade do mês, com os tipos de item presentes naquele dia (o detalhe vem da seleção). */
@Immutable
data class CalendarDayUiState(
    val date: LocalDate,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val isSelected: Boolean,
    val markers: Set<CalendarItemKind>,
)

/** Um grupo de dia na visão Agenda (Seção 7.9): lista contínua a partir de hoje, agrupada por dia. */
@Immutable
data class CalendarAgendaGroupUiState(val date: LocalDate, val items: List<CalendarItemUiState>)

/** Estado da tela Calendário (Seção 7.9): visões Mês e Agenda sobre a mesma fonte unificada. */
@Immutable
data class CalendarUiState(
    val isLoading: Boolean = true,
    val viewMode: CalendarViewMode = CalendarViewMode.MONTH,
    val showMilestones: Boolean = true,
    val monthLabel: String = "",
    val monthDays: List<CalendarDayUiState> = emptyList(),
    val selectedDate: LocalDate? = null,
    val selectedDayItems: List<CalendarItemUiState> = emptyList(),
    val agendaGroups: List<CalendarAgendaGroupUiState> = emptyList(),
)
