// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.model

import br.com.colman.changes.core.testing.TestZones
import br.com.colman.changes.core.testing.propertyIterations
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.set
import io.kotest.property.checkAll
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.Uuid

class DoseScheduleSpec : BehaviorSpec({
    fun d(text: String) = LocalDate.parse(text)

    val dates = Arb.long(LocalDate(2000, 1, 1).toEpochDays(), LocalDate(2040, 12, 31).toEpochDays()).map {
        LocalDate.fromEpochDays(it)
    }
    val zones = Arb.element(TestZones.ALL)
    val times = Arb.int(0, 1439).map { LocalTime(it / 60, it % 60) }

    fun regimen(
        schedule: Schedule,
        start: LocalDate,
        end: LocalDate? = null,
        time: LocalTime? = LocalTime(8, 0),
    ) = Regimen(
        id = Uuid.random(),
        medicationId = Uuid.random(),
        dose = Dose(100.0, DoseUnit.MG),
        route = Route.INTRAMUSCULAR,
        schedule = schedule,
        timeOfDay = time,
        startDate = start,
        endDate = end,
        isActive = true,
        notes = null,
    )

    Given("criterion 7.1.1: INTERVAL_DAYS(14) started on D, across the 2018 Brazilian DST start") {
        val zone = TestZones.SAO_PAULO
        val start = d("2018-10-21")
        val occurrences = DoseSchedule.occurrences(regimen(Schedule.IntervalDays(14), start), start, d("2018-12-02"))

        Then("doses fall on D, D+14, D+28 and D+42") {
            occurrences shouldContainExactly listOf(d("2018-10-21"), d("2018-11-04"), d("2018-11-18"), d("2018-12-02"))
        }

        Then("each dose keeps 08:00 local time, before and after the offset changes") {
            val locals = occurrences.map { DoseSchedule.instantOf(it, LocalTime(8, 0), zone)!!.toLocalDateTime(zone) }
            locals.map { it.date } shouldContainExactly occurrences
            locals.map { it.time }.distinct() shouldContainExactly listOf(LocalTime(8, 0))
        }

        Then("a time inside the DST gap is pushed forward within the same day") {
            val local = DoseSchedule.instantOf(d("2018-11-04"), LocalTime(0, 30), zone)!!.toLocalDateTime(zone)
            local.date shouldBe d("2018-11-04")
            local.time shouldBe LocalTime(1, 30)
        }
    }

    Given("criterion 7.9.2: a regimen that already ended") {
        val ended = regimen(Schedule.IntervalDays(7), d("2026-01-01"), end = d("2026-01-29"))

        Then("no dose is planned after the end date, and the end date itself is included") {
            DoseSchedule.occurrences(ended, d("2026-01-20"), d("2026-03-31")) shouldContainExactly listOf(d("2026-01-22"), d("2026-01-29"))
            DoseSchedule.occurrences(ended, d("2026-01-30"), d("2026-03-31")).shouldBeEmpty()
            DoseSchedule.next(ended, d("2026-01-30")).shouldBeNull()
        }
    }

    Given("property 11.2.3: any INTERVAL_DAYS regimen and any window") {
        Then("occurrences are exactly the congruent local days inside the bounds, at constant spacing in any zone") {
            checkAll(
                propertyIterations(1000),
                dates,
                Arb.int(1, 60),
                Arb.int(-400, 400),
                Arb.int(0, 800),
                Arb.int(0, 1200).orNull(0.3),
            ) { start, interval, fromOffset, length, endOffset ->
                val from = start.plus(fromOffset, DateTimeUnit.DAY)
                val to = from.plus(length, DateTimeUnit.DAY)
                val end = endOffset?.let { start.plus(it, DateTimeUnit.DAY) }
                val occurrences = DoseSchedule.occurrences(Schedule.IntervalDays(interval), start, end, from, to)

                val low = maxOf(start, from).toEpochDays()
                val high = (end?.let { minOf(it, to) } ?: to).toEpochDays()
                val expected = (low..high)
                    .filter { (it - start.toEpochDays()) % interval == 0L }
                    .map { LocalDate.fromEpochDays(it) }
                occurrences shouldContainExactly expected
                occurrences.zipWithNext().forEach { (a, b) -> (b.toEpochDays() - a.toEpochDays()) shouldBe interval.toLong() }
            }
        }

        Then("the planned instant always falls on the planned local day, for every zone and time") {
            checkAll(propertyIterations(1000), dates, times, zones) { date, time, zone ->
                DoseSchedule.instantOf(date, time, zone)!!.toLocalDateTime(zone).date shouldBe date
            }
        }
    }

    Given("a twice-weekly regimen (open question 1, ADR 0007)") {
        val twice = Schedule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY))

        Then("doses fall only on the chosen weekdays") {
            checkAll(
                propertyIterations(300),
                dates,
                Arb.set(Arb.element(DayOfWeek.entries), 1..7),
                Arb.int(1, 4)
            ) { start, days, every ->
                val result = DoseSchedule.occurrences(
                    Schedule.Weekly(days, every),
                    start,
                    null,
                    start,
                    start.plus(120, DateTimeUnit.DAY)
                )
                result.all { it.dayOfWeek in days } shouldBe true
                result.zipWithNext().all { (a, b) -> a < b } shouldBe true
            }
        }

        Then("concrete example") {
            DoseSchedule.occurrences(twice, d("2026-09-14"), null, d("2026-09-14"), d("2026-09-27")) shouldContainExactly
                listOf(d("2026-09-14"), d("2026-09-17"), d("2026-09-21"), d("2026-09-24"))
        }
    }

    Given("a custom rule and an as-needed regimen") {
        Then("custom uses the recurrence rule") {
            val quarterly = Schedule.Custom(RecurrenceRule(RecurrenceRule.Frequency.MONTHLY, interval = 3))
            DoseSchedule.occurrences(quarterly, d("2026-01-10"), null, d("2026-01-01"), d("2026-12-31")) shouldContainExactly
                listOf(d("2026-01-10"), d("2026-04-10"), d("2026-07-10"), d("2026-10-10"))
        }

        Then("as-needed never plans a dose") {
            DoseSchedule.occurrences(
                Schedule.AsNeeded,
                d("2026-01-01"),
                null,
                d("2026-01-01"),
                d("2026-12-31")
            ).shouldBeEmpty()
            DoseSchedule.next(regimen(Schedule.AsNeeded, d("2026-01-01")), d("2026-01-01")).shouldBeNull()
        }
    }

    Given("next dose and planned instants") {
        val biweekly = regimen(Schedule.IntervalDays(14), d("2026-09-01"))

        Then("next returns the first planned day on or after the given day") {
            DoseSchedule.next(biweekly, d("2026-09-01")) shouldBe d("2026-09-01")
            DoseSchedule.next(biweekly, d("2026-09-02")) shouldBe d("2026-09-15")
            DoseSchedule.next(biweekly, d("2026-09-02"), horizonDays = 12).shouldBeNull()
            DoseSchedule.next(biweekly, d("2026-09-02"), horizonDays = 13) shouldBe d("2026-09-15")
        }

        Then("without a time of day there is no planned instant") {
            DoseSchedule.instantOf(d("2026-09-01"), null, TestZones.UTC).shouldBeNull()
        }
    }

    Given("varying intervals: first dose in 45 days, the second 90 days later, then every 90 days") {
        val loading = Schedule.Stepped(listOf(45, 90), thenEvery = 90)
        val start = d("2026-09-13")

        Then("doses fall on D+45, D+135 and then every 90 days") {
            DoseSchedule.occurrences(loading, start, null, start, d("2027-07-24")) shouldContainExactly
                listOf(d("2026-10-28"), d("2027-01-26"), d("2027-04-26"))
            DoseSchedule.occurrences(loading, start, null, start, d("2027-07-25")).last() shouldBe d("2027-07-25")
        }

        Then("a first step of 0 is a dose on the start date itself") {
            DoseSchedule.occurrences(Schedule.Stepped(listOf(0, 42), 84), start, null, start, d("2027-01-16")) shouldContainExactly
                listOf(start, d("2026-10-25"))
        }

        Then("without continuous use the series ends after the last step") {
            DoseSchedule.occurrences(Schedule.Stepped(listOf(45, 90)), start, null, start, d("2030-01-01")) shouldContainExactly
                listOf(d("2026-10-28"), d("2027-01-26"))
        }

        Then("a later window jumps straight to the continuous doses inside it, and the end date is inclusive") {
            DoseSchedule.occurrences(loading, start, d("2027-10-23"), d("2027-04-27"), d("2028-12-31")) shouldContainExactly
                listOf(d("2027-07-25"), d("2027-10-23"))
            DoseSchedule.occurrences(loading, start, null, d("2027-04-26"), d("2027-04-26")) shouldContainExactly
                listOf(d("2027-04-26"))
            DoseSchedule.occurrences(loading, start, null, d("2027-04-27"), d("2027-07-24")).shouldBeEmpty()
            DoseSchedule.next(regimen(loading, start), d("2026-10-29")) shouldBe d("2027-01-26")
        }

        Then("occurrences match a dose-by-dose expansion for any steps, continuation and window") {
            checkAll(
                propertyIterations(500),
                dates,
                Arb.int(0, 120),
                Arb.list(Arb.int(1, 120), 0..5),
                Arb.int(1, 120).orNull(0.3),
                Arb.int(-200, 900),
                Arb.int(0, 900),
            ) { start, first, rest, every, fromOffset, length ->
                val steps = listOf(first) + rest
                val from = start.plus(fromOffset, DateTimeUnit.DAY)
                val to = from.plus(length, DateTimeUnit.DAY)
                val expanded = mutableListOf<LocalDate>()
                var day = start
                for (step in steps) {
                    day = day.plus(step, DateTimeUnit.DAY)
                    expanded += day
                }
                while (every != null && day <= to) {
                    day = day.plus(every, DateTimeUnit.DAY)
                    expanded += day
                }
                DoseSchedule.occurrences(Schedule.Stepped(steps, every), start, null, from, to) shouldContainExactly
                    expanded.filter { it in from..to }
            }
        }
    }

    Given("schedule validation") {
        Then("varying intervals take 1 to 12 steps, the first 0..366 days, the rest and the continuation 1..366") {
            fun valid(
                steps: List<Int>,
                every: Int? = null
            ) = DoseSchedule.validate(Schedule.Stepped(steps, every)).isSuccess
            valid(emptyList()) shouldBe false
            valid(listOf(-1)) shouldBe false
            valid(listOf(0)) shouldBe true
            valid(listOf(366)) shouldBe true
            valid(listOf(367)) shouldBe false
            valid(listOf(45, 0)) shouldBe false
            valid(listOf(45, 1)) shouldBe true
            valid(listOf(45, 366)) shouldBe true
            valid(listOf(45, 367)) shouldBe false
            valid(List(12) { 30 }) shouldBe true
            valid(List(13) { 30 }) shouldBe false
            valid(listOf(45), 0) shouldBe false
            valid(listOf(45), 1) shouldBe true
            valid(listOf(45), 366) shouldBe true
            valid(listOf(45), 367) shouldBe false
            DoseSchedule.validate(Schedule.Stepped(listOf(45), 0)).errorOrNull() shouldBe
                DomainError.Invalid("schedule", DomainError.Reason.OUT_OF_RANGE)
        }

        Then("interval must be between 1 and 366 days") {
            DoseSchedule.validate(Schedule.IntervalDays(0)).isSuccess shouldBe false
            DoseSchedule.validate(Schedule.IntervalDays(1)).isSuccess shouldBe true
            DoseSchedule.validate(Schedule.IntervalDays(366)).isSuccess shouldBe true
            DoseSchedule.validate(Schedule.IntervalDays(367)).errorOrNull() shouldBe
                DomainError.Invalid("schedule", DomainError.Reason.OUT_OF_RANGE)
        }

        Then("weekly needs at least one day and 1..52 weeks") {
            DoseSchedule.validate(Schedule.Weekly(emptySet())).isSuccess shouldBe false
            DoseSchedule.validate(Schedule.Weekly(setOf(DayOfWeek.MONDAY), 0)).isSuccess shouldBe false
            DoseSchedule.validate(Schedule.Weekly(setOf(DayOfWeek.MONDAY), 1)).isSuccess shouldBe true
            DoseSchedule.validate(Schedule.Weekly(setOf(DayOfWeek.MONDAY), 52)).isSuccess shouldBe true
            DoseSchedule.validate(Schedule.Weekly(setOf(DayOfWeek.MONDAY), 53)).isSuccess shouldBe false
        }

        Then("custom rules must be valid and as-needed is always valid") {
            DoseSchedule.validate(Schedule.Custom(RecurrenceRule(RecurrenceRule.Frequency.DAILY, interval = 0))).isSuccess shouldBe false
            DoseSchedule.validate(Schedule.Custom(RecurrenceRule(RecurrenceRule.Frequency.DAILY))).isSuccess shouldBe true
            DoseSchedule.validate(Schedule.AsNeeded).getOrNull() shouldBe Schedule.AsNeeded
        }

        Then("schedule types are reported") {
            Schedule.IntervalDays(1).type shouldBe ScheduleType.INTERVAL_DAYS
            Schedule.Weekly(setOf(DayOfWeek.MONDAY)).type shouldBe ScheduleType.WEEKLY
            Schedule.AsNeeded.type shouldBe ScheduleType.AS_NEEDED
            Schedule.Custom(RecurrenceRule(RecurrenceRule.Frequency.DAILY)).type shouldBe ScheduleType.CUSTOM_CRON
            Schedule.Stepped(listOf(45)).type shouldBe ScheduleType.STEPPED
            DayOfWeek.MONDAY.isoDayNumber shouldBe 1
        }
    }
})
