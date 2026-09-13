// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.model

import br.com.colman.changes.core.testing.TestZones
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Seção 7.9 e ADR 0010: quando cada lembrete dispara. */
class ReminderPlannerSpec : BehaviorSpec({
    val zone = TestZones.SAO_PAULO
    val today = LocalDate(2026, 9, 12)
    val now = LocalDateTime(today, LocalTime(9, 0)).toInstant(zone)
    val regimenId = Uuid.parse("5a1f0c3e-7d2b-4e8a-9f10-2b3c4d5e6f70")
    val otherRegimenId = Uuid.parse("5a1f0c3e-7d2b-4e8a-9f10-2b3c4d5e6f71")
    val eventId = Uuid.parse("6b2e1d4f-8e3c-4f9b-8a21-3c4d5e6f7081")

    fun at(date: LocalDate, time: LocalTime, timeZone: TimeZone = zone): Instant =
        LocalDateTime(date, time).toInstant(timeZone)

    fun regimen(
        schedule: Schedule = Schedule.IntervalDays(7),
        time: LocalTime? = LocalTime(8, 0),
        active: Boolean = true,
        start: LocalDate = today,
        id: Uuid = regimenId,
    ) = Regimen(
        id = id,
        medicationId = Uuid.random(),
        dose = Dose(1.0, DoseUnit.MG),
        route = Route.INTRAMUSCULAR,
        schedule = schedule,
        timeOfDay = time,
        startDate = start,
        endDate = null,
        isActive = active,
        notes = null,
    )

    fun event(
        start: Instant,
        minutes: Int? = 30,
        recurrence: RecurrenceRule? = null,
        completed: RecordedTime? = null,
        recordedIn: TimeZone = zone,
    ) = CalendarEvent(
        id = eventId,
        title = "consulta",
        description = null,
        start = RecordedTime.of(start, recordedIn),
        end = null,
        isAllDay = false,
        category = CalendarCategory.APPOINTMENT,
        sourceType = EventSourceType.MANUAL,
        sourceId = null,
        reminderMinutesBefore = minutes,
        recurrence = recurrence,
        completedAt = completed,
    )

    fun doseKey(id: Uuid, date: LocalDate) = "dose:$id:${date.toEpochDays()}"

    fun eventKey(date: LocalDate) = "event:$eventId:${date.toEpochDays()}"

    Given("regimens with a dose time") {
        Then("each future dose in the window gets a reminder at the local dose time, keyed by regimen and date") {
            val other = regimen(Schedule.IntervalDays(14), LocalTime(20, 0), id = otherRegimenId)
            ReminderPlanner.doses(listOf(regimen(), other), today, 14, zone, now) shouldBe listOf(
                PlannedReminder(doseKey(regimenId, LocalDate(2026, 9, 19)), at(LocalDate(2026, 9, 19), LocalTime(8, 0)), ReminderSource.DOSE),
                PlannedReminder(doseKey(regimenId, LocalDate(2026, 9, 26)), at(LocalDate(2026, 9, 26), LocalTime(8, 0)), ReminderSource.DOSE),
                PlannedReminder(doseKey(otherRegimenId, today), at(today, LocalTime(20, 0)), ReminderSource.DOSE),
                PlannedReminder(doseKey(otherRegimenId, LocalDate(2026, 9, 26)), at(LocalDate(2026, 9, 26), LocalTime(20, 0)), ReminderSource.DOSE),
            )
        }
        Then("the last day of the window is included") {
            ReminderPlanner.doses(listOf(regimen()), today, 7, zone, now).map {
                it.key
            } shouldBe listOf(doseKey(regimenId, LocalDate(2026, 9, 19)))
        }
        Then("a dose due exactly now is not reminded, one a minute later is") {
            ReminderPlanner.doses(listOf(regimen(time = LocalTime(9, 0))), today, 0, zone, now).shouldBeEmpty()
            ReminderPlanner.doses(listOf(regimen(time = LocalTime(9, 1))), today, 0, zone, now).map { it.at } shouldBe
                listOf(at(today, LocalTime(9, 1)))
        }
    }

    Given("regimens that never remind") {
        Then("an inactive regimen and a regimen without a dose time plan nothing") {
            ReminderPlanner.doses(
                listOf(regimen(active = false), regimen(time = null)),
                today,
                30,
                zone,
                now
            ).shouldBeEmpty()
        }
    }

    Given("a daily dose at 02:30 across the start of daylight saving time in New York") {
        Then("the local time that does not exist moves forward, the other days keep 02:30") {
            val ny = TestZones.NEW_YORK
            val start = LocalDate(2027, 3, 13)
            val daily = regimen(Schedule.IntervalDays(1), LocalTime(2, 30), start = start)
            ReminderPlanner.doses(listOf(daily), start, 2, ny, Instant.parse("2027-03-01T00:00:00Z")).map { it.at } shouldBe listOf(
                Instant.parse("2027-03-13T07:30:00Z"),
                Instant.parse("2027-03-14T07:30:00Z"),
                Instant.parse("2027-03-15T06:30:00Z"),
            )
        }
    }

    Given("a one-off event with a reminder") {
        val end = LocalDate(2026, 9, 30)
        Then("it reminds the chosen minutes before its local start time") {
            val start = at(LocalDate(2026, 9, 20), LocalTime(10, 0))
            ReminderPlanner.events(listOf(event(start)), today, end, zone, now) shouldBe listOf(
                PlannedReminder(eventKey(LocalDate(2026, 9, 20)), at(LocalDate(2026, 9, 20), LocalTime(9, 30)), ReminderSource.EVENT),
            )
        }
        Then("both ends of the window are included, and days outside it are left out") {
            fun countOn(day: LocalDate) =
                ReminderPlanner.events(
                    listOf(event(at(day, LocalTime(23, 0)))),
                    LocalDate(2026, 9, 15),
                    LocalDate(2026, 9, 20),
                    zone,
                    now
                ).size
            countOn(LocalDate(2026, 9, 14)) shouldBe 0
            countOn(LocalDate(2026, 9, 15)) shouldBe 1
            countOn(LocalDate(2026, 9, 20)) shouldBe 1
            countOn(LocalDate(2026, 9, 21)) shouldBe 0
        }
        Then("a reminder that would fire now or earlier is skipped") {
            ReminderPlanner.events(listOf(event(at(today, LocalTime(9, 30)))), today, end, zone, now).shouldBeEmpty()
            ReminderPlanner.events(listOf(event(at(today, LocalTime(9, 31)))), today, end, zone, now).map { it.at } shouldBe
                listOf(at(today, LocalTime(9, 1)))
        }
        Then("the local time recorded with the event holds after the device changes time zone") {
            val tokyo = at(LocalDate(2026, 9, 20), LocalTime(10, 0), TestZones.TOKYO)
            ReminderPlanner.events(listOf(event(tokyo, recordedIn = TestZones.TOKYO)), today, end, zone, now).map { it.at } shouldBe
                listOf(at(LocalDate(2026, 9, 20), LocalTime(9, 30)))
        }
    }

    Given("events that never remind") {
        Then("an event without reminder minutes, or already completed, plans nothing") {
            val start = at(LocalDate(2026, 9, 20), LocalTime(10, 0))
            val done = RecordedTime.of(now, zone)
            ReminderPlanner.events(
                listOf(event(start, minutes = null), event(start, completed = done)),
                today,
                LocalDate(2026, 9, 30),
                zone,
                now
            )
                .shouldBeEmpty()
        }
    }

    Given("a weekly recurring event") {
        Then("every occurrence in the window gets its own reminder") {
            val weekly = RecurrenceRule(RecurrenceRule.Frequency.WEEKLY, byDay = setOf(DayOfWeek.MONDAY))
            val start = at(LocalDate(2026, 9, 14), LocalTime(10, 0))
            ReminderPlanner.events(listOf(event(start, recurrence = weekly)), today, LocalDate(2026, 9, 30), zone, now).map {
                it.key
            } shouldBe
                listOf(eventKey(LocalDate(2026, 9, 14)), eventKey(LocalDate(2026, 9, 21)), eventKey(LocalDate(2026, 9, 28)))
        }
    }
})
