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
import br.com.colman.changes.core.model.MeasurementType
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
 * Tela de Medidas (Seção 7.4), sem estado próprio de negócio: peso e IMC, e as demais medidas do
 * corpo, cada uma com gráfico, valor atual, adicionar/editar/excluir.
 */
@Composable
fun MeasurementsScreen(
    state: MeasurementsUiState,
    onEvent: (MeasurementsUiEvent) -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val typeLabel = rememberMeasurementTypeLabel(state)
    ChangesScreen(
        title = stringResource(R.string.vitals_measurements_title),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        floatingAction = {
            FloatingActionButton(onClick = { onEvent(MeasurementsUiEvent.AddRequested(MeasurementType.WEIGHT)) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.vitals_add_measurement))
            }
        },
    ) { padding ->
        MeasurementsContent(state, typeLabel, onEvent, onOpenSettings, modifier.padding(padding))
    }
    state.form?.let { form -> MeasurementFormDialog(form, typeLabel, onEvent) }
}

@Composable
private fun rememberMeasurementTypeLabel(state: MeasurementsUiState): @Composable (MeasurementType) -> String {
    val chestLabel = state.sections.firstOrNull { it.type == MeasurementType.CHEST }?.label
    return { type ->
        when (type) {
            MeasurementType.CHEST -> chestLabel ?: stringResource(R.string.vitals_type_custom)
            MeasurementType.CUSTOM -> stringResource(R.string.vitals_type_custom)
            else -> stringResource(fixedLabelRes(type))
        }
    }
}

@Composable
private fun MeasurementsContent(
    state: MeasurementsUiState,
    typeLabel: @Composable (MeasurementType) -> String,
    onEvent: (MeasurementsUiEvent) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier,
) {
    val nothingToShow = state.bmi == null && state.sections.all { it.entries.isEmpty() }
    when {
        state.isLoading -> LoadingState(modifier = modifier)
        nothingToShow -> EmptyState(
            title = stringResource(R.string.vitals_measurements_empty_title),
            body = stringResource(R.string.vitals_measurements_empty_body),
            modifier = modifier,
        )
        else -> LazyColumn(modifier = modifier.fillMaxSize()) {
            item { BmiCard(state.bmi, onOpenSettings, onEvent) }
            items(state.sections, key = { it.type.name + (it.customLabel ?: "") }) { section ->
                MeasurementSectionCard(section, typeLabel(section.type), onEvent)
            }
            item {
                TextButton(onClick = { onEvent(MeasurementsUiEvent.AddRequested(MeasurementType.CUSTOM)) }) {
                    Text(stringResource(R.string.vitals_add_custom_measurement))
                }
            }
        }
    }
}

@Composable
private fun BmiCard(bmi: BmiUiState?, onOpenSettings: () -> Unit, onEvent: (MeasurementsUiEvent) -> Unit) {
    when (bmi) {
        null -> Unit
        BmiUiState.NeedsHeight -> Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.bmi_needs_height))
            TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.vitals_open_settings)) }
        }
        BmiUiState.NeedsWeight -> Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.bmi_needs_weight))
            TextButton(
                onClick = { onEvent(MeasurementsUiEvent.AddRequested(MeasurementType.WEIGHT)) }
            ) { Text(stringResource(R.string.vitals_record_weight)) }
        }
        is BmiUiState.Value -> Column(Modifier.padding(16.dp)) {
            SectionHeader(stringResource(R.string.bmi_label))
            Text(Formatters.number(bmi.value), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.bmi_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun MeasurementSectionCard(
    section: MeasurementSectionUiState,
    label: String,
    onEvent: (MeasurementsUiEvent) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(label)
        section.current?.let { current -> Text(stringResource(R.string.vitals_current_value, formattedValue(current))) }
        if (section.entries.isEmpty()) {
            Text(stringResource(R.string.vitals_no_entries), modifier = Modifier.padding(horizontal = 16.dp))
        } else {
            MeasurementChart(section, label)
        }
        TextButton(
            onClick = { onEvent(MeasurementsUiEvent.AddRequested(section.type, section.customLabel)) },
            modifier = Modifier.padding(horizontal = 8.dp),
        ) { Text(stringResource(R.string.action_add)) }
        section.entries.asReversed().forEach { entry -> MeasurementEntryRow(entry, onEvent) }
        HorizontalDivider()
    }
}

@Composable
private fun MeasurementChart(section: MeasurementSectionUiState, label: String) {
    val entries = section.entries
    val latest = entries.last()
    val summary = pluralStringResource(
        R.plurals.vitals_measurement_chart_summary,
        entries.size,
        label,
        entries.size,
        Formatters.shortDate(entries.first().date),
        Formatters.shortDate(latest.date),
        formattedValue(latest),
    )
    LineChart(
        series = listOf(ChartSeries(section.chartPoints)),
        summary = summary,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun MeasurementEntryRow(entry: MeasurementEntryUiState, onEvent: (MeasurementsUiEvent) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(formattedValue(entry))
            Text(Formatters.date(entry.date), style = MaterialTheme.typography.bodySmall)
            entry.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        IconButton(onClick = { onEvent(MeasurementsUiEvent.EditRequested(entry.id)) }) {
            Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.vitals_edit_entry))
        }
        IconButton(onClick = { onEvent(MeasurementsUiEvent.DeleteRequested(entry.id)) }) {
            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.vitals_delete_entry))
        }
    }
}

@Composable
private fun MeasurementFormDialog(
    form: MeasurementFormUiState,
    typeLabel: @Composable (MeasurementType) -> String,
    onEvent: (MeasurementsUiEvent) -> Unit,
) {
    val titleRes = if (form.editingId == null) R.string.vitals_add_measurement else R.string.vitals_edit_measurement
    AlertDialog(
        onDismissRequest = { onEvent(MeasurementsUiEvent.FormDismissed) },
        title = { Text(stringResource(titleRes)) },
        text = { MeasurementFormFields(form, typeLabel, onEvent) },
        confirmButton = {
            TextButton(
                onClick = { onEvent(MeasurementsUiEvent.FormSaved) }
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(
                onClick = { onEvent(MeasurementsUiEvent.FormDismissed) }
            ) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun MeasurementFormFields(
    form: MeasurementFormUiState,
    typeLabel: @Composable (MeasurementType) -> String,
    onEvent: (MeasurementsUiEvent) -> Unit,
) {
    FormColumn {
        DropdownField(
            options = MeasurementType.entries,
            selected = form.type,
            onSelect = { onEvent(MeasurementsUiEvent.FormTypeChanged(it)) },
            label = stringResource(R.string.vitals_field_type),
            optionLabel = typeLabel,
        )
        if (form.type == MeasurementType.CUSTOM) {
            CustomMeasurementFields(form, onEvent)
        }
        NumberField(
            value = form.valueText,
            onValueChange = { onEvent(MeasurementsUiEvent.FormValueChanged(it)) },
            label = stringResource(R.string.vitals_field_value),
            supportingText = if (form.valueError) stringResource(R.string.vitals_value_error) else null,
            isError = form.valueError,
        )
        DateField(
            date = form.date,
            onDateChange = { onEvent(MeasurementsUiEvent.FormDateChanged(it)) },
            label = stringResource(R.string.vitals_field_date),
        )
        OutlinedTextField(
            value = form.notes,
            onValueChange = { onEvent(MeasurementsUiEvent.FormNotesChanged(it)) },
            label = { Text(stringResource(R.string.vitals_field_notes)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
