// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.model

import br.com.colman.changes.core.testing.propertyIterations
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.numericDouble
import io.kotest.property.checkAll

/** Seção 11.2, item 4: ida e volta dentro de epsilon; nunca NaN/Infinity. */
class UnitsSpec : FunSpec({
    val realistic = Arb.numericDouble(0.0, 1_000_000.0)
    val anyDouble = Arb.double()
    val units = Arb.element(MeasurementUnit.entries)

    fun relativeTolerance(value: Double) = maxOf(1e-9, value * 1e-12)

    test("kg to lb and back preserves the value") {
        checkAll(propertyIterations(1000), realistic) { kg ->
            val lb = Units.convert(kg, MeasurementUnit.KG, MeasurementUnit.LB).getOrNull().shouldNotBeNull()
            Units.convert(lb, MeasurementUnit.LB, MeasurementUnit.KG).getOrNull() shouldBe (kg plusOrMinus relativeTolerance(kg))
        }
    }

    test("cm to in and back preserves the value") {
        checkAll(propertyIterations(1000), realistic) { cm ->
            val inches = Units.convert(cm, MeasurementUnit.CM, MeasurementUnit.IN).getOrNull().shouldNotBeNull()
            Units.convert(inches, MeasurementUnit.IN, MeasurementUnit.CM).getOrNull() shouldBe (cm plusOrMinus relativeTolerance(cm))
        }
    }

    test("mg to mL and back preserves the value for any positive mg/mL concentration") {
        val concentrations = Arb.numericDouble(0.001, 1000.0)
        checkAll(propertyIterations(1000), Arb.numericDouble(0.0, 10_000.0), concentrations) { mg, c ->
            val concentration = Concentration(c, ConcentrationUnit.MG_PER_ML)
            val ml = Units.mgToMl(mg, concentration).getOrNull().shouldNotBeNull()
            Units.mlToMg(ml, concentration).getOrNull() shouldBe (mg plusOrMinus maxOf(1e-9, mg * 1e-12))
        }
    }

    test("known conversions") {
        Units.convert(1.0, MeasurementUnit.LB, MeasurementUnit.KG).getOrNull() shouldBe 0.45359237
        Units.convert(1.0, MeasurementUnit.IN, MeasurementUnit.CM).getOrNull() shouldBe 2.54
        Units.convert(0.45359237, MeasurementUnit.KG, MeasurementUnit.LB).getOrNull() shouldBe (1.0 plusOrMinus 1e-12)
        Units.convert(2.54, MeasurementUnit.CM, MeasurementUnit.IN).getOrNull() shouldBe (1.0 plusOrMinus 1e-12)
        Units.convert(12.5, MeasurementUnit.PERCENT, MeasurementUnit.PERCENT).getOrNull() shouldBe 12.5
        val deposteron = Concentration(100.0, ConcentrationUnit.MG_PER_ML)
        Units.mgToMl(200.0, deposteron).getOrNull() shouldBe 2.0
        Units.mlToMg(1.0, deposteron).getOrNull() shouldBe 100.0
    }

    test("conversion never produces NaN or Infinity, for any input") {
        checkAll(propertyIterations(2000), anyDouble, units, units) { value, from, to ->
            val result = Units.convert(value, from, to)
            val converted = result.getOrNull()
            if (converted != null) converted.isFinite().shouldBeTrue()
            if (!value.isFinite()) result.errorOrNull() shouldBe DomainError.Invalid("value", DomainError.Reason.NOT_FINITE)
        }
        checkAll(propertyIterations(2000), anyDouble, anyDouble) { dose, c ->
            val concentration = Concentration(c, ConcentrationUnit.MG_PER_ML)
            Units.mgToMl(dose, concentration).getOrNull()?.isFinite()?.shouldBeTrue()
            Units.mlToMg(dose, concentration).getOrNull()?.isFinite()?.shouldBeTrue()
        }
    }

    test("overflowing conversions fail instead of returning Infinity") {
        Units.convert(Double.MAX_VALUE, MeasurementUnit.KG, MeasurementUnit.LB).errorOrNull() shouldBe
            DomainError.Invalid("value", DomainError.Reason.NOT_FINITE)
        Units.mlToMg(Double.MAX_VALUE, Concentration(10.0, ConcentrationUnit.MG_PER_ML)).errorOrNull() shouldBe
            DomainError.Invalid("value", DomainError.Reason.NOT_FINITE)
    }

    test("incompatible units are rejected") {
        Units.convert(1.0, MeasurementUnit.KG, MeasurementUnit.CM).errorOrNull() shouldBe
            DomainError.Invalid("unit", DomainError.Reason.OUT_OF_RANGE)
        Units.convert(1.0, MeasurementUnit.PERCENT, MeasurementUnit.KG).errorOrNull() shouldBe
            DomainError.Invalid("unit", DomainError.Reason.OUT_OF_RANGE)
    }

    test("same unit is identity but still rejects non-finite values") {
        Units.convert(3.0, MeasurementUnit.KG, MeasurementUnit.KG).getOrNull() shouldBe 3.0
        Units.convert(Double.NaN, MeasurementUnit.KG, MeasurementUnit.KG).errorOrNull() shouldBe
            DomainError.Invalid("value", DomainError.Reason.NOT_FINITE)
    }

    test("mg and mL conversion needs a finite, positive mg/mL concentration") {
        Units.mgToMl(1.0, Concentration(10.0, ConcentrationUnit.MG_PER_G)).errorOrNull() shouldBe
            DomainError.Invalid("concentration", DomainError.Reason.OUT_OF_RANGE)
        Units.mgToMl(1.0, Concentration(0.0, ConcentrationUnit.MG_PER_ML)).errorOrNull() shouldBe
            DomainError.Invalid("concentration", DomainError.Reason.NOT_POSITIVE)
        Units.mlToMg(1.0, Concentration(-1.0, ConcentrationUnit.MG_PER_ML)).errorOrNull() shouldBe
            DomainError.Invalid("concentration", DomainError.Reason.NOT_POSITIVE)
        Units.mgToMl(1.0, Concentration(Double.NaN, ConcentrationUnit.MG_PER_ML)).errorOrNull() shouldBe
            DomainError.Invalid("concentration", DomainError.Reason.NOT_FINITE)
        Units.mgToMl(1.0, Concentration(Double.POSITIVE_INFINITY, ConcentrationUnit.MG_PER_ML)).errorOrNull() shouldBe
            DomainError.Invalid("concentration", DomainError.Reason.NOT_FINITE)
        Units.mgToMl(1e-300, Concentration(1e-300, ConcentrationUnit.MG_PER_ML)).getOrNull() shouldBe (1.0 plusOrMinus 1e-9)
    }
})
