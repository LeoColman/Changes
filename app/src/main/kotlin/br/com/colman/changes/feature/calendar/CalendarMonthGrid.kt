// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.colman.changes.R
import br.com.colman.changes.ui.format.Formatters

private const val DAY_CELL_MIN_DP = 48
private const val MARKER_DOT_DP = 6

@Composable
internal fun MonthNavigationHeader(monthLabel: String, onEvent: (CalendarUiEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onEvent(CalendarUiEvent.PreviousMonth) }) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.calendar_previous_month),
            )
        }
        Text(text = monthLabel, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = { onEvent(CalendarUiEvent.NextMonth) }) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.calendar_next_month),
            )
        }
    }
}

@Composable
internal fun WeekdayHeaderRow(firstWeek: List<CalendarDayUiState>) {
    Row(Modifier.fillMaxWidth()) {
        firstWeek.forEach { day ->
            Text(
                text = calendarWeekdayLabel(day.date.dayOfWeek),
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun MonthGrid(days: List<CalendarDayUiState>, onEvent: (CalendarUiEvent) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        days.chunked(DAYS_IN_WEEK).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    DayCell(day, Modifier.weight(1f)) { onEvent(CalendarUiEvent.SelectDate(day.date)) }
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: CalendarDayUiState, modifier: Modifier = Modifier, onSelect: () -> Unit) {
    val background = if (day.isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val textColor = if (day.isCurrentMonth) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
    val description = dayContentDescription(day)
    Column(
        modifier = modifier
            .heightIn(min = DAY_CELL_MIN_DP.dp)
            .padding(2.dp)
            .background(background, RoundedCornerShape(8.dp))
            .clickable(onClick = onSelect)
            .padding(vertical = 6.dp)
            .semantics { contentDescription = description },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = day.date.day.toString(),
            color = textColor,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
        )
        MarkerDots(day.markers)
    }
}

@Composable
private fun MarkerDots(markers: Set<CalendarItemKind>) {
    if (markers.isNotEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            CalendarItemKind.entries.filter { it in markers }.forEach { kind ->
                Box(modifier = Modifier.size(MARKER_DOT_DP.dp).background(markerColor(kind), CircleShape))
            }
        }
    }
}

@Composable
private fun markerColor(kind: CalendarItemKind): Color = when (kind) {
    CalendarItemKind.PLANNED_DOSE -> MaterialTheme.colorScheme.primary
    CalendarItemKind.LOGGED_DOSE -> MaterialTheme.colorScheme.tertiary
    CalendarItemKind.EVENT -> MaterialTheme.colorScheme.secondary
    CalendarItemKind.MILESTONE -> MaterialTheme.colorScheme.outline
}

@Composable
private fun dayContentDescription(day: CalendarDayUiState): String {
    val dateText = Formatters.date(day.date)
    return if (day.markers.isEmpty()) {
        stringResource(R.string.calendar_day_empty_description, dateText)
    } else {
        val labels = CalendarItemKind.entries.filter { it in day.markers }.map { markerLabel(it) }
        stringResource(R.string.calendar_day_items_description, dateText, labels.joinToString(", "))
    }
}
