// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.platform.reminders

import br.com.colman.changes.core.data.CalendarRepository
import br.com.colman.changes.core.data.NewCalendarEvent
import br.com.colman.changes.core.data.NewRegimen
import br.com.colman.changes.core.data.ProfileRepository
import br.com.colman.changes.core.data.RegimenRepository
import br.com.colman.changes.core.model.CalendarCategory
import br.com.colman.changes.core.model.Dose
import br.com.colman.changes.core.model.DoseUnit
import br.com.colman.changes.core.model.Route
import br.com.colman.changes.core.model.Schedule
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.FixedTimeZoneProvider
import br.com.colman.changes.core.testing.testDatabase
import br.com.colman.changes.core.testing.testDataset
import br.com.colman.changes.platform.AppSettings
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.minutes

class PlannedUpcomingRemindersSpec : FunSpec({
    val io = UnconfinedTestDispatcher()
    val today = LocalDate(2026, 9, 12) // FixedClock: 09:00 em São Paulo

    test("doses of active regimens with a time and events with a reminder become alarms of their kind") {
        val env = PlanningEnvironment(io)
        val regimen = env.regimenAt(LocalTime(20, 0))
        val eventStart = LocalDateTime(LocalDate(2026, 9, 20), LocalTime(10, 0)).toInstant(env.zones.zone)
        val event = env.eventAt(eventStart, reminderMinutes = 60)

        val source = PlannedUpcomingReminders(env.regimens, env.calendar, FakeSettingsStore(), env.zones)
        val reminders = source.upcoming(env.clock.now())

        val doseDates = listOf(today, LocalDate(2026, 9, 19), LocalDate(2026, 9, 26))
        reminders.filter { it.kind == ReminderKind.DOSE }.map { it.key } shouldBe
            doseDates.map { "dose:${regimen.id}:${it.toEpochDays()}" }
        val eventKey = "event:${event.id}:${LocalDate(2026, 9, 20).toEpochDays()}"
        reminders.filter { it.kind == ReminderKind.EVENT } shouldBe
            listOf(ScheduledReminder(eventKey, eventStart - 60.minutes, ReminderKind.EVENT))
    }

    test("with dose reminders turned off only events remain") {
        val env = PlanningEnvironment(io)
        env.regimenAt(LocalTime(20, 0))
        val eventStart = LocalDateTime(LocalDate(2026, 9, 20), LocalTime(10, 0)).toInstant(env.zones.zone)
        env.eventAt(eventStart, reminderMinutes = 0)
        val settings = FakeSettingsStore(AppSettings(doseRemindersEnabled = false))

        val source = PlannedUpcomingReminders(env.regimens, env.calendar, settings, env.zones)
        val reminders = source.upcoming(env.clock.now())

        reminders.map { it.kind } shouldBe listOf(ReminderKind.EVENT)
    }
})

/** Banco em memória com regimes e calendário reais, relógio e fuso fixos. */
internal class PlanningEnvironment(io: kotlinx.coroutines.CoroutineDispatcher) {
    val clock = FixedClock()
    val zones = FixedTimeZoneProvider()
    private val database = testDatabase(clock)
    val regimens = RegimenRepository(database, io, clock)
    val calendar = CalendarRepository(database, io, clock, zones, ProfileRepository(database, io, clock), testDataset)

    suspend fun regimenAt(time: LocalTime) = regimens.create(
        NewRegimen(
            testDataset.medications.first().id,
            Dose(1.0, DoseUnit.MG),
            Route.INTRAMUSCULAR,
            Schedule.IntervalDays(7),
            time,
            LocalDate(2026, 9, 12),
            null,
            null,
        ),
    ).getOrNull().shouldNotBeNull()

    suspend fun eventAt(start: kotlin.time.Instant, reminderMinutes: Int?) = calendar.create(
        NewCalendarEvent("Consulta", null, start, null, false, CalendarCategory.APPOINTMENT, reminderMinutes, null),
    ).getOrNull().shouldNotBeNull()
}
