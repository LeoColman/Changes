// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.colman.changes.R
import br.com.colman.changes.ui.components.ChangesScreen
import br.com.colman.changes.ui.components.EmptyState
import br.com.colman.changes.ui.components.LoadingState
import br.com.colman.changes.ui.components.SectionHeader
import br.com.colman.changes.ui.format.Formatters

/**
 * Tela Hoje, sem estado próprio de negócio (Seção 5): recebe [state] pronto e devolve ações por
 * [onEvent] (mudam dado) e [navigation] (só navegam).
 */
@Composable
fun TodayScreen(
    state: TodayUiState,
    onEvent: (TodayUiEvent) -> Unit,
    navigation: TodayNavigation,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    ChangesScreen(title = stringResource(R.string.tab_today), snackbarHostState = snackbarHostState) { padding ->
        if (state.isLoading) {
            LoadingState(modifier = modifier.padding(padding))
        } else {
            TodayContent(state, onEvent, navigation, padding, modifier)
        }
    }
}

@Composable
private fun TodayContent(
    state: TodayUiState,
    onEvent: (TodayUiEvent) -> Unit,
    navigation: TodayNavigation,
    padding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item { SectionHeader(stringResource(R.string.today_next_dose_header)) }
        item { NextDoseSection(state, onEvent, navigation) }

        item { SectionHeader(stringResource(R.string.today_mood_header)) }
        item { MoodSection(state.moodCheckedIn, navigation) }

        item { SectionHeader(stringResource(R.string.today_agenda_header)) }
        if (state.agenda.isEmpty()) {
            item { Text(stringResource(R.string.today_agenda_empty), modifier = Modifier.padding(horizontal = 16.dp)) }
        } else {
            items(state.agenda, key = { it.key() }) { entry -> AgendaRow(entry, navigation) }
            item {
                TextButton(onClick = navigation.onOpenCalendar, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(stringResource(R.string.today_view_calendar_action))
                }
            }
        }
    }
}

@Composable
private fun NextDoseSection(state: TodayUiState, onEvent: (TodayUiEvent) -> Unit, navigation: TodayNavigation) {
    when {
        !state.hasActiveRegimen -> Column(modifier = Modifier.fillMaxWidth()) {
            EmptyState(
                title = stringResource(R.string.today_no_regimen_title),
                body = stringResource(R.string.today_no_regimen_body),
            )
            Button(
                onClick = navigation.onCreateRegimen,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp),
            ) {
                Text(stringResource(R.string.today_create_regimen_action))
            }
        }

        else -> Column {
            if (state.nextDoses.isEmpty()) {
                Text(
                    text = stringResource(R.string.today_no_scheduled_dose),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else {
                state.nextDoses.forEach { next -> NextDoseCard(next, onEvent, navigation) }
            }
            TextButton(onClick = navigation.onOpenRegimens, modifier = Modifier.padding(horizontal = 8.dp)) {
                Text(stringResource(R.string.today_regimens_action))
            }
        }
    }
}

@Composable
private fun NextDoseCard(next: NextDoseUi, onEvent: (TodayUiEvent) -> Unit, navigation: TodayNavigation) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val time = next.time
            val label = if (time != null) {
                stringResource(R.string.today_next_dose_datetime, Formatters.date(next.date), Formatters.time(time))
            } else {
                stringResource(R.string.today_next_dose_date, Formatters.date(next.date))
            }
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onEvent(TodayUiEvent.LogDose(next.regimenId)) }) {
                    Text(stringResource(R.string.today_log_dose_action))
                }
                OutlinedButton(onClick = { navigation.onLogDoseWithDetails(next.regimenId) }) {
                    Text(stringResource(R.string.today_log_dose_with_details_action))
                }
            }
        }
    }
}

@Composable
private fun MoodSection(checkedIn: Boolean, navigation: TodayNavigation) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (checkedIn) {
                Text(stringResource(R.string.today_mood_done), modifier = Modifier.weight(1f))
            } else {
                Text(stringResource(R.string.today_mood_pending), modifier = Modifier.weight(1f))
                TextButton(onClick = navigation.onOpenMoodCheckIn) { Text(stringResource(R.string.today_mood_action)) }
            }
        }
        TextButton(onClick = navigation.onOpenMoodHistory) { Text(stringResource(R.string.today_mood_history_action)) }
    }
}

@Composable
private fun AgendaRow(entry: AgendaEntryUi, navigation: TodayNavigation) {
    val label = when (entry) {
        is AgendaEntryUi.PlannedDoseEntry -> stringResource(R.string.today_agenda_planned_dose)
        is AgendaEntryUi.EventEntry -> entry.title
    }
    val time = entry.time
    val dateTimeText = if (time != null) {
        stringResource(R.string.today_agenda_datetime, Formatters.date(entry.date), Formatters.time(time))
    } else {
        stringResource(R.string.today_agenda_date, Formatters.date(entry.date))
    }
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(dateTimeText) },
        modifier = Modifier.fillMaxWidth().clickable { onAgendaEntryClick(entry, navigation) },
    )
}

private fun onAgendaEntryClick(entry: AgendaEntryUi, navigation: TodayNavigation) {
    when (entry) {
        is AgendaEntryUi.PlannedDoseEntry -> navigation.onLogDoseWithDetails(entry.regimenId)
        is AgendaEntryUi.EventEntry -> navigation.onOpenEvent(entry.eventId)
    }
}

/** Chave estável para `LazyColumn`: um id não basta sozinho porque doses previstas não têm um. */
private fun AgendaEntryUi.key(): String = when (this) {
    is AgendaEntryUi.PlannedDoseEntry -> "dose:$regimenId:$date"
    is AgendaEntryUi.EventEntry -> "event:$eventId:$date"
}
