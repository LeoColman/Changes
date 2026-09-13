// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.health

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import br.com.colman.changes.R
import br.com.colman.changes.core.model.ConditionSeverity
import br.com.colman.changes.core.model.ConditionStatus
import br.com.colman.changes.ui.components.ChangesScreen
import br.com.colman.changes.ui.components.DateField
import br.com.colman.changes.ui.components.DropdownField
import br.com.colman.changes.ui.components.EmptyState
import br.com.colman.changes.ui.components.FormColumn
import br.com.colman.changes.ui.components.LoadingState
import br.com.colman.changes.ui.components.SectionHeader
import br.com.colman.changes.ui.format.Formatters

private typealias FormChange = ((ConditionFormUiState) -> ConditionFormUiState) -> Unit

/**
 * Tela de Condições de saúde (Seção 7.5), sem estado próprio de negócio. Nenhum texto aqui julga,
 * classifica risco ou contraindica: o conteúdo vem inteiro de `strings_sensitive.xml`.
 */
@Composable
fun ConditionsScreen(
    state: ConditionsUiState,
    onEvent: (ConditionsUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    ChangesScreen(
        title = stringResource(R.string.conditions_title),
        onBack = onBack,
        snackbarHostState = snackbarHostState,
        floatingAction = {
            FloatingActionButton(onClick = { onEvent(ConditionsUiEvent.AddRequested) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.health_add_condition))
            }
        },
    ) { padding ->
        ConditionsContent(state, onEvent, modifier.padding(padding))
    }
    state.form?.let { form -> ConditionFormDialog(form, state.suggestions, onEvent) }
}

@Composable
private fun ConditionsContent(state: ConditionsUiState, onEvent: (ConditionsUiEvent) -> Unit, modifier: Modifier) {
    when {
        state.isLoading -> LoadingState(modifier = modifier)
        state.pinned.isEmpty() && state.others.isEmpty() -> EmptyState(
            title = stringResource(R.string.conditions_empty_title),
            body = stringResource(R.string.conditions_empty_body),
            modifier = modifier,
        )
        else -> LazyColumn(modifier = modifier) {
            if (state.pinned.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.conditions_pinned_header)) }
                items(state.pinned, key = { it.id.toString() }) { item -> ConditionRow(item, onEvent) }
            }
            if (state.others.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.conditions_other_header)) }
                items(state.others, key = { it.id.toString() }) { item -> ConditionRow(item, onEvent) }
            }
        }
    }
}

@Composable
private fun ConditionRow(item: ConditionUiState, onEvent: (ConditionsUiEvent) -> Unit) {
    ListItem(
        headlineContent = { Text(item.label) },
        supportingContent = {
            Column {
                val details = listOfNotNull(
                    item.code,
                    stringResource(severityLabelRes(item.severity)),
                    stringResource(statusLabelRes(item.status)),
                ).joinToString(" · ")
                Text(details)
                item.diagnosedAt?.let {
                    Text(stringResource(R.string.conditions_diagnosed_at) + ": " + Formatters.date(it))
                }
                item.notes?.let { Text(it) }
            }
        },
        trailingContent = {
            Column {
                IconButton(onClick = { onEvent(ConditionsUiEvent.EditRequested(item.id)) }) {
                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.health_edit_condition))
                }
                IconButton(onClick = { onEvent(ConditionsUiEvent.DeleteRequested(item.id)) }) {
                    Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.health_delete_condition))
                }
            }
        },
    )
}

@Composable
private fun ConditionFormDialog(
    form: ConditionFormUiState,
    suggestions: List<String>,
    onEvent: (ConditionsUiEvent) -> Unit,
) {
    val change: FormChange = { apply -> onEvent(ConditionsUiEvent.FormChanged(apply)) }
    val titleRes = if (form.editingId == null) R.string.health_add_condition else R.string.health_edit_condition
    AlertDialog(
        onDismissRequest = { onEvent(ConditionsUiEvent.FormDismissed) },
        title = { Text(stringResource(titleRes)) },
        text = { ConditionFormFields(form, suggestions, change) },
        confirmButton = {
            TextButton(onClick = { onEvent(ConditionsUiEvent.FormSaved) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(ConditionsUiEvent.FormDismissed) }) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun ConditionFormFields(form: ConditionFormUiState, suggestions: List<String>, change: FormChange) {
    FormColumn(Modifier.verticalScroll(rememberScrollState())) {
        ConditionLabelField(
            value = form.label,
            suggestions = suggestions,
            onValueChange = { text -> change { it.copy(label = text) } },
        )
        OutlinedTextField(
            value = form.code,
            onValueChange = { text -> change { it.copy(code = text) } },
            label = { Text(stringResource(R.string.conditions_code)) },
            modifier = Modifier.fillMaxWidth(),
        )
        ConditionClassificationFields(form, change)
        AffectsTreatmentField(form.affectsTreatment, change)
        OutlinedTextField(
            value = form.notes,
            onValueChange = { text -> change { it.copy(notes = text) } },
            label = { Text(stringResource(R.string.health_notes_field)) },
            modifier = Modifier.fillMaxWidth(),
        )
        form.error?.let { Text(it.healthMessage()) }
    }
}

/** Gravidade, situação e as duas datas: agrupados à parte para não estourar o limite de linhas. */
@Composable
private fun ConditionClassificationFields(form: ConditionFormUiState, change: FormChange) {
    DropdownField(
        options = ConditionSeverity.entries,
        selected = form.severity,
        onSelect = { severity -> change { it.copy(severity = severity) } },
        label = stringResource(R.string.conditions_severity),
        optionLabel = { stringResource(severityLabelRes(it)) },
    )
    DropdownField(
        options = ConditionStatus.entries,
        selected = form.status,
        onSelect = { status -> change { it.copy(status = status) } },
        label = stringResource(R.string.conditions_status),
        optionLabel = { stringResource(statusLabelRes(it)) },
    )
    DateField(
        date = form.diagnosedAt,
        onDateChange = { date -> change { it.copy(diagnosedAt = date) } },
        label = stringResource(R.string.conditions_diagnosed_at),
    )
    DateField(
        date = form.resolvedAt,
        onDateChange = { date -> change { it.copy(resolvedAt = date) } },
        label = stringResource(R.string.conditions_resolved_at),
    )
}

@Composable
private fun AffectsTreatmentField(affectsTreatment: Boolean, change: FormChange) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.conditions_affects_treatment)) },
        supportingContent = { Text(stringResource(R.string.conditions_affects_treatment_help)) },
        trailingContent = {
            Switch(
                checked = affectsTreatment,
                onCheckedChange = { checked -> change { it.copy(affectsTreatment = checked) } },
            )
        },
    )
}

/** Campo de rótulo com autocompletar a partir de [suggestions]: entrada livre, sem restrição à lista. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionLabelField(value: String, suggestions: List<String>, onValueChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = if (value.isBlank()) suggestions else suggestions.filter { it.contains(value, ignoreCase = true) }
    ExposedDropdownMenuBox(expanded = expanded && filtered.isNotEmpty(), onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(stringResource(R.string.conditions_label)) },
            supportingText = { Text(stringResource(R.string.conditions_suggestions_hint)) },
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && filtered.isNotEmpty()) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded && filtered.isNotEmpty(), onDismissRequest = { expanded = false }) {
            filtered.forEach { suggestion ->
                DropdownMenuItem(
                    text = { Text(suggestion) },
                    onClick = {
                        onValueChange(suggestion)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun severityLabelRes(severity: ConditionSeverity): Int = when (severity) {
    ConditionSeverity.MILD -> R.string.conditions_severity_mild
    ConditionSeverity.MODERATE -> R.string.conditions_severity_moderate
    ConditionSeverity.SEVERE -> R.string.conditions_severity_severe
    ConditionSeverity.UNKNOWN -> R.string.conditions_severity_unknown
}

private fun statusLabelRes(status: ConditionStatus): Int = when (status) {
    ConditionStatus.ACTIVE -> R.string.conditions_status_active
    ConditionStatus.RESOLVED -> R.string.conditions_status_resolved
    ConditionStatus.IN_REMISSION -> R.string.conditions_status_in_remission
    ConditionStatus.SUSPECTED -> R.string.conditions_status_suspected
}
