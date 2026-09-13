// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.medication

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import br.com.colman.changes.R
import br.com.colman.changes.core.model.Schedule
import br.com.colman.changes.ui.components.ChangesScreen
import br.com.colman.changes.ui.components.EmptyState
import br.com.colman.changes.ui.components.LoadingState
import br.com.colman.changes.ui.components.SectionHeader
import br.com.colman.changes.ui.format.Formatters

/** Lista de regimes (Seção 7.1): ativos primeiro, depois encerrados. Stateless. */
@Composable
fun RegimenListScreen(state: RegimenListUiState, onEvent: (RegimenListUiEvent) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        ChangesScreen(
            title = stringResource(R.string.medication_list_title),
            onBack = { onEvent(RegimenListUiEvent.Back) },
            actions = {
                IconButton(onClick = { onEvent(RegimenListUiEvent.OpenHistory) }) {
                    Icon(
                        Icons.Outlined.History,
                        contentDescription = stringResource(R.string.medication_action_history),
                    )
                }
            },
            floatingAction = {
                FloatingActionButton(onClick = { onEvent(RegimenListUiEvent.CreateRegimen) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.medication_action_create))
                }
            },
        ) { padding ->
            when {
                state.isLoading -> LoadingState(Modifier.padding(padding))
                state.activeRegimens.isEmpty() && state.endedRegimens.isEmpty() -> EmptyState(
                    title = stringResource(R.string.medication_list_empty_title),
                    body = stringResource(R.string.medication_list_empty_body),
                    modifier = Modifier.padding(padding),
                )
                else -> RegimenListContent(state, onEvent, Modifier.padding(padding))
            }
        }
    }
}

@Composable
private fun RegimenListContent(
    state: RegimenListUiState,
    onEvent: (RegimenListUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        if (state.activeRegimens.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.medication_section_active)) }
            items(state.activeRegimens, key = { it.regimen.id.toString() }) { entry ->
                RegimenRow(entry, onClick = { onEvent(RegimenListUiEvent.OpenRegimen(entry.regimen.id.toString())) })
            }
        }
        if (state.endedRegimens.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.medication_section_ended)) }
            items(state.endedRegimens, key = { it.regimen.id.toString() }) { entry ->
                RegimenRow(entry, onClick = { onEvent(RegimenListUiEvent.OpenRegimen(entry.regimen.id.toString())) })
            }
        }
    }
}

@Composable
private fun RegimenRow(item: RegimenListItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier.clickable(onClick = onClick),
        headlineContent = { Text(item.medicationName) },
        supportingContent = {
            Column {
                Text(
                    stringResource(
                        R.string.medication_dose_and_route,
                        Formatters.number(item.regimen.dose.value),
                        doseUnitLabel(item.regimen.dose.unit),
                        routeLabel(item.regimen.route),
                    ),
                )
                Text(scheduleSummary(item.regimen.schedule))
                Text(nextDoseText(item))
            }
        },
    )
}

@Composable
private fun nextDoseText(item: RegimenListItem): String = when {
    item.regimen.schedule is Schedule.AsNeeded -> stringResource(R.string.medication_next_dose_as_needed)
    item.nextDose != null -> stringResource(R.string.medication_next_dose, Formatters.date(item.nextDose))
    else -> stringResource(R.string.medication_next_dose_none)
}
