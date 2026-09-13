// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.health

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import br.com.colman.changes.ui.chart.ChartSeries
import br.com.colman.changes.ui.chart.LineChart
import br.com.colman.changes.ui.components.ChangesScreen
import br.com.colman.changes.ui.components.DateField
import br.com.colman.changes.ui.components.EmptyState
import br.com.colman.changes.ui.components.FormColumn
import br.com.colman.changes.ui.components.LoadingState
import br.com.colman.changes.ui.format.Formatters

private typealias NewAnalyteChange = ((NewAnalyteFormUiState) -> NewAnalyteFormUiState) -> Unit
private typealias ResultChange = ((LabResultFormUiState) -> LabResultFormUiState) -> Unit

/**
 * Tela de Exames (Seção 7.6), sem estado próprio de negócio: lista de analitos com o último
 * resultado, e o detalhe de um analito (gráfico com a faixa do laudo e a lista de resultados).
 */
@Composable
fun LabResultsScreen(
    state: LabResultsUiState,
    onEvent: (LabResultsUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val detail = state.detail
    ChangesScreen(
        title = detail?.label ?: stringResource(R.string.labs_title),
        onBack = { if (detail != null) onEvent(LabResultsUiEvent.BackToListRequested) else onBack() },
        snackbarHostState = snackbarHostState,
        floatingAction = {
            val addEvent =
                if (detail != null) LabResultsUiEvent.AddResultRequested else LabResultsUiEvent.NewAnalyteRequested
            FloatingActionButton(onClick = { onEvent(addEvent) }) {
                val description = if (detail != null) R.string.health_add_result else R.string.health_add_analyte
                Icon(Icons.Filled.Add, contentDescription = stringResource(description))
            }
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingState(modifier.padding(padding))
            detail != null -> AnalyteDetailContent(detail, onEvent, modifier.padding(padding))
            state.analytes.isEmpty() -> EmptyState(
                title = stringResource(R.string.labs_empty_title),
                body = stringResource(R.string.labs_empty_body),
                modifier = modifier.padding(padding),
            )
            else -> AnalyteListContent(state.analytes, onEvent, modifier.padding(padding))
        }
    }
    state.newAnalyteForm?.let { form -> NewAnalyteDialog(form, onEvent) }
    state.resultForm?.let { form -> ResultFormDialog(form, onEvent) }
}

@Composable
private fun AnalyteListContent(
    analytes: List<LabAnalyteListItemUiState>,
    onEvent: (LabResultsUiEvent) -> Unit,
    modifier: Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(analytes, key = { it.id.toString() }) { item -> AnalyteRow(item, onEvent) }
    }
}

@Composable
private fun AnalyteRow(item: LabAnalyteListItemUiState, onEvent: (LabResultsUiEvent) -> Unit) {
    val latest = item.latest
    ListItem(
        headlineContent = { Text(item.label) },
        supportingContent = {
            val supportingText = if (latest != null) {
                "${Formatters.number(latest.value)} ${latest.unit}"
            } else {
                stringResource(R.string.health_no_results_for_analyte)
            }
            Text(supportingText)
        },
        modifier = Modifier.clickable { onEvent(LabResultsUiEvent.AnalyteOpened(item.id)) },
    )
}

@Composable
private fun AnalyteDetailContent(
    detail: LabAnalyteDetailUiState,
    onEvent: (LabResultsUiEvent) -> Unit,
    modifier: Modifier,
) {
    if (detail.results.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.labs_empty_title),
            body = stringResource(R.string.health_no_results_for_analyte),
            modifier = modifier,
        )
        return
    }
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        item { AnalyteChart(detail) }
        items(detail.results.asReversed(), key = { it.id.toString() }) { row -> ResultRow(row, onEvent) }
    }
}

@Composable
private fun AnalyteChart(detail: LabAnalyteDetailUiState) {
    val results = detail.results
    val first = results.first()
    val last = results.last()
    val summary = pluralStringResource(
        R.plurals.labs_chart_summary,
        results.size,
        detail.label,
        results.size,
        Formatters.shortDate(first.date),
        Formatters.shortDate(last.date),
        "${Formatters.number(last.value)} ${last.unit}",
    )
    LineChart(
        series = listOf(ChartSeries(detail.chartPoints)),
        summary = summary,
        band = detail.chartBand,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun ResultRow(row: LabResultRowUiState, onEvent: (LabResultsUiEvent) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${Formatters.number(row.value)} ${row.unit}", style = MaterialTheme.typography.bodyLarge)
                Text(Formatters.date(row.date), style = MaterialTheme.typography.bodySmall)
                row.labName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                row.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            IconButton(onClick = { onEvent(LabResultsUiEvent.EditResultRequested(row.id)) }) {
                Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.health_edit_result))
            }
            IconButton(onClick = { onEvent(LabResultsUiEvent.DeleteResultRequested(row.id)) }) {
                Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.health_delete_result))
            }
        }
        if (row.isOutOfRange) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column {
                    Text(stringResource(R.string.labs_out_of_range), style = MaterialTheme.typography.bodySmall)
                    Text(stringResource(R.string.labs_out_of_range_hint), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun NewAnalyteDialog(form: NewAnalyteFormUiState, onEvent: (LabResultsUiEvent) -> Unit) {
    val change: NewAnalyteChange = { apply -> onEvent(LabResultsUiEvent.NewAnalyteChanged(apply)) }
    AlertDialog(
        onDismissRequest = { onEvent(LabResultsUiEvent.NewAnalyteDismissed) },
        title = { Text(stringResource(R.string.health_add_analyte)) },
        text = {
            FormColumn {
                OutlinedTextField(
                    value = form.label,
                    onValueChange = { text -> change { it.copy(label = text) } },
                    label = { Text(stringResource(R.string.health_analyte_label_field)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.defaultUnit,
                    onValueChange = { text -> change { it.copy(defaultUnit = text) } },
                    label = { Text(stringResource(R.string.labs_unit)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                form.error?.let { Text(it.healthMessage()) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onEvent(LabResultsUiEvent.NewAnalyteSaved) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(LabResultsUiEvent.NewAnalyteDismissed) }) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun ResultFormDialog(form: LabResultFormUiState, onEvent: (LabResultsUiEvent) -> Unit) {
    val change: ResultChange = { apply -> onEvent(LabResultsUiEvent.ResultFormChanged(apply)) }
    val titleRes = if (form.editingId == null) R.string.health_add_result else R.string.health_edit_result
    AlertDialog(
        onDismissRequest = { onEvent(LabResultsUiEvent.ResultFormDismissed) },
        title = { Text(stringResource(titleRes)) },
        text = { ResultFormFields(form, change) },
        confirmButton = {
            TextButton(onClick = { onEvent(LabResultsUiEvent.ResultFormSaved) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(LabResultsUiEvent.ResultFormDismissed) }) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun ResultFormFields(form: LabResultFormUiState, change: ResultChange) {
    FormColumn(Modifier.verticalScroll(rememberScrollState())) {
        OutlinedTextField(
            value = form.valueText,
            onValueChange = { text -> change { it.copy(valueText = text) } },
            label = { Text(stringResource(R.string.labs_value)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.unit,
            onValueChange = { text -> change { it.copy(unit = text) } },
            label = { Text(stringResource(R.string.labs_unit)) },
            modifier = Modifier.fillMaxWidth(),
        )
        DateField(
            date = form.date,
            onDateChange = { date -> change { it.copy(date = date) } },
            label = stringResource(R.string.labs_collected_at),
        )
        ReferenceRangeFields(form, change)
        OutlinedTextField(
            value = form.labName,
            onValueChange = { text -> change { it.copy(labName = text) } },
            label = { Text(stringResource(R.string.labs_lab_name)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.notes,
            onValueChange = { text -> change { it.copy(notes = text) } },
            label = { Text(stringResource(R.string.health_notes_field)) },
            modifier = Modifier.fillMaxWidth(),
        )
        form.error?.let { Text(it.healthMessage()) }
    }
}

/** Faixa de referência do laudo: agrupada à parte para não estourar o limite de linhas. */
@Composable
private fun ReferenceRangeFields(form: LabResultFormUiState, change: ResultChange) {
    Text(stringResource(R.string.labs_reference_help), style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(
        value = form.referenceLowText,
        onValueChange = { text -> change { it.copy(referenceLowText = text) } },
        label = { Text(stringResource(R.string.labs_reference_low)) },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.referenceHighText,
        onValueChange = { text -> change { it.copy(referenceHighText = text) } },
        label = { Text(stringResource(R.string.labs_reference_high)) },
        modifier = Modifier.fillMaxWidth(),
    )
}
