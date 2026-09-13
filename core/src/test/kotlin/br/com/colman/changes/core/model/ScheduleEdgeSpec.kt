// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.model

import br.com.colman.changes.core.model.InjectionSite.GLUTE_LEFT
import br.com.colman.changes.core.model.InjectionSite.GLUTE_RIGHT
import br.com.colman.changes.core.model.InjectionSite.THIGH_LEFT
import br.com.colman.changes.core.model.InjectionSite.THIGH_RIGHT
import br.com.colman.changes.core.model.RecurrenceRule.Frequency
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate

/** Casos que separam regras parecidas: limites exatos de série e "menos usado" contra "mais antigo". */
class ScheduleEdgeSpec : FunSpec({
    fun d(text: String) = LocalDate.parse(text)

    test("the least used site wins even when it was used more recently") {
        val recent = listOf(GLUTE_LEFT, GLUTE_RIGHT, GLUTE_RIGHT, THIGH_LEFT, THIGH_LEFT, THIGH_RIGHT, THIGH_RIGHT)
        InjectionSiteRotation.suggest(recent, listOf(GLUTE_RIGHT, GLUTE_LEFT)) shouldBe GLUTE_LEFT
        InjectionSiteRotation.suggest(recent, listOf(GLUTE_LEFT, GLUTE_RIGHT)) shouldBe GLUTE_LEFT
    }

    test("between equally used sites, the one used longest ago wins regardless of candidate order") {
        val recent = listOf(GLUTE_LEFT, GLUTE_RIGHT)
        InjectionSiteRotation.suggest(recent, listOf(GLUTE_LEFT, GLUTE_RIGHT)) shouldBe GLUTE_RIGHT
        InjectionSiteRotation.suggest(recent, listOf(GLUTE_RIGHT, GLUTE_LEFT)) shouldBe GLUTE_RIGHT
    }

    test("between never used sites, the candidate order decides") {
        InjectionSiteRotation.suggest(listOf(GLUTE_LEFT), listOf(THIGH_RIGHT, THIGH_LEFT)) shouldBe THIGH_RIGHT
    }

    test("monthly series include a limit that falls exactly on the first of the month") {
        RecurrenceRule(Frequency.MONTHLY).occurrences(d("2026-01-01"), d("2026-01-01"), d("2026-03-01")) shouldContainExactly
            listOf(d("2026-01-01"), d("2026-02-01"), d("2026-03-01"))
    }

    test("yearly series include a limit on 1 January and stop before an anniversary after the limit") {
        RecurrenceRule(Frequency.YEARLY).occurrences(d("2026-01-01"), d("2026-01-01"), d("2028-01-01")) shouldContainExactly
            listOf(d("2026-01-01"), d("2027-01-01"), d("2028-01-01"))
        RecurrenceRule(Frequency.YEARLY).occurrences(d("2026-06-01"), d("2026-01-01"), d("2027-03-01")) shouldContainExactly
            listOf(d("2026-06-01"))
    }

    test("until before the window end caps the series; until after it does not") {
        val rule = RecurrenceRule(Frequency.DAILY, until = d("2026-01-02"))
        rule.occurrences(d("2026-01-01"), d("2026-01-01"), d("2026-01-05")) shouldContainExactly listOf(d("2026-01-01"), d("2026-01-02"))
        rule.occurrences(d("2026-01-01"), d("2026-01-01"), d("2026-01-01")) shouldContainExactly listOf(d("2026-01-01"))
        rule.occurrences(d("2026-01-03"), d("2026-01-01"), d("2026-01-05")).shouldBeEmpty()
    }

    test("weekly BYDAY with a window ending before the start is empty") {
        RecurrenceRule(Frequency.WEEKLY, byDay = setOf(kotlinx.datetime.DayOfWeek.MONDAY))
            .occurrences(d("2026-01-07"), d("2026-01-05"), d("2026-01-06")).shouldBeEmpty()
    }
})
