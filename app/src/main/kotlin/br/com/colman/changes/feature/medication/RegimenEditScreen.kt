// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.medication

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.colman.changes.R
import br.com.colman.changes.core.model.DoseUnit
import br.com.colman.changes.core.model.Route
import br.com.colman.changes.ui.components.ChangesScreen
import br.com.colman.changes.ui.components.ConfirmDialog
import br.com.colman.changes.ui.components.DateField
import br.com.colman.changes.ui.components.DropdownField
import br.com.colman.changes.ui.components.FormColumn
import br.com.colman.changes.ui.components.LoadingState
import br.com.colman.changes.ui.components.NumberField
import br.com.colman.changes.ui.components.SectionHeader

/** Ação de campo simples de formulário: aplica uma transformação ao estado atual. */
internal typealias Change = ((RegimenEditUiState) -> RegimenEditUiState) -> Unit

/** Criar/editar regime (Seção 7.1). Stateless: toda mutação sai como [RegimenEditUiEvent]. */
@Composable
fun RegimenEditScreen(state: RegimenEditUiState, onEvent: (RegimenEditUiEvent) -> Unit, modifier: Modifier = Modifier) {
    Box(modifier) {
        ChangesScreen(
            title = stringResource(
                if (state.isNew) R.string.medication_edit_title_new else R.string.medication_edit_title_edit,
            ),
            onBack = { onEvent(RegimenEditUiEvent.Back) },
            actions = {
                if (!state.isNew) {
                    IconButton(onClick = { onEvent(RegimenEditUiEvent.RequestDelete) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                }
            },
        ) { padding ->
            if (state.isLoading) {
                LoadingState(Modifier.padding(padding))
            } else {
                RegimenEditForm(state, onEvent, Modifier.padding(padding).verticalScroll(rememberScrollState()))
            }
        }
    }
    if (state.showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.medication_delete_confirm_title),
            text = stringResource(R.string.medication_delete_confirm_body),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { onEvent(RegimenEditUiEvent.ConfirmDelete) },
            onDismiss = { onEvent(RegimenEditUiEvent.CancelDelete) },
        )
    }
}

@Composable
private fun RegimenEditForm(
    state: RegimenEditUiState,
    onEvent: (RegimenEditUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val change: Change = { apply -> onEvent(RegimenEditUiEvent.FieldChanged(apply)) }

    FormColumn(modifier) {
        SectionHeader(stringResource(R.string.medication_section_medication))
        MedicationPicker(state, onEvent, change)
        HorizontalDivider()

        SectionHeader(stringResource(R.string.medication_section_dose))
        DoseSection(state, change)
        HorizontalDivider()

        SectionHeader(stringResource(R.string.medication_section_schedule))
        ScheduleFields(state, change)
        HorizontalDivider()

        SectionHeader(stringResource(R.string.medication_section_dates))
        DatesSection(state, change)

        NotesAndSave(state, onEvent, change)
    }
}

@Composable
private fun DoseSection(state: RegimenEditUiState, change: Change) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(
            value = state.doseValue,
            onValueChange = { value -> change { it.copy(doseValue = value) } },
            label = stringResource(R.string.medication_field_dose_value),
            modifier = Modifier.weight(1f),
        )
        DropdownField(
            options = DoseUnit.entries,
            selected = state.doseUnit,
            onSelect = { unit -> change { it.copy(doseUnit = unit) } },
            label = stringResource(R.string.medication_field_dose_unit),
            optionLabel = { doseUnitLabel(it) },
            modifier = Modifier.weight(1f),
        )
    }
    DropdownField(
        options = Route.entries,
        selected = state.route,
        onSelect = { route -> change { it.copy(route = route) } },
        label = stringResource(R.string.medication_field_route),
        optionLabel = { routeLabel(it) },
    )
}

@Composable
private fun DatesSection(state: RegimenEditUiState, change: Change) {
    DateField(
        date = state.startDate,
        onDateChange = { date -> change { it.copy(startDate = date) } },
        label = stringResource(R.string.medication_field_start_date),
    )
    EndDateField(state, change)
    if (!state.isNew) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.medication_field_active), modifier = Modifier.weight(1f))
            Switch(checked = state.isActive, onCheckedChange = { active -> change { it.copy(isActive = active) } })
        }
    }
}

@Composable
private fun NotesAndSave(state: RegimenEditUiState, onEvent: (RegimenEditUiEvent) -> Unit, change: Change) {
    OutlinedTextField(
        value = state.notes,
        onValueChange = { notes -> change { it.copy(notes = notes) } },
        label = { Text(stringResource(R.string.medication_field_notes)) },
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.error != null) {
        Text(state.error.medicationMessage(), color = MaterialTheme.colorScheme.error)
    }
    Button(onClick = { onEvent(RegimenEditUiEvent.Save) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_save))
    }
}

@Composable
private fun MedicationPicker(state: RegimenEditUiState, onEvent: (RegimenEditUiEvent) -> Unit, change: Change) {
    val selected = state.availableMedications.firstOrNull { it.id == state.selectedMedicationId }
    DropdownField(
        options = state.availableMedications,
        selected = selected,
        onSelect = { option ->
            change {
                it.copy(
                    selectedMedicationId = option.id,
                    route = it.route ?: option.defaultRoute,
                )
            }
        },
        label = stringResource(R.string.medication_field_medication),
        optionLabel = { it.name },
    )
    if (state.isCreatingMedication) {
        NewMedicationForm(state, onEvent, change)
    } else {
        TextButton(onClick = { onEvent(RegimenEditUiEvent.StartCreatingMedication) }) {
            Text(stringResource(R.string.medication_action_new_medication))
        }
    }
}

@Composable
private fun NewMedicationForm(state: RegimenEditUiState, onEvent: (RegimenEditUiEvent) -> Unit, change: Change) {
    OutlinedTextField(
        value = state.newMedicationName,
        onValueChange = { value -> change { it.copy(newMedicationName = value) } },
        label = { Text(stringResource(R.string.medication_field_new_name)) },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.newMedicationSubstance,
        onValueChange = { value -> change { it.copy(newMedicationSubstance = value) } },
        label = { Text(stringResource(R.string.medication_field_new_substance)) },
        modifier = Modifier.fillMaxWidth(),
    )
    DropdownField(
        options = Route.entries,
        selected = state.newMedicationRoute,
        onSelect = { route -> change { it.copy(newMedicationRoute = route) } },
        label = stringResource(R.string.medication_field_new_default_route),
        optionLabel = { routeLabel(it) },
    )
    NumberField(
        value = state.newMedicationConcentrationValue,
        onValueChange = { value -> change { it.copy(newMedicationConcentrationValue = value) } },
        label = stringResource(R.string.medication_field_new_concentration),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { onEvent(RegimenEditUiEvent.CancelCreatingMedication) }) {
            Text(stringResource(R.string.action_cancel))
        }
        Button(onClick = { onEvent(RegimenEditUiEvent.ConfirmNewMedication) }) {
            Text(stringResource(R.string.medication_action_confirm_new_medication))
        }
    }
}

@Composable
private fun EndDateField(state: RegimenEditUiState, change: Change) {
    DateField(
        date = state.endDate,
        onDateChange = { date -> change { it.copy(endDate = date) } },
        label = stringResource(R.string.medication_field_end_date),
    )
    if (state.endDate != null) {
        TextButton(onClick = { change { it.copy(endDate = null) } }) {
            Text(stringResource(R.string.medication_field_end_date_clear))
        }
    }
}
