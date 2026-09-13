// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import br.com.colman.changes.R
import br.com.colman.changes.core.model.UnitSystem
import br.com.colman.changes.ui.components.ChangesScreen
import br.com.colman.changes.ui.components.DateField
import br.com.colman.changes.ui.components.DropdownField
import br.com.colman.changes.ui.components.FormColumn
import br.com.colman.changes.ui.components.LoadingState
import br.com.colman.changes.ui.components.NumberField

/** Tela de Perfil, sem estado próprio de negócio (Seção 5): nenhum campo é obrigatório. */
@Composable
fun ProfileScreen(
    state: ProfileUiState,
    onEvent: (ProfileUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ChangesScreen(title = stringResource(R.string.settings_section_profile), onBack = onBack) { padding ->
        if (state.isLoading) {
            LoadingState(modifier = modifier.padding(padding))
        } else {
            ProfileForm(state, onEvent, modifier.padding(padding))
        }
    }
}

@Composable
private fun ProfileForm(state: ProfileUiState, onEvent: (ProfileUiEvent) -> Unit, modifier: Modifier) {
    FormColumn(modifier = modifier.verticalScroll(rememberScrollState())) {
        OutlinedTextField(
            value = state.displayName,
            onValueChange = { onEvent(ProfileUiEvent.DisplayNameChanged(it)) },
            label = { Text(stringResource(R.string.settings_profile_display_name)) },
        )
        DateField(
            date = state.hrtStartDate,
            onDateChange = { onEvent(ProfileUiEvent.HrtStartDateChanged(it)) },
            label = stringResource(R.string.settings_profile_hrt_start_date),
        )
        DropdownField(
            options = UnitSystem.entries,
            selected = state.unitSystem,
            onSelect = { onEvent(ProfileUiEvent.UnitSystemChanged(it)) },
            label = stringResource(R.string.settings_profile_unit_system),
            optionLabel = { stringResource(unitSystemLabelRes(it)) },
        )
        NumberField(
            value = state.heightText,
            onValueChange = { onEvent(ProfileUiEvent.HeightChanged(it)) },
            label = stringResource(heightLabelRes(state.unitSystem)),
        )
        NumberField(
            value = state.birthYearText,
            onValueChange = { onEvent(ProfileUiEvent.BirthYearChanged(it)) },
            label = stringResource(R.string.settings_profile_birth_year),
        )
        ToggleRow(
            label = stringResource(R.string.settings_profile_show_bmi, stringResource(R.string.bmi_label)),
            checked = state.showBmi,
            onCheckedChange = { onEvent(ProfileUiEvent.ShowBmiChanged(it)) },
            enabled = state.heightText.isNotBlank(),
        )
    }
}

private fun heightLabelRes(unitSystem: UnitSystem): Int =
    if (unitSystem == UnitSystem.IMPERIAL) R.string.settings_profile_height_in else R.string.settings_profile_height_cm

private fun unitSystemLabelRes(unitSystem: UnitSystem): Int = when (unitSystem) {
    UnitSystem.METRIC -> R.string.settings_unit_metric
    UnitSystem.IMPERIAL -> R.string.settings_unit_imperial
}
