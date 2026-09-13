// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.settings

import br.com.colman.changes.core.model.MeasurementUnit
import br.com.colman.changes.core.model.UnitSystem
import br.com.colman.changes.core.model.Units
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.ui.format.Formatters

/**
 * Altura sempre gravada em cm no perfil (Seção 6.1); a tela de Perfil exibe e lê no sistema de
 * unidades escolhido. Usado só pela tela de Perfil: o passo de altura do onboarding é sempre em cm.
 */
internal fun heightDisplayUnit(unitSystem: UnitSystem): MeasurementUnit =
    if (unitSystem == UnitSystem.IMPERIAL) MeasurementUnit.IN else MeasurementUnit.CM

/** Altura em cm formatada no sistema de unidades escolhido, para preencher o campo de texto. */
internal fun formatHeight(heightCm: Double, unitSystem: UnitSystem): String {
    val unit = heightDisplayUnit(unitSystem)
    val value = Units.convert(heightCm, MeasurementUnit.CM, unit).getOrNull() ?: heightCm
    return Formatters.number(value)
}

/** Lê o texto digitado no sistema de unidades escolhido e devolve a altura em cm, ou `null` se vazio ou inválido. */
internal fun parseHeightCm(text: String, unitSystem: UnitSystem): Double? {
    val value = Formatters.parseNumber(text) ?: return null
    val unit = heightDisplayUnit(unitSystem)
    return Units.convert(value, unit, MeasurementUnit.CM).getOrNull()
}
