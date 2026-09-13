// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.model

import br.com.colman.changes.core.model.RecurrenceRule.Frequency
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

class RecurrenceRuleSpec : FunSpec({
    fun d(text: String) = LocalDate.parse(text)

    fun rule(text: String) = RecurrenceRule.parse(text).getOrNull()!!

    context("parse and format") {
        test("canonical text round-trips") {
            val text = "FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,TH;UNTIL=20261231"
            rule(text).format() shouldBe text
            rule(text) shouldBe RecurrenceRule(Frequency.WEEKLY, 2, setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), d("2026-12-31"))
        }

        test("defaults are omitted and COUNT is written") {
            RecurrenceRule(Frequency.DAILY).format() shouldBe "FREQ=DAILY"
            RecurrenceRule(Frequency.MONTHLY, count = 5).format() shouldBe "FREQ=MONTHLY;COUNT=5"
            RecurrenceRule(Frequency.YEARLY, interval = 2).format() shouldBe "FREQ=YEARLY;INTERVAL=2"
        }

        test("BYDAY is written in ISO order regardless of input order") {
            RecurrenceRule(Frequency.WEEKLY, byDay = setOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)).format() shouldBe
                "FREQ=WEEKLY;BYDAY=MO,WE,SU"
        }

        test("keys are case-insensitive, whitespace tolerated, date-times truncated to the date") {
            rule(" freq=daily ; interval=3 ") shouldBe RecurrenceRule(Frequency.DAILY, 3)
            rule("FREQ=DAILY;UNTIL=20261231T235959Z").until shouldBe d("2026-12-31")
            rule("FREQ=WEEKLY;BYDAY=SU,SA").byDay shouldBe setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
            rule("FREQ=DAILY;INTERVAL=999").interval shouldBe 999
            rule("FREQ=DAILY;COUNT=9999").count shouldBe 9999
            rule("FREQ=DAILY;COUNT=1").count shouldBe 1
            rule("UNTIL=20000101;FREQ=YEARLY").until shouldBe d("2000-01-01")
        }

        withData(
            nameFn = { "rejects '$it'" },
            "",
            "FREQ",
            "FREQ=HOURLY",
            "INTERVAL=2",
            "FREQ=DAILY;INTERVAL=0",
            "FREQ=DAILY;INTERVAL=1000",
            "FREQ=DAILY;INTERVAL=x",
            "FREQ=DAILY;BYDAY=MO",
            "FREQ=MONTHLY;BYDAY=MO",
            "FREQ=WEEKLY;BYDAY=XX",
            "FREQ=WEEKLY;BYDAY=MO,XX",
            "FREQ=WEEKLY;BYDAY=",
            "FREQ=WEEKLY;COUNT=3;UNTIL=20260101",
            "FREQ=DAILY;FOO=1",
            "FREQ=DAILY;FREQ=WEEKLY",
            "FREQ=DAILY;UNTIL=2026",
            "FREQ=DAILY;UNTIL=2026013X",
            "FREQ=DAILY;UNTIL=20260230",
            "FREQ=DAILY;COUNT=0",
            "FREQ=DAILY;COUNT=10000",
            "FREQ=DAILY;COUNT=many",
        ) { text ->
            RecurrenceRule.parse(text).errorOrNull() shouldBe DomainError.Invalid("recurrenceRule", DomainError.Reason.OUT_OF_RANGE)
        }

        test("validated rejects rules the subset does not support") {
            RecurrenceRule.validated(RecurrenceRule(Frequency.DAILY, interval = 0)).isSuccess shouldBe false
            RecurrenceRule.validated(RecurrenceRule(Frequency.DAILY, count = 2, until = d("2026-01-01"))).isSuccess shouldBe false
            RecurrenceRule.validated(RecurrenceRule(Frequency.YEARLY, byDay = setOf(DayOfWeek.MONDAY))).isSuccess shouldBe false
            RecurrenceRule.validated(RecurrenceRule(Frequency.WEEKLY, byDay = setOf(DayOfWeek.MONDAY))).isSuccess shouldBe true
        }
    }

    context("occurrences") {
        test("daily with interval") {
            RecurrenceRule(Frequency.DAILY, interval = 3).occurrences(d("2026-01-01"), d("2026-01-05"), d("2026-01-14")) shouldContainExactly
                listOf(d("2026-01-07"), d("2026-01-10"), d("2026-01-13"))
        }

        test("the window bounds are inclusive") {
            RecurrenceRule(Frequency.DAILY, interval = 2).occurrences(d("2026-01-01"), d("2026-01-03"), d("2026-01-07")) shouldContainExactly
                listOf(d("2026-01-03"), d("2026-01-05"), d("2026-01-07"))
        }

        test("weekly without BYDAY repeats the start weekday") {
            RecurrenceRule(Frequency.WEEKLY, interval = 2).occurrences(d("2026-01-07"), d("2026-01-01"), d("2026-02-28")) shouldContainExactly
                listOf(d("2026-01-07"), d("2026-01-21"), d("2026-02-04"), d("2026-02-18"))
        }

        test("weekly BYDAY starting mid-week skips days before the start") {
            // 2026-01-07 é quarta-feira.
            RecurrenceRule(Frequency.WEEKLY, byDay = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))
                .occurrences(d("2026-01-07"), d("2026-01-01"), d("2026-01-19")) shouldContainExactly
                listOf(d("2026-01-08"), d("2026-01-12"), d("2026-01-15"), d("2026-01-19"))
        }

        test("weekly BYDAY every other week") {
            RecurrenceRule(Frequency.WEEKLY, interval = 2, byDay = setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
                .occurrences(d("2026-01-05"), d("2026-01-01"), d("2026-02-01")) shouldContainExactly
                listOf(d("2026-01-05"), d("2026-01-09"), d("2026-01-19"), d("2026-01-23"))
        }

        test("weekly BYDAY stops exactly at the limit inside a week") {
            RecurrenceRule(Frequency.WEEKLY, byDay = setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY))
                .occurrences(d("2026-01-05"), d("2026-01-05"), d("2026-01-12")) shouldContainExactly
                listOf(d("2026-01-05"), d("2026-01-11"), d("2026-01-12"))
        }

        test("monthly on the 31st skips shorter months") {
            RecurrenceRule(Frequency.MONTHLY).occurrences(d("2026-01-31"), d("2026-01-01"), d("2026-08-31")) shouldContainExactly
                listOf(d("2026-01-31"), d("2026-03-31"), d("2026-05-31"), d("2026-07-31"), d("2026-08-31"))
        }

        test("monthly with interval crosses year boundaries") {
            RecurrenceRule(Frequency.MONTHLY, interval = 3).occurrences(d("2025-11-15"), d("2025-01-01"), d("2026-12-31")) shouldContainExactly
                listOf(d("2025-11-15"), d("2026-02-15"), d("2026-05-15"), d("2026-08-15"), d("2026-11-15"))
        }

        test("monthly stops at the month containing the limit") {
            RecurrenceRule(Frequency.MONTHLY).occurrences(d("2026-01-10"), d("2026-01-01"), d("2026-03-09")) shouldContainExactly
                listOf(d("2026-01-10"), d("2026-02-10"))
        }

        test("yearly on 29 February only lands on leap years") {
            RecurrenceRule(Frequency.YEARLY).occurrences(d("2024-02-29"), d("2024-01-01"), d("2032-12-31")) shouldContainExactly
                listOf(d("2024-02-29"), d("2028-02-29"), d("2032-02-29"))
        }

        test("yearly with interval") {
            RecurrenceRule(Frequency.YEARLY, interval = 2).occurrences(d("2026-03-01"), d("2026-01-01"), d("2031-01-01")) shouldContainExactly
                listOf(d("2026-03-01"), d("2028-03-01"), d("2030-03-01"))
        }

        test("COUNT counts from the start, not from the window") {
            RecurrenceRule(Frequency.DAILY, count = 3).occurrences(d("2026-01-01"), d("2026-01-02"), d("2026-01-31")) shouldContainExactly
                listOf(d("2026-01-02"), d("2026-01-03"))
            RecurrenceRule(Frequency.DAILY, count = 1).occurrences(d("2026-01-01"), d("2026-01-01"), d("2026-01-31")) shouldContainExactly
                listOf(d("2026-01-01"))
        }

        test("UNTIL is inclusive and also caps the window") {
            val untilJan3 = RecurrenceRule(Frequency.DAILY, until = d("2026-01-03"))
            untilJan3.occurrences(d("2026-01-01"), d("2026-01-01"), d("2026-01-31")) shouldContainExactly
                listOf(d("2026-01-01"), d("2026-01-02"), d("2026-01-03"))
            val untilDec31 = RecurrenceRule(Frequency.DAILY, until = d("2026-12-31"))
            untilDec31.occurrences(d("2026-01-01"), d("2026-01-30"), d("2026-01-31")) shouldContainExactly
                listOf(d("2026-01-30"), d("2026-01-31"))
        }

        test("empty when the window ends before the start or before itself begins") {
            RecurrenceRule(
                Frequency.DAILY
            ).occurrences(d("2026-02-01"), d("2026-01-01"), d("2026-01-31")).shouldBeEmpty()
            RecurrenceRule(
                Frequency.DAILY
            ).occurrences(d("2026-01-01"), d("2026-01-10"), d("2026-01-09")).shouldBeEmpty()
            RecurrenceRule(
                Frequency.DAILY,
                until = d("2025-12-31")
            ).occurrences(d("2026-01-01"), d("2026-01-01"), d("2026-01-31")).shouldBeEmpty()
        }

        test("a window of a single day on the start returns the start") {
            RecurrenceRule(Frequency.DAILY, interval = 5).occurrences(d("2026-01-01"), d("2026-01-01"), d("2026-01-01")) shouldContainExactly
                listOf(d("2026-01-01"))
        }
    }
})
