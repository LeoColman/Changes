// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.vitals

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.colman.changes.R
import br.com.colman.changes.core.model.ExerciseIntensity
import br.com.colman.changes.ui.chart.ChartSeries
import br.com.colman.changes.ui.chart.LineChart
import br.com.colman.changes.ui.components.ChangesScreen
import br.com.colman.changes.ui.components.DateField
import br.com.colman.changes.ui.components.DropdownField
import br.com.colman.changes.ui.components.EmptyState
import br.com.colman.changes.ui.components.FormColumn
import br.com.colman.changes.ui.components.LoadingState
import br.com.colman.changes.ui.components.NumberField
import br.com.colman.changes.ui.components.SectionHeader
import br.com.colman.changes.ui.format.Formatters

/**
 * Tela de Exercício (Seção 7.4), sem estado próprio de negócio: sessões livres e resumo semanal em
 * minutos, sem meta e sem barra de progresso.
 */
@Composable
fun ExerciseScreen(
    screenState: ExerciseScreenState,
    onEvent: (ExerciseUiEvent) -> Unit,
    onRoutinesEvent: (RoutinesUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val state = screenState.exercise
    val routines = screenState.routines
    ChangesScreen(
        title = stringResource(R.string.vitals_exercise_title),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        floatingAction = {
            FloatingActionButton(onClick = { onEvent(ExerciseUiEvent.AddRequested) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.vitals_add_session))
            }
        },
    ) { padding ->
        if (state.isLoading || routines.isLoading) {
            LoadingState(modifier = modifier.padding(padding))
        } else {
            LazyColumn(modifier = modifier.padding(padding).fillMaxSize()) {
                item { RoutinesSection(routines, onRoutinesEvent) }
                if (state.sessions.isEmpty()) {
                    item {
                        EmptyState(
                            title = stringResource(R.string.vitals_exercise_empty_title),
                            body = stringResource(R.string.vitals_exercise_empty_body),
                        )
                    }
                } else {
                    item { WeeklySummary(state) }
                    item { SectionHeader(stringResource(R.string.vitals_exercise_title)) }
                    items(state.sessions, key = { it.id.toString() }) { session ->
                        ExerciseSessionRow(session, onEvent)
                    }
                }
            }
        }
    }
    state.form?.let { form -> ExerciseFormDialog(form, onEvent) }
    routines.form?.let { form -> RoutineFormDialog(form, onRoutinesEvent) }
}

@Composable
private fun WeeklySummary(state: ExerciseUiState) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.vitals_weekly_title))
        val weekly = state.weeklyMinutes
        if (weekly.isNotEmpty()) {
            val summary = pluralStringResource(
                R.plurals.vitals_weekly_chart_summary,
                weekly.size,
                weekly.size,
                Formatters.shortDate(weekly.first().weekStart),
                Formatters.shortDate(weekly.last().weekStart),
            )
            LineChart(
                series = listOf(ChartSeries(state.weeklyChartPoints)),
                summary = summary,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            weekly.forEach { week ->
                Text(
                    stringResource(R.string.vitals_week_minutes, Formatters.shortDate(week.weekStart), week.minutes),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ExerciseSessionRow(session: ExerciseSessionUiState, onEvent: (ExerciseUiEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(session.activity)
            Text(
                stringResource(
                    R.string.vitals_session_summary,
                    Formatters.date(session.date),
                    session.durationMinutes,
                    stringResource(intensityLabelRes(session.intensity)),
                ),
                style = MaterialTheme.typography.bodySmall,
            )
            session.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        IconButton(onClick = { onEvent(ExerciseUiEvent.EditRequested(session.id)) }) {
            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.vitals_edit_session))
        }
        IconButton(onClick = { onEvent(ExerciseUiEvent.DeleteRequested(session.id)) }) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.vitals_delete_session))
        }
    }
}

@Composable
private fun ExerciseFormDialog(form: ExerciseFormUiState, onEvent: (ExerciseUiEvent) -> Unit) {
    val titleRes = if (form.editingId == null) R.string.vitals_add_session else R.string.vitals_edit_session
    AlertDialog(
        onDismissRequest = { onEvent(ExerciseUiEvent.FormDismissed) },
        title = { Text(stringResource(titleRes)) },
        text = { ExerciseFormFields(form, onEvent) },
        confirmButton = {
            TextButton(onClick = { onEvent(ExerciseUiEvent.FormSaved) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(ExerciseUiEvent.FormDismissed) }
            ) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ActivityField(form: ExerciseFormUiState, onEvent: (ExerciseUiEvent) -> Unit) {
    OutlinedTextField(
        value = form.activity,
        onValueChange = { onEvent(ExerciseUiEvent.FormActivityChanged(it)) },
        label = { Text(stringResource(R.string.vitals_field_activity)) },
        isError = form.activityError,
        supportingText = if (form.activityError) { { Text(stringResource(R.string.vitals_activity_error)) } } else null,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ExerciseFormFields(form: ExerciseFormUiState, onEvent: (ExerciseUiEvent) -> Unit) {
    FormColumn {
        ActivityField(form, onEvent)
        NumberField(
            value = form.durationText,
            onValueChange = { onEvent(ExerciseUiEvent.FormDurationChanged(it)) },
            label = stringResource(R.string.vitals_field_duration),
            supportingText = if (form.durationError) stringResource(R.string.vitals_duration_error) else null,
            isError = form.durationError,
        )
        DropdownField(
            options = ExerciseIntensity.entries,
            selected = form.intensity,
            onSelect = { onEvent(ExerciseUiEvent.FormIntensityChanged(it)) },
            label = stringResource(R.string.vitals_field_intensity),
            optionLabel = { intensity -> stringResource(intensityLabelRes(intensity)) },
        )
        DateField(
            date = form.date,
            onDateChange = { onEvent(ExerciseUiEvent.FormDateChanged(it)) },
            label = stringResource(R.string.vitals_field_date),
            supportingText = if (form.dateError) stringResource(R.string.vitals_date_future_error) else null,
        )
        OutlinedTextField(
            value = form.notes,
            onValueChange = { onEvent(ExerciseUiEvent.FormNotesChanged(it)) },
            label = { Text(stringResource(R.string.vitals_field_notes)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun intensityLabelRes(intensity: ExerciseIntensity): Int = when (intensity) {
    ExerciseIntensity.LIGHT -> R.string.vitals_intensity_light
    ExerciseIntensity.MODERATE -> R.string.vitals_intensity_moderate
    ExerciseIntensity.VIGOROUS -> R.string.vitals_intensity_vigorous
}
