// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.vitals

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import br.com.colman.changes.R
import br.com.colman.changes.core.model.MeasurementType
import br.com.colman.changes.core.model.MeasurementUnit
import br.com.colman.changes.ui.components.DropdownField
import br.com.colman.changes.ui.format.Formatters

/** Nome do tipo e unidade quando a medida é personalizada: rótulo digitado e unidade escolhida. */
@Composable
fun CustomMeasurementFields(form: MeasurementFormUiState, onEvent: (MeasurementsUiEvent) -> Unit) {
    OutlinedTextField(
        value = form.customLabel,
        onValueChange = { onEvent(MeasurementsUiEvent.FormCustomLabelChanged(it)) },
        label = { Text(stringResource(R.string.vitals_field_custom_label)) },
        modifier = Modifier.fillMaxWidth(),
    )
    DropdownField(
        options = MeasurementUnit.entries,
        selected = form.unit,
        onSelect = { onEvent(MeasurementsUiEvent.FormUnitChanged(it)) },
        label = stringResource(R.string.vitals_field_unit),
        optionLabel = { unit -> stringResource(unitLabelRes(unit)) },
    )
}

/** Valor formatado por locale, seguido da unidade de exibição. */
@Composable
fun formattedValue(entry: MeasurementEntryUiState): String = stringResource(
    R.string.vitals_value_with_unit,
    Formatters.number(entry.value),
    stringResource(unitLabelRes(entry.unit)),
)

fun unitLabelRes(unit: MeasurementUnit): Int = when (unit) {
    MeasurementUnit.KG -> R.string.vitals_unit_kg
    MeasurementUnit.LB -> R.string.vitals_unit_lb
    MeasurementUnit.CM -> R.string.vitals_unit_cm
    MeasurementUnit.IN -> R.string.vitals_unit_in
    MeasurementUnit.PERCENT -> R.string.vitals_unit_percent
}

/** Rótulo do tipo fixo. Tórax não passa por aqui: vem do `BodyVocabularyResolver` (ver [MeasurementSectionUiState]). */
fun fixedLabelRes(type: MeasurementType): Int = when (type) {
    MeasurementType.WEIGHT -> R.string.vitals_type_weight
    MeasurementType.WAIST -> R.string.vitals_type_waist
    MeasurementType.HIP -> R.string.vitals_type_hip
    MeasurementType.BICEP -> R.string.vitals_type_bicep
    MeasurementType.NECK -> R.string.vitals_type_neck
    MeasurementType.BODY_FAT_PCT -> R.string.vitals_type_body_fat
    MeasurementType.CHEST, MeasurementType.CUSTOM -> R.string.vitals_type_custom
}
