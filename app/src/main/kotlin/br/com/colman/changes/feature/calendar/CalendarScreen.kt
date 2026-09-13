// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

internal const val DAYS_IN_WEEK = 7

/**
 * Tela Calendário (Seção 7.9), sem estado próprio de negócio: recebe [state] pronto e devolve
 * mudanças por [onEvent] e navegação pura por [navigation].
 */
@Composable
fun CalendarScreen(
    state: CalendarUiState,
    onEvent: (CalendarUiEvent) -> Unit,
    navigation: CalendarNavigation,
    modifier: Modifier = Modifier,
) {
    ChangesScreen(
        title = stringResource(R.string.tab_calendar),
        floatingAction = {
            state.selectedDate?.let { date ->
                FloatingActionButton(onClick = { navigation.onNewEvent(date.toEpochDays()) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.calendar_action_new_event))
                }
            }
        },
    ) { padding ->
        if (state.isLoading) {
            LoadingState(modifier.padding(padding))
        } else {
            CalendarContent(state, onEvent, navigation, modifier.padding(padding))
        }
    }
}

@Composable
private fun CalendarContent(
    state: CalendarUiState,
    onEvent: (CalendarUiEvent) -> Unit,
    navigation: CalendarNavigation,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        ViewModeSelector(state.viewMode, onEvent)
        MilestonesToggle(state.showMilestones, onEvent)
        when (state.viewMode) {
            CalendarViewMode.MONTH -> MonthView(state, onEvent, navigation)
            CalendarViewMode.AGENDA -> AgendaView(state, navigation)
        }
    }
}

@Composable
private fun ViewModeSelector(viewMode: CalendarViewMode, onEvent: (CalendarUiEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = viewMode == CalendarViewMode.MONTH,
            onClick = { onEvent(CalendarUiEvent.ChangeViewMode(CalendarViewMode.MONTH)) },
            label = { Text(stringResource(R.string.calendar_view_month)) },
        )
        FilterChip(
            selected = viewMode == CalendarViewMode.AGENDA,
            onClick = { onEvent(CalendarUiEvent.ChangeViewMode(CalendarViewMode.AGENDA)) },
            label = { Text(stringResource(R.string.calendar_view_agenda)) },
        )
    }
}

@Composable
private fun MilestonesToggle(enabled: Boolean, onEvent: (CalendarUiEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.calendar_toggle_milestones), modifier = Modifier.weight(1f))
        Switch(checked = enabled, onCheckedChange = { onEvent(CalendarUiEvent.ToggleMilestones(it)) })
    }
}

@Composable
private fun MonthView(state: CalendarUiState, onEvent: (CalendarUiEvent) -> Unit, navigation: CalendarNavigation) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        MonthNavigationHeader(state.monthLabel, onEvent)
        WeekdayHeaderRow(state.monthDays.take(DAYS_IN_WEEK))
        MonthGrid(state.monthDays, onEvent)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SelectedDaySection(state, navigation)
    }
}

@Composable
private fun SelectedDaySection(state: CalendarUiState, navigation: CalendarNavigation) {
    val dateText = state.selectedDate?.let { Formatters.date(it) }.orEmpty()
    SectionHeader(stringResource(R.string.calendar_selected_day_header, dateText))
    if (state.selectedDayItems.isEmpty()) {
        Text(
            text = stringResource(R.string.calendar_selected_day_empty),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    } else {
        Column {
            state.selectedDayItems.forEach { item -> CalendarItemRow(item, navigation) }
        }
    }
}

@Composable
private fun AgendaView(state: CalendarUiState, navigation: CalendarNavigation) {
    if (state.agendaGroups.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.calendar_agenda_empty_title),
            body = stringResource(R.string.calendar_agenda_empty_body),
        )
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            state.agendaGroups.forEach { group ->
                item(key = "header-${group.date}") { SectionHeader(Formatters.date(group.date)) }
                items(group.items, key = { it.key() }) { entry -> CalendarItemRow(entry, navigation) }
            }
        }
    }
}
