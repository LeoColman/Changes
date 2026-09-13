// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import br.com.colman.changes.R
import br.com.colman.changes.core.model.BodyChangeCategory
import br.com.colman.changes.core.model.BodyMeasurementUnit
import br.com.colman.changes.core.model.Intensity

/** Rótulos de exibição que não citam anatomia: categorias, unidades e níveis de intensidade. */
@Composable
fun categoryLabel(category: BodyChangeCategory): String = stringResource(
    when (category) {
        BodyChangeCategory.SKIN -> R.string.body_category_skin
        BodyChangeCategory.HAIR -> R.string.body_category_hair
        BodyChangeCategory.BODY -> R.string.body_category_body
        BodyChangeCategory.SEXUAL_REPRODUCTIVE -> R.string.body_category_sexual_reproductive
        BodyChangeCategory.VOICE -> R.string.body_category_voice
        BodyChangeCategory.EMOTIONAL -> R.string.body_category_emotional
        BodyChangeCategory.PAIN -> R.string.body_category_pain
        BodyChangeCategory.OTHER -> R.string.body_category_other
    },
)

@Composable
fun intensityLabel(intensity: Intensity?): String = stringResource(
    when (intensity) {
        null -> R.string.body_intensity_unspecified
        Intensity.NONE -> R.string.body_intensity_none
        Intensity.MILD -> R.string.body_intensity_mild
        Intensity.MODERATE -> R.string.body_intensity_moderate
        Intensity.MARKED -> R.string.body_intensity_marked
        Intensity.COMPLETE -> R.string.body_intensity_complete
    },
)

@Composable
fun measurementUnitLabel(unit: BodyMeasurementUnit?): String = stringResource(
    when (unit) {
        null -> R.string.body_unit_none
        BodyMeasurementUnit.CM -> R.string.body_unit_cm
        BodyMeasurementUnit.IN -> R.string.body_unit_in
        BodyMeasurementUnit.HZ -> R.string.body_unit_hz
        BodyMeasurementUnit.KG -> R.string.body_unit_kg
        BodyMeasurementUnit.LB -> R.string.body_unit_lb
        BodyMeasurementUnit.PERCENT -> R.string.body_unit_percent
    },
)

@Composable
fun measurementUnitSymbol(unit: BodyMeasurementUnit): String = stringResource(
    when (unit) {
        BodyMeasurementUnit.CM -> R.string.body_unit_symbol_cm
        BodyMeasurementUnit.IN -> R.string.body_unit_symbol_in
        BodyMeasurementUnit.HZ -> R.string.body_unit_symbol_hz
        BodyMeasurementUnit.KG -> R.string.body_unit_symbol_kg
        BodyMeasurementUnit.LB -> R.string.body_unit_symbol_lb
        BodyMeasurementUnit.PERCENT -> R.string.body_unit_symbol_percent
    },
)

@Composable
fun errorMessageText(error: BodyErrorMessage): String = stringResource(
    when (error) {
        BodyErrorMessage.REQUIRED_FIELD -> R.string.body_error_required_field
        BodyErrorMessage.FUTURE_DATE -> R.string.body_error_future_date
        BodyErrorMessage.MEASUREMENT_UNSUPPORTED -> R.string.body_error_measurement_unsupported
        BodyErrorMessage.INVALID_MEASUREMENT -> R.string.body_error_invalid_measurement
        BodyErrorMessage.BUILTIN_IMMUTABLE -> R.string.body_error_builtin_immutable
        BodyErrorMessage.NOT_FOUND -> R.string.body_error_not_found
        BodyErrorMessage.UNKNOWN -> R.string.body_error_unknown
    },
)
