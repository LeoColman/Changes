// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.mood

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.colman.changes.R
import br.com.colman.changes.ui.chart.ChartSeries
import br.com.colman.changes.ui.chart.LineChart
import br.com.colman.changes.ui.components.ChangesScreen
import br.com.colman.changes.ui.components.EmptyState
import br.com.colman.changes.ui.components.LoadingState
import br.com.colman.changes.ui.components.SectionHeader
import br.com.colman.changes.ui.format.Formatters
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val WEEK_DAYS = 7
private const val MOOD_SCALE_MAX = 5f
private val DAY_CELL_SIZE = 40.dp

/**
 * Histórico de humor, sem estado próprio de negócio (Seção 5): calendário de calor do mês, tendência
 * dos últimos 90 dias e a lista de registros. Toque num dia ou registro chama [onOpenDay].
 */
@Composable
fun MoodHistoryScreen(
    state: MoodHistoryUiState,
    onEvent: (MoodHistoryUiEvent) -> Unit,
    onOpenDay: (Long) -> Unit,
    onOpenSupport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ChangesScreen(title = stringResource(R.string.mood_history_title), onBack = onBack) { padding ->
        Column(modifier = modifier.padding(padding).fillMaxSize()) {
            Column(modifier = Modifier.weight(1f)) {
                when {
                    state.isLoading -> LoadingState()
                    state.entries.isEmpty() -> EmptyState(
                        title = stringResource(R.string.mood_history_title),
                        body = stringResource(R.string.mood_empty_body),
                    )
                    else -> MoodHistoryContent(state, onEvent, onOpenDay)
                }
            }
            HorizontalDivider()
            MoodSupportFooter(onOpenSupport)
        }
    }
}

@Composable
private fun MoodHistoryContent(
    state: MoodHistoryUiState,
    onEvent: (MoodHistoryUiEvent) -> Unit,
    onOpenDay: (Long) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item { SectionHeader(stringResource(R.string.mood_heatmap_title)) }
        item { HeatmapSection(state, onEvent, onOpenDay) }

        if (state.trendEntryCount > 0) {
            item { SectionHeader(stringResource(R.string.mood_trend_title)) }
            item { TrendSection(state) }
        }

        item { SectionHeader(stringResource(R.string.mood_history_title)) }
        items(state.entries, key = { it.id.toString() }) { entry -> EntryRow(entry, onOpenDay) }
    }
}

@Composable
private fun HeatmapSection(
    state: MoodHistoryUiState,
    onEvent: (MoodHistoryUiEvent) -> Unit,
    onOpenDay: (Long) -> Unit,
) {
    val monthLabel = monthYearLabel(state.month)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { onEvent(MoodHistoryUiEvent.PreviousMonth) }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.mood_previous_month),
                )
            }
            Text(monthLabel, style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = { onEvent(MoodHistoryUiEvent.NextMonth) }, enabled = state.canGoToNextMonth) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(R.string.mood_next_month),
                )
            }
        }
        val summary = pluralStringResource(
            R.plurals.mood_heatmap_summary,
            state.registeredDaysInMonth,
            monthLabel,
            state.registeredDaysInMonth,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(WEEK_DAYS),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = summary },
        ) {
            items(state.heatmapDays, key = { it.date.toEpochDays() }) { day ->
                HeatmapDayCell(day, state.today, onOpenDay)
            }
        }
    }
}

@Composable
private fun HeatmapDayCell(day: MoodHeatmapDayUiState, today: LocalDate, onOpenDay: (Long) -> Unit) {
    val mood = day.mood
    val tone = MaterialTheme.colorScheme.primary
    val background = if (mood != null) {
        tone.copy(alpha = mood.toFloat() / MOOD_SCALE_MAX)
    } else {
        MaterialTheme.colorScheme.surface
    }
    val description = if (mood != null) {
        stringResource(R.string.mood_day_with_entry, Formatters.date(day.date), mood)
    } else {
        stringResource(R.string.mood_day_without_entry, Formatters.date(day.date))
    }
    val canOpen = day.date <= today
    val clickModifier = if (canOpen) Modifier.clickable { onOpenDay(day.date.toEpochDays()) } else Modifier
    Column(
        modifier = Modifier
            .size(DAY_CELL_SIZE)
            .clip(MaterialTheme.shapes.small)
            .background(background)
            .then(clickModifier)
            .semantics { contentDescription = description },
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = "${day.date.day}", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
    }
}

@Composable
private fun TrendSection(state: MoodHistoryUiState) {
    val fromDate = state.trendFirstDate ?: state.today
    val toDate = state.trendLastDate ?: state.today
    val summary = pluralStringResource(
        R.plurals.mood_trend_summary,
        state.trendEntryCount,
        state.trendEntryCount,
        Formatters.shortDate(fromDate),
        Formatters.shortDate(toDate),
    )
    val series = listOf(
        ChartSeries(state.trendMoodPoints),
        ChartSeries(state.trendEnergyPoints, MaterialTheme.colorScheme.tertiary),
    )
    LineChart(series = series, summary = summary, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun EntryRow(entry: MoodEntryUiState, onOpenDay: (Long) -> Unit) {
    val moodLabel = stringResource(scaleLabelRes(entry.mood))
    val energyLabel = stringResource(scaleLabelRes(entry.energy))
    val summary = stringResource(R.string.mood_entry_summary, Formatters.date(entry.date), moodLabel, energyLabel)
    ListItem(
        headlineContent = { Text(Formatters.date(entry.date)) },
        supportingContent = { Text(summary) },
        modifier = Modifier.fillMaxWidth().clickable { onOpenDay(entry.date.toEpochDays()) },
    )
}

private val SCALE_LABEL_RES = listOf(
    R.string.mood_scale_1,
    R.string.mood_scale_2,
    R.string.mood_scale_3,
    R.string.mood_scale_4,
    R.string.mood_scale_5,
)

private fun scaleLabelRes(value: Int): Int = SCALE_LABEL_RES[value - 1]

private fun monthYearLabel(month: LocalDate, locale: Locale = Locale.getDefault()): String =
    DateTimeFormatter.ofPattern("MMMM yyyy", locale).format(month.toJavaLocalDate())
