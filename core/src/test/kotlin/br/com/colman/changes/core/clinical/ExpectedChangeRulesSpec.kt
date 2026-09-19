// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.clinical

import br.com.colman.changes.core.clinical.ExpectedChangeStatus.NOT_YET_EXPECTED
import br.com.colman.changes.core.clinical.ExpectedChangeStatus.PAST_ONSET_WINDOW
import br.com.colman.changes.core.clinical.ExpectedChangeStatus.WITHIN_ONSET_WINDOW
import br.com.colman.changes.core.model.TreatmentProtocol
import br.com.colman.changes.core.testing.propertyIterations
import br.com.colman.changes.core.testing.testDataset
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

class ExpectedChangeRulesSpec : BehaviorSpec({
    fun change(onsetMin: Double, onsetMax: Double?) = ExpectedChange(
        changeTypeCode = "X",
        protocol = TreatmentProtocol.MASCULINIZING,
        onsetMonthsMin = onsetMin,
        onsetMonthsMax = onsetMax,
        maxEffectMonthsMin = null,
        maxEffectMonthsMax = null,
        permanence = Permanence.NOT_STATED,
        permanenceSource = null,
        source = SourceKey.ENDO_2017,
    )

    Given("a change with onset between 1 and 6 months") {
        val acne = change(1.0, 6.0)

        Then("status flips exactly at the window limits, both inclusive") {
            ExpectedChangeRules.status(acne, -3.0) shouldBe NOT_YET_EXPECTED
            ExpectedChangeRules.status(acne, 0.0) shouldBe NOT_YET_EXPECTED
            ExpectedChangeRules.status(acne, 0.9999) shouldBe NOT_YET_EXPECTED
            ExpectedChangeRules.status(acne, 1.0) shouldBe WITHIN_ONSET_WINDOW
            ExpectedChangeRules.status(acne, 6.0) shouldBe WITHIN_ONSET_WINDOW
            ExpectedChangeRules.status(acne, 6.0001) shouldBe PAST_ONSET_WINDOW
        }
    }

    Given("a change whose source gives no upper onset limit") {
        Then("it stays within the window forever once started") {
            ExpectedChangeRules.status(change(12.0, null), 11.99) shouldBe NOT_YET_EXPECTED
            ExpectedChangeRules.status(change(12.0, null), 12.0) shouldBe WITHIN_ONSET_WINDOW
            ExpectedChangeRules.status(change(12.0, null), 1000.0) shouldBe WITHIN_ONSET_WINDOW
            ExpectedChangeRules.onsetEnd(change(12.0, null), LocalDate(2026, 1, 1)).shouldBeNull()
        }
    }

    Given("month arithmetic") {
        Then("months are counted with the mean Gregorian month") {
            ExpectedChangeRules.DAYS_PER_MONTH shouldBe (30.436875 plusOrMinus 1e-12)
            ExpectedChangeRules.monthsBetween(LocalDate(2026, 1, 1), LocalDate(2026, 1, 1)) shouldBe 0.0
            ExpectedChangeRules.monthsBetween(LocalDate(2025, 1, 1), LocalDate(2026, 1, 1)) shouldBe (11.99 plusOrMinus 0.01)
            ExpectedChangeRules.monthsBetween(LocalDate(2026, 1, 1), LocalDate(2025, 12, 1)) shouldBe (-1.0 plusOrMinus 0.02)
        }

        Then("onset dates for a start on 29 February") {
            val leap = LocalDate(2024, 2, 29)
            ExpectedChangeRules.onsetStart(change(1.0, 6.0), leap) shouldBe LocalDate(2024, 3, 31)
            ExpectedChangeRules.onsetEnd(change(1.0, 6.0), leap) shouldBe LocalDate(2024, 8, 29)
        }
    }

    Given("any dataset row and any treatment start date") {
        val starts = Arb.long(
            LocalDate(2000, 1, 1).toEpochDays(),
            LocalDate(2040, 12, 31).toEpochDays()
        ).map { LocalDate.fromEpochDays(it) }
        val rows = Arb.element(testDataset.expectedChanges)

        Then("the computed onset dates agree with the status rule to the day") {
            checkAll(propertyIterations(1000), rows, starts) { row, start ->
                val first = ExpectedChangeRules.onsetStart(row, start)
                ExpectedChangeRules.status(row, ExpectedChangeRules.monthsBetween(start, first)) shouldBe WITHIN_ONSET_WINDOW
                val dayBefore = first.minus(1, DateTimeUnit.DAY)
                ExpectedChangeRules.status(row, ExpectedChangeRules.monthsBetween(start, dayBefore)) shouldBe NOT_YET_EXPECTED
                val last = ExpectedChangeRules.onsetEnd(row, start)
                if (last != null) {
                    ExpectedChangeRules.status(row, ExpectedChangeRules.monthsBetween(start, last)) shouldBe WITHIN_ONSET_WINDOW
                    val dayAfter = last.plus(1, DateTimeUnit.DAY)
                    ExpectedChangeRules.status(row, ExpectedChangeRules.monthsBetween(start, dayAfter)) shouldBe PAST_ONSET_WINDOW
                }
            }
        }
    }

    Given("the timeline") {
        val changes = listOf(change(1.0, 6.0).copy(changeTypeCode = "A"), change(6.0, 12.0).copy(changeTypeCode = "B"))
        val observed = mapOf("A" to LocalDate(2026, 3, 1))

        When("there is no treatment start date (criterion 7.2.1)") {
            val items = ExpectedChangeRules.timeline(changes, null, LocalDate(2026, 9, 12), observed)

            Then("ranges are listed without relative state and observations are kept") {
                items.map { it.change } shouldContainExactly changes
                items.map { it.status } shouldContainExactly listOf(null, null)
                items.map { it.onsetStart } shouldContainExactly listOf(null, null)
                items.map { it.onsetEnd } shouldContainExactly listOf(null, null)
                items.map { it.firstObserved } shouldContainExactly listOf(LocalDate(2026, 3, 1), null)
                items.map { it.observedAtMonths } shouldContainExactly listOf(null, null)
            }
        }

        When("the treatment started on 2026-01-01") {
            val start = LocalDate(2026, 1, 1)
            val items = ExpectedChangeRules.timeline(changes, start, LocalDate(2026, 4, 1), observed)

            Then("each item carries its state, its window and the person's own observation") {
                items.map { it.status } shouldContainExactly listOf(WITHIN_ONSET_WINDOW, NOT_YET_EXPECTED)
                items[0].onsetStart shouldBe ExpectedChangeRules.onsetStart(changes[0], start)
                items[0].onsetEnd shouldBe ExpectedChangeRules.onsetEnd(changes[0], start)
                items[0].observedAtMonths shouldBe ExpectedChangeRules.monthsBetween(start, LocalDate(2026, 3, 1))
                items[1].observedAtMonths.shouldBeNull()
                items[1].firstObserved.shouldBeNull()
            }
        }
    }
})
