// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.colman.changes.R
import br.com.colman.changes.ui.components.DateField
import br.com.colman.changes.ui.components.DropdownField
import br.com.colman.changes.ui.components.NumberField
import br.com.colman.changes.ui.components.TimeField
import kotlinx.datetime.DayOfWeek

/** Data/hora de início e fim, e o campo de lembrete (Seção 7.9), parte do formulário de [EventEditScreen]. */
@Composable
internal fun DateTimeSection(state: EventEditUiState, change: Change) {
    DateField(
        date = state.startDate,
        onDateChange = { date -> change { it.copy(startDate = date) } },
        label = stringResource(R.string.calendar_field_start_date),
    )
    if (!state.isAllDay) {
        TimeField(
            time = state.startTime,
            onTimeChange = { time -> change { it.copy(startTime = time) } },
            label = stringResource(R.string.calendar_field_start_time),
        )
    }
    EndDateSection(state, change)
}

@Composable
private fun EndDateSection(state: EventEditUiState, change: Change) {
    DateField(
        date = state.endDate,
        onDateChange = { date -> change { it.copy(endDate = date) } },
        label = stringResource(R.string.calendar_field_end_date),
    )
    if (state.endDate != null) {
        if (!state.isAllDay) {
            TimeField(
                time = state.endTime,
                onTimeChange = { time -> change { it.copy(endTime = time) } },
                label = stringResource(R.string.calendar_field_end_time),
            )
        }
        TextButton(onClick = { change { it.copy(endDate = null, endTime = null) } }) {
            Text(stringResource(R.string.calendar_field_end_date_clear))
        }
    }
}

@Composable
internal fun ReminderSection(state: EventEditUiState, change: Change) {
    DropdownField(
        options = ReminderOption.entries,
        selected = state.reminder,
        onSelect = { option -> change { it.copy(reminder = option) } },
        label = stringResource(R.string.calendar_field_reminder),
        optionLabel = { reminderLabel(it) },
    )
}

/** Recorrência simples (Seção 7.9): frequência, intervalo, dias da semana e fim. */
@Composable
internal fun RecurrenceSection(state: EventEditUiState, change: Change) {
    DropdownField(
        options = EventRecurrenceFrequency.entries,
        selected = state.recurrenceFrequency,
        onSelect = { frequency -> change { it.copy(recurrenceFrequency = frequency) } },
        label = stringResource(R.string.calendar_field_recurrence_frequency),
        optionLabel = { recurrenceFrequencyLabel(it) },
    )
    if (state.recurrenceFrequency != EventRecurrenceFrequency.NONE) {
        NumberField(
            value = state.recurrenceInterval,
            onValueChange = { value -> change { it.copy(recurrenceInterval = value) } },
            label = stringResource(R.string.calendar_field_recurrence_interval),
        )
        if (state.recurrenceFrequency == EventRecurrenceFrequency.WEEKLY) {
            WeeklyDaysSelector(state.recurrenceByDay, change)
        }
        RecurrenceEndSection(state, change)
    }
}

@Composable
private fun WeeklyDaysSelector(selected: Set<DayOfWeek>, change: Change) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DayOfWeek.entries.forEach { day ->
            FilterChip(
                selected = day in selected,
                onClick = {
                    change { state ->
                        val days = if (day in state.recurrenceByDay) {
                            state.recurrenceByDay - day
                        } else {
                            state.recurrenceByDay + day
                        }
                        state.copy(recurrenceByDay = days)
                    }
                },
                label = { Text(calendarWeekdayLabel(day)) },
            )
        }
    }
}

@Composable
private fun RecurrenceEndSection(state: EventEditUiState, change: Change) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RecurrenceEndOption.entries.forEach { option ->
            FilterChip(
                selected = state.recurrenceEndOption == option,
                onClick = { change { it.copy(recurrenceEndOption = option) } },
                label = { Text(recurrenceEndOptionLabel(option)) },
            )
        }
    }
    when (state.recurrenceEndOption) {
        RecurrenceEndOption.ON_DATE -> DateField(
            date = state.recurrenceUntil,
            onDateChange = { date -> change { it.copy(recurrenceUntil = date) } },
            label = stringResource(R.string.calendar_field_recurrence_until),
        )
        RecurrenceEndOption.AFTER_COUNT -> NumberField(
            value = state.recurrenceCount,
            onValueChange = { value -> change { it.copy(recurrenceCount = value) } },
            label = stringResource(R.string.calendar_field_recurrence_count),
        )
        RecurrenceEndOption.NEVER -> Unit
    }
}
