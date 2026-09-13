// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.mood

import androidx.compose.runtime.Immutable
import br.com.colman.changes.ui.chart.ChartPoint
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

private val EPOCH = LocalDate(year = 1970, monthNumber = 1, dayOfMonth = 1)

/**
 * Estado do histórico de humor (Seção 7.7): calendário de calor do mês exibido, tendência dos
 * últimos 90 dias e a lista de todos os registros. [entries] nunca é filtrada pelo texto da nota
 * (critério 7.7.2).
 */
@Immutable
data class MoodHistoryUiState(
    val isLoading: Boolean = true,
    val today: LocalDate = EPOCH,
    val month: LocalDate = EPOCH,
    val canGoToNextMonth: Boolean = false,
    val heatmapDays: List<MoodHeatmapDayUiState> = emptyList(),
    val registeredDaysInMonth: Int = 0,
    val trendMoodPoints: List<ChartPoint> = emptyList(),
    val trendEnergyPoints: List<ChartPoint> = emptyList(),
    val trendEntryCount: Int = 0,
    val trendFirstDate: LocalDate? = null,
    val trendLastDate: LocalDate? = null,
    val entries: List<MoodEntryUiState> = emptyList(),
)

/** Um dia do calendário de calor. [mood] nulo é um dia sem registro (vazio). */
@Immutable
data class MoodHeatmapDayUiState(val date: LocalDate, val mood: Int?)

/** Uma linha da lista de registros. */
@Immutable
data class MoodEntryUiState(val id: Uuid, val date: LocalDate, val mood: Int, val energy: Int)

/** Navegação entre meses do calendário de calor (Seção 7.7). */
sealed interface MoodHistoryUiEvent {
    data object PreviousMonth : MoodHistoryUiEvent

    data object NextMonth : MoodHistoryUiEvent
}
