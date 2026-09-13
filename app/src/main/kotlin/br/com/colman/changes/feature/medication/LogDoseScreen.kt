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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.colman.changes.R
import br.com.colman.changes.core.model.DoseUnit
import br.com.colman.changes.core.model.InjectionSite
import br.com.colman.changes.core.model.Route
import br.com.colman.changes.ui.components.ChangesScreen
import br.com.colman.changes.ui.components.ConfirmDialog
import br.com.colman.changes.ui.components.DateField
import br.com.colman.changes.ui.components.DropdownField
import br.com.colman.changes.ui.components.FormColumn
import br.com.colman.changes.ui.components.LoadingState
import br.com.colman.changes.ui.components.NumberField
import br.com.colman.changes.ui.components.TimeField

private typealias LogChange = ((LogDoseUiState) -> LogDoseUiState) -> Unit

/** Registrar dose (Seção 7.1). Stateless: toda mutação sai como [LogDoseUiEvent]. */
@Composable
fun LogDoseScreen(state: LogDoseUiState, onEvent: (LogDoseUiEvent) -> Unit, modifier: Modifier = Modifier) {
    val titleRes = if (state.isEditing) R.string.medication_log_title_edit else R.string.medication_log_title_new
    Box(modifier) {
        ChangesScreen(
            title = stringResource(titleRes),
            onBack = { onEvent(LogDoseUiEvent.Back) },
            actions = {
                if (state.isEditing) {
                    IconButton(onClick = { onEvent(LogDoseUiEvent.RequestDelete) }) {
                        Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
                    }
                }
            },
        ) { padding ->
            if (state.isLoading) {
                LoadingState(Modifier.padding(padding))
            } else {
                LogDoseForm(state, onEvent, Modifier.padding(padding).verticalScroll(rememberScrollState()))
            }
        }
    }
    if (state.showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.medication_log_delete_confirm_title),
            text = stringResource(R.string.medication_log_delete_confirm_body),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { onEvent(LogDoseUiEvent.ConfirmDelete) },
            onDismiss = { onEvent(LogDoseUiEvent.CancelDelete) },
        )
    }
}

@Composable
private fun LogDoseForm(state: LogDoseUiState, onEvent: (LogDoseUiEvent) -> Unit, modifier: Modifier = Modifier) {
    val change: LogChange = { apply -> onEvent(LogDoseUiEvent.FieldChanged(apply)) }

    FormColumn(modifier) {
        MedicationField(state, change)
        DoseAndRouteSection(state, change)
        DateTimeSection(state, change)
        OutlinedTextField(
            value = state.notes,
            onValueChange = { notes -> change { it.copy(notes = notes) } },
            label = { Text(stringResource(R.string.medication_field_notes)) },
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.error != null) {
            Text(state.error.medicationMessage(), color = MaterialTheme.colorScheme.error)
        }
        Button(onClick = { onEvent(LogDoseUiEvent.Save) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_save))
        }
    }
}

@Composable
private fun DoseAndRouteSection(state: LogDoseUiState, change: LogChange) {
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
    if (state.route.isInjection) {
        DropdownField(
            options = InjectionSite.entries,
            selected = state.injectionSite,
            onSelect = { site -> change { it.copy(injectionSite = site) } },
            label = stringResource(R.string.medication_field_injection_site),
            optionLabel = { injectionSiteLabel(it) },
        )
    }
}

@Composable
private fun DateTimeSection(state: LogDoseUiState, change: LogChange) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DateField(
            date = state.date,
            onDateChange = { date -> change { it.copy(date = date) } },
            label = stringResource(R.string.medication_field_date),
            modifier = Modifier.weight(1f),
        )
        TimeField(
            time = state.time,
            onTimeChange = { time -> change { it.copy(time = time) } },
            label = stringResource(R.string.medication_field_time),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MedicationField(state: LogDoseUiState, change: LogChange) {
    if (state.medicationEditable) {
        val selected = state.availableMedications.firstOrNull { it.id == state.selectedMedicationId }
        DropdownField(
            options = state.availableMedications,
            selected = selected,
            onSelect = { option -> change { it.copy(selectedMedicationId = option.id) } },
            label = stringResource(R.string.medication_field_medication),
            optionLabel = { it.name },
        )
    } else {
        val name = state.availableMedications.firstOrNull { it.id == state.selectedMedicationId }?.name.orEmpty()
        OutlinedTextField(
            value = name,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(stringResource(R.string.medication_field_medication)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
