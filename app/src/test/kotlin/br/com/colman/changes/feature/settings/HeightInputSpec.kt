// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.settings

import br.com.colman.changes.core.model.UnitSystem
import br.com.colman.changes.core.model.Units
import br.com.colman.changes.ui.format.Formatters
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/** Seção 6.1: altura sempre gravada em cm, exibida e lida no sistema de unidades escolhido na tela de Perfil. */
class HeightInputSpec : FunSpec({
    test("metric shows the recorded centimeters as-is") {
        Formatters.parseNumber(formatHeight(182.0, UnitSystem.METRIC)) shouldBe (182.0 plusOrMinus 0.001)
    }

    test("imperial converts to inches instead of showing the raw centimeters") {
        val shown = Formatters.parseNumber(formatHeight(182.0, UnitSystem.IMPERIAL))

        shown.shouldNotBeNull()
        shown shouldBe (182.0 / Units.CM_PER_INCH plusOrMinus 0.01)
    }

    test("reads the typed value back into centimeters, converting from the chosen unit") {
        parseHeightCm("10", UnitSystem.IMPERIAL) shouldBe (10.0 * Units.CM_PER_INCH plusOrMinus 0.001)
        parseHeightCm("175", UnitSystem.METRIC) shouldBe (175.0 plusOrMinus 0.001)
    }

    test("empty or unparseable text is null, not zero") {
        parseHeightCm("", UnitSystem.METRIC).shouldBeNull()
        parseHeightCm("abc", UnitSystem.IMPERIAL).shouldBeNull()
    }
})
