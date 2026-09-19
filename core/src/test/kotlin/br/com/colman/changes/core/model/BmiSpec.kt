// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.model

import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.propertyIterations
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.numericDouble
import io.kotest.property.checkAll
import kotlinx.datetime.TimeZone
import kotlin.uuid.Uuid

/** Seção 11.2, item 5. */
class BmiSpec : FunSpec({
    val weights = Arb.numericDouble(1.0, 500.0)
    val heights = Arb.numericDouble(30.0, 250.0)

    test("known value") {
        Bmi.calculate(70.0, 175.0).getOrNull() shouldBe (22.857142857 plusOrMinus 1e-6)
    }

    test("BMI is strictly increasing in weight for a fixed height") {
        checkAll(propertyIterations(1000), weights, weights, heights) { a, b, height ->
            if (a != b) {
                val (low, high) = if (a < b) a to b else b to a
                val bmiLow = Bmi.calculate(low, height).getOrNull().shouldNotBeNull()
                val bmiHigh = Bmi.calculate(high, height).getOrNull().shouldNotBeNull()
                bmiLow shouldBeLessThan bmiHigh
            }
        }
    }

    test("zero or negative height is a failure, never an exception") {
        checkAll(propertyIterations(1000), weights, Arb.double(max = 0.0)) { weight, height ->
            val error = Bmi.calculate(weight, height).errorOrNull()
            if (height.isFinite()) {
                error shouldBe DomainError.Invalid("heightCm", DomainError.Reason.NOT_POSITIVE)
            } else {
                error shouldBe DomainError.Invalid("heightCm", DomainError.Reason.NOT_FINITE)
            }
        }
    }

    test("zero or negative weight is a failure") {
        Bmi.calculate(0.0, 170.0).errorOrNull() shouldBe DomainError.Invalid("weightKg", DomainError.Reason.NOT_POSITIVE)
        Bmi.calculate(-1.0, 170.0).errorOrNull() shouldBe DomainError.Invalid("weightKg", DomainError.Reason.NOT_POSITIVE)
        Bmi.calculate(Double.MIN_VALUE, 170.0).getOrNull().shouldNotBeNull()
        Bmi.calculate(70.0, Double.MIN_VALUE).errorOrNull() shouldBe DomainError.Invalid("bmi", DomainError.Reason.NOT_FINITE)
    }

    test("non-finite inputs are failures") {
        Bmi.calculate(Double.NaN, 170.0).errorOrNull() shouldBe DomainError.Invalid("weightKg", DomainError.Reason.NOT_FINITE)
        Bmi.calculate(Double.POSITIVE_INFINITY, 170.0).errorOrNull() shouldBe DomainError.Invalid("weightKg", DomainError.Reason.NOT_FINITE)
        Bmi.calculate(70.0, Double.NaN).errorOrNull() shouldBe DomainError.Invalid("heightCm", DomainError.Reason.NOT_FINITE)
    }

    test("weight measurements convert to kg; other types are rejected") {
        val at = RecordedTime.of(FixedClock.DEFAULT_NOW, TimeZone.UTC)
        val pounds = Measurement(Uuid.random(), MeasurementType.WEIGHT, null, 154.0, MeasurementUnit.LB, at, null)
        Bmi.weightInKg(pounds).getOrNull() shouldBe (69.85322498 plusOrMinus 1e-6)
        val waist = pounds.copy(type = MeasurementType.WAIST, unit = MeasurementUnit.CM)
        Bmi.weightInKg(waist).errorOrNull() shouldBe DomainError.Invalid("type", DomainError.Reason.OUT_OF_RANGE)
    }
})
