// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.medication

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.colman.changes.R
import br.com.colman.changes.ui.components.ChangesScreen
import br.com.colman.changes.ui.components.EmptyState
import br.com.colman.changes.ui.components.LoadingState
import br.com.colman.changes.ui.components.SectionHeader
import br.com.colman.changes.ui.format.Formatters

/** Histórico de doses (Seção 7.1). Stateless: toda mutação sai como [DoseHistoryUiEvent]. */
@Composable
fun DoseHistoryScreen(
    state: DoseHistoryUiState,
    onEvent: (DoseHistoryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Box(modifier) {
        ChangesScreen(
            title = stringResource(R.string.medication_history_title),
            onBack = { onEvent(DoseHistoryUiEvent.Back) },
            snackbarHostState = snackbarHostState,
        ) { padding ->
            if (state.isLoading) {
                LoadingState(Modifier.padding(padding))
            } else {
                DoseHistoryContent(state, onEvent, Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun DoseHistoryContent(
    state: DoseHistoryUiState,
    onEvent: (DoseHistoryUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item { PeriodFilterRow(state, onEvent) }
        item { MedicationFilterRow(state, onEvent) }
        items(state.adherenceSentences, key = { it.regimenId }) { summary -> AdherenceRow(summary) }
        if (state.monthGroups.isEmpty()) {
            item {
                EmptyState(
                    title = stringResource(R.string.medication_history_empty_title),
                    body = stringResource(R.string.medication_history_empty_body),
                )
            }
        } else {
            state.monthGroups.forEach { group ->
                item(key = "header-${group.key}") { SectionHeader(group.key) }
                items(group.entries, key = { it.log.id.toString() }) { entry ->
                    HistoryRow(entry, onEvent)
                }
            }
        }
    }
}

@Composable
private fun PeriodFilterRow(state: DoseHistoryUiState, onEvent: (DoseHistoryUiEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HistoryPeriod.entries.forEach { period ->
            FilterChip(
                selected = state.periodFilter == period,
                onClick = { onEvent(DoseHistoryUiEvent.FilterByPeriod(period)) },
                label = { Text(periodLabel(period)) },
            )
        }
    }
}

@Composable
private fun MedicationFilterRow(state: DoseHistoryUiState, onEvent: (DoseHistoryUiEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = state.medicationFilter == null,
            onClick = { onEvent(DoseHistoryUiEvent.FilterByMedication(null)) },
            label = { Text(stringResource(R.string.medication_history_filter_all)) },
        )
        state.availableMedications.forEach { option ->
            FilterChip(
                selected = state.medicationFilter == option.id,
                onClick = { onEvent(DoseHistoryUiEvent.FilterByMedication(option.id)) },
                label = { Text(option.name) },
            )
        }
    }
}

@Composable
private fun AdherenceRow(summary: AdherenceSummary) {
    val text = if (summary.expected == 0) {
        val days = summary.windowDays
        val windowPhrase = pluralStringResource(R.plurals.medication_window_days, days, days)
        stringResource(R.string.adherence_none_expected, windowPhrase)
    } else {
        pluralStringResource(
            R.plurals.adherence_sentence,
            summary.expected,
            summary.registered,
            summary.expected,
            summary.windowDays,
        )
    }
    ListItem(headlineContent = { Text(summary.medicationName) }, supportingContent = { Text(text) })
}

@Composable
private fun HistoryRow(entry: HistoryEntry, onEvent: (DoseHistoryUiEvent) -> Unit) {
    val log = entry.log
    ListItem(
        modifier = Modifier.clickable { onEvent(DoseHistoryUiEvent.EditEntry(log.id.toString())) },
        headlineContent = { Text(entry.medicationName) },
        supportingContent = {
            Text(
                stringResource(
                    R.string.medication_dose_and_route,
                    Formatters.number(log.dose.value),
                    doseUnitLabel(log.dose.unit),
                    routeLabel(log.route),
                ) + " · " + Formatters.recorded(log.takenAt),
            )
        },
    )
}

@Composable
private fun periodLabel(period: HistoryPeriod): String = stringResource(
    when (period) {
        HistoryPeriod.DAYS_30 -> R.string.medication_period_30
        HistoryPeriod.DAYS_90 -> R.string.medication_period_90
        HistoryPeriod.DAYS_365 -> R.string.medication_period_365
        HistoryPeriod.ALL -> R.string.medication_period_all
    },
)
