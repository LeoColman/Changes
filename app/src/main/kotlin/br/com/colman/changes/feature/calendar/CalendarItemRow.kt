// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import br.com.colman.changes.R
import br.com.colman.changes.ui.format.Formatters
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** Uma linha de item da agenda unificada (Seção 7.9): usada na seleção de dia e na visão Agenda. */
@Composable
internal fun CalendarItemRow(item: CalendarItemUiState, navigation: CalendarNavigation) {
    ListItem(
        headlineContent = { Text(itemHeadline(item)) },
        supportingContent = { Text(itemSupporting(item)) },
        modifier = Modifier.fillMaxWidth().clickable { onItemClick(item, navigation) },
    )
}

@Composable
private fun itemHeadline(item: CalendarItemUiState): String = when (item) {
    is CalendarItemUiState.PlannedDoseItem -> markerLabel(CalendarItemKind.PLANNED_DOSE)
    is CalendarItemUiState.LoggedDoseItem -> markerLabel(CalendarItemKind.LOGGED_DOSE)
    is CalendarItemUiState.EventItem -> item.title
    is CalendarItemUiState.MilestoneItem -> item.label
}

@Composable
private fun itemSupporting(item: CalendarItemUiState): String = when (item) {
    is CalendarItemUiState.PlannedDoseItem -> timeOrDateText(item.date, item.time)
    is CalendarItemUiState.LoggedDoseItem -> timeOrDateText(item.date, item.time)
    is CalendarItemUiState.EventItem -> eventSupportingText(item)
    is CalendarItemUiState.MilestoneItem -> markerLabel(CalendarItemKind.MILESTONE)
}

@Composable
private fun eventSupportingText(item: CalendarItemUiState.EventItem): String {
    val base = categoryLabel(item.category) + ", " + timeOrDateText(item.date, item.time)
    return if (item.isCompleted) base + ", " + stringResource(R.string.calendar_event_completed) else base
}

@Composable
private fun timeOrDateText(date: LocalDate, time: LocalTime?): String = if (time != null) {
    stringResource(R.string.calendar_item_datetime, Formatters.date(date), Formatters.time(time))
} else {
    stringResource(R.string.calendar_item_date, Formatters.date(date))
}

private fun onItemClick(item: CalendarItemUiState, navigation: CalendarNavigation) {
    when (item) {
        is CalendarItemUiState.PlannedDoseItem -> navigation.onLogDose(item.regimenId, item.date.toEpochDays())
        is CalendarItemUiState.LoggedDoseItem -> navigation.onEditDose(item.doseLogId)
        is CalendarItemUiState.EventItem -> navigation.onEditEvent(item.eventId)
        is CalendarItemUiState.MilestoneItem -> navigation.onOpenExpectedChanges()
    }
}

/** Chave estável para `LazyColumn`: uma dose prevista não tem id próprio. */
internal fun CalendarItemUiState.key(): String = when (this) {
    is CalendarItemUiState.PlannedDoseItem -> "dose:$regimenId:$date"
    is CalendarItemUiState.LoggedDoseItem -> "log:$doseLogId"
    is CalendarItemUiState.EventItem -> "event:$eventId:$date"
    is CalendarItemUiState.MilestoneItem -> "milestone:$changeTypeCode:$date"
}
