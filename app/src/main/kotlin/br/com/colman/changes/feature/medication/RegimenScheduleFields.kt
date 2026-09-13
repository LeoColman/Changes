// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.medication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.colman.changes.R
import br.com.colman.changes.ui.components.DropdownField
import br.com.colman.changes.ui.components.NumberField
import br.com.colman.changes.ui.components.TimeField
import br.com.colman.changes.ui.format.Formatters
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/** Mesmo teto do domínio (`DoseSchedule`): até 12 doses antes do uso contínuo. */
private const val MAX_STEP_FIELDS = 12

private val SCHEDULE_OPTIONS = listOf(
    ScheduleOption.DAILY,
    ScheduleOption.INTERVAL_DAYS,
    ScheduleOption.WEEKLY,
    ScheduleOption.MONTHLY,
    ScheduleOption.QUARTERLY,
    ScheduleOption.STEPPED,
    ScheduleOption.AS_NEEDED,
)

/** Agenda do regime (ADR 0007): tipo, campos do tipo, hora e a prévia das próximas doses. */
@Composable
internal fun ScheduleFields(state: RegimenEditUiState, change: Change) {
    val options = if (state.scheduleOption == ScheduleOption.CUSTOM) {
        SCHEDULE_OPTIONS + ScheduleOption.CUSTOM
    } else {
        SCHEDULE_OPTIONS
    }
    DropdownField(
        options = options,
        selected = state.scheduleOption,
        onSelect = { option -> change { it.copy(scheduleOption = option) } },
        label = stringResource(R.string.medication_field_schedule_type),
        optionLabel = { scheduleOptionLabel(it) },
    )
    ScheduleOptionFields(state, change)
    TimeField(
        time = state.timeOfDay,
        onTimeChange = { time -> change { it.copy(timeOfDay = time) } },
        label = stringResource(R.string.medication_field_time_of_day),
    )
    if (state.timeOfDay != null) {
        TextButton(onClick = { change { it.copy(timeOfDay = null) } }) {
            Text(stringResource(R.string.medication_field_time_of_day_clear))
        }
    }
    NextDosesPreview(state.nextDoses)
}

@Composable
private fun ScheduleOptionFields(state: RegimenEditUiState, change: Change) {
    when (state.scheduleOption) {
        ScheduleOption.INTERVAL_DAYS -> NumberField(
            value = state.intervalDays,
            onValueChange = { value -> change { it.copy(intervalDays = value) } },
            label = stringResource(R.string.medication_field_interval_days),
        )
        ScheduleOption.WEEKLY -> {
            WeeklyDaysSelector(state.weeklyDays, change)
            NumberField(
                value = state.everyWeeks,
                onValueChange = { value -> change { it.copy(everyWeeks = value) } },
                label = stringResource(R.string.medication_field_every_weeks),
            )
        }
        ScheduleOption.MONTHLY -> NumberField(
            value = state.everyMonths,
            onValueChange = { value -> change { it.copy(everyMonths = value) } },
            label = stringResource(R.string.medication_field_every_months),
        )
        ScheduleOption.STEPPED -> SteppedFields(state, change)
        else -> Unit
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
                        val days = if (day in state.weeklyDays) state.weeklyDays - day else state.weeklyDays + day
                        state.copy(weeklyDays = days)
                    }
                },
                label = { Text(weekdayLabel(day)) },
            )
        }
    }
}

/** Um campo por dose: a primeira conta da data de início, as seguintes da dose anterior. */
@Composable
private fun SteppedFields(state: RegimenEditUiState, change: Change) {
    Text(stringResource(R.string.medication_stepped_help), style = MaterialTheme.typography.bodySmall)
    state.stepDays.forEachIndexed { index, text ->
        NumberField(
            value = text,
            onValueChange = { value ->
                change { current ->
                    current.copy(stepDays = current.stepDays.mapIndexed { i, old -> if (i == index) value else old })
                }
            },
            label = if (index == 0) {
                stringResource(R.string.medication_field_step_first)
            } else {
                stringResource(R.string.medication_field_step_next, index + 1)
            },
        )
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.stepDays.size < MAX_STEP_FIELDS) {
            TextButton(onClick = { change { it.copy(stepDays = it.stepDays + "") } }) {
                Text(stringResource(R.string.medication_action_add_step))
            }
        }
        if (state.stepDays.size > 1) {
            TextButton(onClick = { change { it.copy(stepDays = it.stepDays.dropLast(1)) } }) {
                Text(stringResource(R.string.medication_action_remove_step))
            }
        }
    }
    ContinuousFields(state, change)
}

@Composable
private fun ContinuousFields(state: RegimenEditUiState, change: Change) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.medication_field_continuous), modifier = Modifier.weight(1f))
        Switch(checked = state.continuous, onCheckedChange = { on -> change { it.copy(continuous = on) } })
    }
    if (state.continuous) {
        NumberField(
            value = state.continuousEveryDays,
            onValueChange = { value -> change { it.copy(continuousEveryDays = value) } },
            label = stringResource(R.string.medication_field_continuous_every),
        )
    }
}

/** Confere a agenda antes de salvar: some quando a agenda está incompleta ou é "quando necessário". */
@Composable
private fun NextDosesPreview(dates: List<LocalDate>) {
    if (dates.isNotEmpty()) {
        Text(
            stringResource(R.string.medication_next_doses_preview, dates.joinToString(", ") { Formatters.date(it) }),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
