// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.today

import androidx.compose.runtime.Immutable
import br.com.colman.changes.core.model.CalendarCategory
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.uuid.Uuid

/** Estado da tela Hoje (Seção 9): próxima dose, check-in do dia e próximos eventos. */
@Immutable
data class TodayUiState(
    val isLoading: Boolean = true,
    val hasActiveRegimen: Boolean = false,
    val nextDoses: List<NextDoseUi> = emptyList(),
    val moodCheckedIn: Boolean = false,
    val agenda: List<AgendaEntryUi> = emptyList(),
)

/**
 * Próxima dose de um regime ativo com agenda (Seção 7.1). [date] pode estar no passado quando a
 * pessoa não registrou uma dose prevista: aparece como fato neutro, sem destaque.
 */
@Immutable
data class NextDoseUi(val regimenId: Uuid, val date: LocalDate, val time: LocalTime?)

/** Item da lista "Próximos eventos" (Seção 7.9): dose prevista ou evento manual. */
@Immutable
sealed interface AgendaEntryUi {
    val date: LocalDate
    val time: LocalTime?

    data class PlannedDoseEntry(val regimenId: Uuid, override val date: LocalDate, override val time: LocalTime?) :
        AgendaEntryUi

    data class EventEntry(
        val eventId: Uuid,
        val title: String,
        val category: CalendarCategory,
        override val date: LocalDate,
        override val time: LocalTime?,
    ) : AgendaEntryUi
}
