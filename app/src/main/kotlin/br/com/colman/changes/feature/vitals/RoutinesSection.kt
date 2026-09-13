// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.vitals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.colman.changes.R
import br.com.colman.changes.ui.components.DateField
import br.com.colman.changes.ui.components.DropdownField
import br.com.colman.changes.ui.components.FormColumn
import br.com.colman.changes.ui.components.NumberField
import br.com.colman.changes.ui.components.SectionHeader
import br.com.colman.changes.ui.components.TimeField
import br.com.colman.changes.ui.format.Formatters
import kotlinx.datetime.DayOfWeek

/**
 * Seção de Rotinas (ADR 0011), acima das sessões na tela de Exercício: cada rotina com a atividade,
 * o resumo da frequência e a hora quando houver. Sem rotina, um texto curto explica onde ela aparece.
 */
@Composable
internal fun RoutinesSection(state: RoutinesUiState, onEvent: (RoutinesUiEvent) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.vitals_routines_title))
        if (state.routines.isEmpty()) {
            Text(
                stringResource(R.string.vitals_routines_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        } else {
            state.routines.forEach { routine -> RoutineRow(routine, onEvent) }
        }
        TextButton(
            onClick = { onEvent(RoutinesUiEvent.AddRequested) },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) { Text(stringResource(R.string.vitals_add_routine)) }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun RoutineRow(routine: RoutineUiState, onEvent: (RoutinesUiEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(routine.activity)
            val summary = routineFrequencySummary(routine)
            val subtitle = routine.time?.let {
                stringResource(R.string.vitals_routine_summary_with_time, summary, Formatters.time(it))
            } ?: summary
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = { onEvent(RoutinesUiEvent.EditRequested(routine.id)) }) {
            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.vitals_edit_routine))
        }
        IconButton(onClick = { onEvent(RoutinesUiEvent.DeleteRequested(routine.id)) }) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.vitals_delete_routine))
        }
    }
}

@Composable
internal fun RoutineFormDialog(form: RoutineFormUiState, onEvent: (RoutinesUiEvent) -> Unit) {
    val titleRes = if (form.editingId == null) R.string.vitals_add_routine else R.string.vitals_edit_routine
    AlertDialog(
        onDismissRequest = { onEvent(RoutinesUiEvent.FormDismissed) },
        title = { Text(stringResource(titleRes)) },
        text = { RoutineFormFields(form, onEvent) },
        confirmButton = {
            TextButton(onClick = { onEvent(RoutinesUiEvent.FormSaved) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(RoutinesUiEvent.FormDismissed) }
            ) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun RoutineActivityField(form: RoutineFormUiState, onEvent: (RoutinesUiEvent) -> Unit) {
    OutlinedTextField(
        value = form.activity,
        onValueChange = { onEvent(RoutinesUiEvent.FormActivityChanged(it)) },
        label = { Text(stringResource(R.string.vitals_field_activity)) },
        isError = form.activityError,
        supportingText = if (form.activityError) { { Text(stringResource(R.string.vitals_activity_error)) } } else null,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun RoutineFormFields(form: RoutineFormUiState, onEvent: (RoutinesUiEvent) -> Unit) {
    FormColumn {
        RoutineActivityField(form, onEvent)
        DropdownField(
            options = RoutineFrequency.entries,
            selected = form.frequency,
            onSelect = { onEvent(RoutinesUiEvent.FormFrequencyChanged(it)) },
            label = stringResource(R.string.vitals_field_frequency),
            optionLabel = { routineFrequencyLabel(it) },
        )
        if (form.frequency == RoutineFrequency.WEEKLY) {
            WeeklyFields(form, onEvent)
        }
        DateField(
            date = form.startDate,
            onDateChange = { onEvent(RoutinesUiEvent.FormStartDateChanged(it)) },
            label = stringResource(R.string.vitals_field_start_date),
        )
        TimeFields(form, onEvent)
        if (form.time != null) {
            DropdownField(
                options = RoutineReminderOption.entries,
                selected = form.reminder,
                onSelect = { onEvent(RoutinesUiEvent.FormReminderChanged(it)) },
                label = stringResource(R.string.vitals_field_reminder),
                optionLabel = { routineReminderLabel(it) },
            )
        }
    }
}

@Composable
private fun WeeklyFields(form: RoutineFormUiState, onEvent: (RoutinesUiEvent) -> Unit) {
    NumberField(
        value = form.intervalText,
        onValueChange = { onEvent(RoutinesUiEvent.FormIntervalChanged(it)) },
        label = stringResource(R.string.vitals_field_weekly_interval),
        supportingText = if (form.intervalError) stringResource(R.string.vitals_weekly_interval_error) else null,
        isError = form.intervalError,
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DayOfWeek.entries.forEach { day ->
            FilterChip(
                selected = day in form.weeklyDays,
                onClick = { onEvent(RoutinesUiEvent.FormWeeklyDayToggled(day)) },
                label = { Text(routineWeekdayLabel(day)) },
            )
        }
    }
    if (form.weeklyDaysError) {
        Text(
            stringResource(R.string.vitals_weekly_days_error),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun TimeFields(form: RoutineFormUiState, onEvent: (RoutinesUiEvent) -> Unit) {
    TimeField(
        time = form.time,
        onTimeChange = { onEvent(RoutinesUiEvent.FormTimeChanged(it)) },
        label = stringResource(R.string.vitals_field_time),
    )
    if (form.time != null) {
        TextButton(onClick = { onEvent(RoutinesUiEvent.FormTimeChanged(null)) }) {
            Text(stringResource(R.string.vitals_field_time_clear))
        }
    }
}
