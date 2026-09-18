// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.mood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.changes.core.data.MoodRepository
import br.com.colman.changes.core.model.MoodLog
import br.com.colman.changes.core.model.TimeZoneProvider
import br.com.colman.changes.ui.chart.ChartPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

private const val STOP_TIMEOUT_MILLIS = 5_000L

/** Janela fixa da tendência (Seção 7.7): últimos 90 dias, independente do mês navegado no calendário. */
private const val TREND_WINDOW_DAYS = 90

/**
 * Histórico de humor (Seção 7.7): calendário de calor de um mês navegável, tendência dos últimos 90
 * dias e a lista completa de registros. Nada aqui lê o texto da nota (critério 7.7.2).
 */
class MoodHistoryViewModel(
    private val moodRepository: MoodRepository,
    private val clock: Clock,
    private val timeZones: TimeZoneProvider,
) : ViewModel() {

    private val monthFlow = MutableStateFlow(currentMonthStart())

    val state: StateFlow<MoodHistoryUiState> = combine(monthFlow, moodRepository.observeAll()) { month, all ->
        buildState(month, all)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MoodHistoryUiState())

    fun onEvent(event: MoodHistoryUiEvent) {
        when (event) {
            MoodHistoryUiEvent.PreviousMonth -> monthFlow.update { it.minus(1, DateTimeUnit.MONTH) }
            MoodHistoryUiEvent.NextMonth -> monthFlow.update { current ->
                val next = current.plus(1, DateTimeUnit.MONTH)
                if (next <= currentMonthStart()) next else current
            }
        }
    }

    private fun today(): LocalDate = clock.now().toLocalDateTime(timeZones.current()).date

    private fun currentMonthStart(): LocalDate {
        val today = today()
        return LocalDate(year = today.year, monthNumber = today.monthNumber, dayOfMonth = 1)
    }

    private fun buildState(month: LocalDate, all: List<MoodLog>): MoodHistoryUiState {
        val today = today()
        val byDate = all.associateBy { it.date }
        val heatmapDays = (0 until daysInMonth(month)).map { offset ->
            val date = month.plus(offset, DateTimeUnit.DAY)
            MoodHeatmapDayUiState(date, byDate[date]?.mood)
        }
        val trendFrom = today.minus(TREND_WINDOW_DAYS - 1, DateTimeUnit.DAY)
        val trendLogs = all.filter { it.date in trendFrom..today }.sortedBy { it.date }
        return MoodHistoryUiState(
            isLoading = false,
            today = today,
            month = month,
            canGoToNextMonth = month < currentMonthStart(),
            heatmapDays = heatmapDays,
            registeredDaysInMonth = heatmapDays.count { it.mood != null },
            trendMoodPoints = trendLogs.map { ChartPoint(it.date.toEpochDays().toDouble(), it.mood.toDouble()) },
            trendEnergyPoints = trendLogs.map { ChartPoint(it.date.toEpochDays().toDouble(), it.energy.toDouble()) },
            trendEntryCount = trendLogs.size,
            trendFirstDate = trendLogs.firstOrNull()?.date,
            trendLastDate = trendLogs.lastOrNull()?.date,
            entries = all.sortedByDescending { it.date }.map { it.toEntryUiState() },
        )
    }

    private fun daysInMonth(month: LocalDate): Int =
        (month.plus(1, DateTimeUnit.MONTH).toEpochDays() - month.toEpochDays()).toInt()

    private fun MoodLog.toEntryUiState() = MoodEntryUiState(
        id = id,
        date = date,
        mood = mood,
        energy = energy,
        relief = relief,
        irritability = irritability,
        emotionalIntensity = emotionalIntensity,
        anxiety = anxiety,
    )
}
