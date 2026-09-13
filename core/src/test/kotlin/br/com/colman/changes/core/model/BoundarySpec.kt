// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.model

import br.com.colman.changes.core.model.MeasurementUnit.CM
import br.com.colman.changes.core.model.MeasurementUnit.IN
import br.com.colman.changes.core.model.MeasurementUnit.KG
import br.com.colman.changes.core.model.MeasurementUnit.LB
import br.com.colman.changes.core.model.MeasurementUnit.PERCENT
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Limites exatos de conversão e IMC: onde `<` e `<=` se distinguem. */
class BoundarySpec : FunSpec({
    val notFinite = DomainError.Invalid("value", DomainError.Reason.NOT_FINITE)
    val incompatible = DomainError.Invalid("unit", DomainError.Reason.OUT_OF_RANGE)

    test("every pair of measurement units is either convertible or rejected") {
        val convertible = setOf(KG to LB, LB to KG, CM to IN, IN to CM)
        MeasurementUnit.entries.forEach { from ->
            MeasurementUnit.entries.forEach { to ->
                val result = Units.convert(10.0, from, to)
                if (from == to) {
                    result.getOrNull() shouldBe 10.0
                } else if ((from to to) in convertible) {
                    result.isSuccess shouldBe true
                } else {
                    result.errorOrNull() shouldBe incompatible
                }
            }
        }
        Units.convert(10.0, KG, LB).getOrNull() shouldBe (10.0 / Units.KG_PER_LB plusOrMinus 1e-12)
        Units.convert(10.0, LB, KG).getOrNull() shouldBe (10.0 * Units.KG_PER_LB plusOrMinus 1e-12)
        Units.convert(10.0, CM, IN).getOrNull() shouldBe (10.0 / Units.CM_PER_INCH plusOrMinus 1e-12)
        Units.convert(10.0, IN, CM).getOrNull() shouldBe (10.0 * Units.CM_PER_INCH plusOrMinus 1e-12)
        Units.convert(10.0, PERCENT, LB).errorOrNull() shouldBe incompatible
    }

    test("the largest finite double is still finite") {
        Units.convert(Double.MAX_VALUE, KG, KG).getOrNull() shouldBe Double.MAX_VALUE
        Units.convert(Double.MAX_VALUE, LB, KG).isSuccess shouldBe true
        Units.convert(-Double.MAX_VALUE, LB, KG).isSuccess shouldBe true
        Units.convert(Double.NEGATIVE_INFINITY, KG, KG).errorOrNull() shouldBe notFinite
        Units.mgToMl(Double.MAX_VALUE, Concentration(1.0, ConcentrationUnit.MG_PER_ML)).getOrNull() shouldBe Double.MAX_VALUE
        Units.mlToMg(Double.MAX_VALUE, Concentration(1.0, ConcentrationUnit.MG_PER_ML)).getOrNull() shouldBe Double.MAX_VALUE
        Units.mgToMl(1.0, Concentration(Double.MAX_VALUE, ConcentrationUnit.MG_PER_ML)).getOrNull() shouldBe 1.0 / Double.MAX_VALUE
        Units.mgToMl(1.0, Concentration(Double.MIN_VALUE, ConcentrationUnit.MG_PER_ML)).errorOrNull() shouldBe notFinite
        Units.mgToMl(Double.NaN, Concentration(1.0, ConcentrationUnit.MG_PER_ML)).errorOrNull() shouldBe notFinite
        Units.mlToMg(Double.POSITIVE_INFINITY, Concentration(1.0, ConcentrationUnit.MG_PER_ML)).errorOrNull() shouldBe notFinite
        Units.mgToMl(1.0, Concentration(Double.MIN_VALUE * 1e10, ConcentrationUnit.MG_PER_ML)).isSuccess shouldBe false
    }

    test("a BMI of exactly the largest double is returned, one past it is rejected") {
        Bmi.calculate(Double.MAX_VALUE, 100.0).getOrNull() shouldBe Double.MAX_VALUE
        Bmi.calculate(Double.MAX_VALUE, 99.0).errorOrNull() shouldBe DomainError.Invalid("bmi", DomainError.Reason.NOT_FINITE)
        Bmi.calculate(70.0, Double.MAX_VALUE).getOrNull() shouldBe 0.0
        Bmi.calculate(70.0, Double.MIN_VALUE).errorOrNull() shouldBe DomainError.Invalid("bmi", DomainError.Reason.NOT_FINITE)
        Bmi.calculate(70.0, 0.0).errorOrNull() shouldBe DomainError.Invalid("heightCm", DomainError.Reason.NOT_POSITIVE)
        Bmi.calculate(70.0, Double.NEGATIVE_INFINITY).errorOrNull() shouldBe DomainError.Invalid("heightCm", DomainError.Reason.NOT_FINITE)
    }

    test("agenda items expose their local date") {
        val at = RecordedTime.fromDb(Instant.parse("2026-09-12T02:00:00Z").toEpochMilliseconds(), -3L * 3600)
        val log = DoseLog(Uuid.random(), null, Uuid.random(), Dose(1.0, DoseUnit.MG), Route.ORAL, null, at, null)
        AgendaItem.LoggedDose(log).date shouldBe LocalDate(2026, 9, 11)
        AgendaItem.Milestone("VOICE_DEEPENING", LocalDate(2026, 1, 1)).changeTypeCode shouldBe "VOICE_DEEPENING"
        AgendaItem.PlannedDose(Uuid.random(), Uuid.random(), LocalDate(2026, 1, 1), null).at shouldBe null
        val instant = Instant.parse("2026-01-01T11:00:00Z")
        AgendaItem.PlannedDose(Uuid.random(), Uuid.random(), LocalDate(2026, 1, 1), instant).at shouldBe instant
    }
})
