// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import app.cash.turbine.test
import br.com.colman.changes.core.clinical.ExpectedChangeRules
import br.com.colman.changes.core.model.AgendaItem
import br.com.colman.changes.core.model.CalendarCategory
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.Dose
import br.com.colman.changes.core.model.DoseUnit
import br.com.colman.changes.core.model.RecurrenceRule
import br.com.colman.changes.core.model.Route
import br.com.colman.changes.core.model.Schedule
import br.com.colman.changes.core.model.errorOrNull
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.core.model.isSuccess
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.FixedTimeZoneProvider
import br.com.colman.changes.core.testing.TestZones
import br.com.colman.changes.core.testing.testDatabase
import br.com.colman.changes.core.testing.testDataset
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.minutes

class CalendarRepositorySpec : FunSpec({
    val io = UnconfinedTestDispatcher()
    fun medicationId() = testDataset.medications.first { it.key == "DEPOSTERON" }.id

    fun newEvent(
        title: String = "Consulta",
        start: kotlin.time.Instant,
        end: kotlin.time.Instant? = null,
        isAllDay: Boolean = false,
        reminderMinutesBefore: Int? = null,
        recurrence: RecurrenceRule? = null,
    ) = NewCalendarEvent(
        title,
        "desc",
        start,
        end,
        isAllDay,
        CalendarCategory.APPOINTMENT,
        reminderMinutesBefore,
        recurrence
    )

    test("observeWithReminders lists only events that have a reminder and are not in the trash") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val profiles = ProfileRepository(database, io, clock)
        val repository = CalendarRepository(database, io, clock, FixedTimeZoneProvider(), profiles, testDataset)
        val start = clock.now + 60.minutes
        val reminded = repository.create(newEvent(title = "Com lembrete", start = start, reminderMinutesBefore = 30))
            .getOrNull().shouldNotBeNull()
        repository.create(newEvent(title = "Sem lembrete", start = start)).getOrNull().shouldNotBeNull()
        val trashed = repository.create(newEvent(title = "Na lixeira", start = start, reminderMinutesBefore = 0))
            .getOrNull().shouldNotBeNull()
        repository.delete(trashed.id)
        repository.observeWithReminders().test {
            awaitItem().map { it.id } shouldBe listOf(reminded.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    test("observeByCategory lists only live events of that category, oldest start first") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val profiles = ProfileRepository(database, io, clock)
        val repository = CalendarRepository(database, io, clock, FixedTimeZoneProvider(), profiles, testDataset)
        fun routine(title: String, start: kotlin.time.Instant) = NewCalendarEvent(
            title,
            null,
            start,
            null,
            false,
            CalendarCategory.EXERCISE,
            null,
            RecurrenceRule(RecurrenceRule.Frequency.DAILY),
        )
        val later = repository.create(routine("Corrida", clock.now + 60.minutes)).getOrNull().shouldNotBeNull()
        val earlier = repository.create(routine("Musculação", clock.now)).getOrNull().shouldNotBeNull()
        repository.create(newEvent(start = clock.now)).getOrNull().shouldNotBeNull()
        val trashed = repository.create(routine("Na lixeira", clock.now)).getOrNull().shouldNotBeNull()
        repository.delete(trashed.id)

        repository.observeByCategory(CalendarCategory.EXERCISE).test {
            awaitItem().map { it.id } shouldBe listOf(earlier.id, later.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    test("create stores a manual event with exact fields, and get/observeAgenda reflect it") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val profiles = ProfileRepository(database, io, clock)
        val repository = CalendarRepository(database, io, clock, FixedTimeZoneProvider(), profiles, testDataset)

        val result = repository.create(newEvent(start = clock.now, reminderMinutesBefore = 30))
        result.isSuccess shouldBe true
        val created = result.getOrNull().shouldNotBeNull()

        val row = database.calendarQueries.selectById(created.id.toString()).executeAsOne()
        row.title shouldBe "Consulta"
        row.category shouldBe "APPOINTMENT"
        row.source_type shouldBe "MANUAL"
        row.reminder_minutes_before shouldBe 30L
        row.created_at shouldBe clock.now.toEpochMilliseconds()
        repository.get(created.id)?.id shouldBe created.id
    }

    test("create rejects a blank title, end before start, a negative reminder, and an invalid recurrence") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val profiles = ProfileRepository(database, io, clock)
        val repository = CalendarRepository(database, io, clock, FixedTimeZoneProvider(), profiles, testDataset)

        repository.create(newEvent(title = " ", start = clock.now)).errorOrNull() shouldBe
            DomainError.Invalid("title", DomainError.Reason.REQUIRED)
        repository.create(newEvent(start = clock.now, end = clock.now - 1.minutes)).errorOrNull() shouldBe
            DomainError.Invalid("end", DomainError.Reason.END_BEFORE_START)
        repository.create(newEvent(start = clock.now, end = clock.now)).isSuccess shouldBe true
        repository.create(newEvent(start = clock.now, reminderMinutesBefore = -1)).errorOrNull() shouldBe
            DomainError.Invalid("reminderMinutesBefore", DomainError.Reason.OUT_OF_RANGE)
        repository.create(newEvent(start = clock.now, reminderMinutesBefore = 0)).isSuccess shouldBe true
        repository.create(
            newEvent(start = clock.now, recurrence = RecurrenceRule(RecurrenceRule.Frequency.DAILY, interval = 0))
        ).errorOrNull() shouldBe DomainError.Invalid("recurrenceRule", DomainError.Reason.OUT_OF_RANGE)
    }

    test("an all-day event stores start as local midnight of the chosen date (Seção 7.9)") {
        val clock = FixedClock()
        val zone = TestZones.SAO_PAULO
        val database = testDatabase(clock)
        val profiles = ProfileRepository(database, io, clock)
        val repository = CalendarRepository(database, io, clock, FixedTimeZoneProvider(zone), profiles, testDataset)
        val chosenNoon = LocalDateTime(LocalDate(2026, 9, 20), LocalTime(12, 0)).toInstant(zone)

        val created = repository.create(newEvent(start = chosenNoon, isAllDay = true)).getOrNull().shouldNotBeNull()

        created.start.localDate shouldBe LocalDate(2026, 9, 20)
        created.start.localDateTime.time shouldBe LocalTime(0, 0)
    }

    test("update validates the same rules and persists changes; setCompleted stores the recorded completion") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val profiles = ProfileRepository(database, io, clock)
        val repository = CalendarRepository(database, io, clock, FixedTimeZoneProvider(), profiles, testDataset)
        val created = repository.create(newEvent(start = clock.now)).getOrNull().shouldNotBeNull()

        val updated = created.copy(title = "Novo título")
        repository.update(updated).isSuccess shouldBe true
        database.calendarQueries.selectById(created.id.toString()).executeAsOne().title shouldBe "Novo título"

        repository.update(updated.copy(title = "")).errorOrNull() shouldBe DomainError.Invalid("title", DomainError.Reason.REQUIRED)

        repository.setCompleted(created.id, clock.now).isSuccess shouldBe true
        database.calendarQueries.selectById(created.id.toString()).executeAsOne().completed_at shouldBe clock.now.toEpochMilliseconds()
        repository.setCompleted(created.id, null).isSuccess shouldBe true
        database.calendarQueries.selectById(created.id.toString()).executeAsOne().completed_at.shouldBeNull()
    }

    test("delete then restore an event") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val profiles = ProfileRepository(database, io, clock)
        val repository = CalendarRepository(database, io, clock, FixedTimeZoneProvider(), profiles, testDataset)
        val created = repository.create(newEvent(start = clock.now)).getOrNull().shouldNotBeNull()

        repository.delete(created.id).isSuccess shouldBe true
        database.calendarQueries.selectById(created.id.toString()).executeAsOne().deleted_at.shouldNotBeNull()
        repository.restore(created.id).isSuccess shouldBe true
        database.calendarQueries.selectById(created.id.toString()).executeAsOne().deleted_at.shouldBeNull()
    }

    test("criterion 7.9.1: changing the device zone does not move an already-recorded all-day event") {
        val clock = FixedClock()
        val zones = FixedTimeZoneProvider(TestZones.SAO_PAULO)
        val database = testDatabase(clock)
        val profiles = ProfileRepository(database, io, clock)
        val repository = CalendarRepository(database, io, clock, zones, profiles, testDataset)
        val created = repository.create(newEvent(start = clock.now, isAllDay = true)).getOrNull().shouldNotBeNull()
        val originalDate = created.start.localDate

        zones.zone = TestZones.TOKYO

        val reread = repository.get(created.id).shouldNotBeNull()
        reread.start.localDate shouldBe originalDate
    }

    test("observeAgenda combines planned doses, logged doses, manual events and milestones, sorted by date") {
        val clock = FixedClock()
        val zone = TestZones.SAO_PAULO
        val zones = FixedTimeZoneProvider(zone)
        val database = testDatabase(clock)
        val profiles = ProfileRepository(database, io, clock)
        profiles.update { it.copy(hrtStartDate = LocalDate(2020, 1, 1)) }
        val regimens = RegimenRepository(database, io, clock)
        regimens.create(
            NewRegimen(
                medicationId(),
                Dose(100.0, DoseUnit.MG),
                Route.INTRAMUSCULAR,
                Schedule.IntervalDays(14),
                LocalTime(8, 0),
                LocalDate(2026, 9, 1),
                null,
                null,
            )
        )
        val doseLogs = DoseLogRepository(database, io, clock, zones)
        val loggedDate = LocalDate(2026, 9, 10)
        doseLogs.log(
            NewDoseLog(
                null,
                medicationId(),
                Dose(100.0, DoseUnit.MG),
                Route.INTRAMUSCULAR,
                null,
                loggedDate.atStartOfDayIn(zone),
                null
            )
        )
        val repository = CalendarRepository(database, io, clock, zones, profiles, testDataset)
        repository.create(
            newEvent(title = "Consulta", start = LocalDate(2026, 9, 20).atStartOfDayIn(zone), isAllDay = true)
        )

        val from = LocalDate(2026, 9, 1)
        val to = LocalDate(2026, 9, 30)
        repository.observeAgenda(from, to, includeMilestones = true).test {
            val items = awaitItem()
            items.zipWithNext().all { (a, b) -> a.date <= b.date } shouldBe true
            items.filterIsInstance<AgendaItem.PlannedDose>().map { it.date } shouldContain LocalDate(2026, 9, 1)
            items.filterIsInstance<AgendaItem.LoggedDose>().map { it.date } shouldContain loggedDate
            items.filterIsInstance<AgendaItem.Event>().map { it.date } shouldContain LocalDate(2026, 9, 20)
            val expectedMilestones = testDataset.expectedChanges.mapNotNull { change ->
                val date = ExpectedChangeRules.onsetStart(change, LocalDate(2020, 1, 1))
                if (date in from..to) date else null
            }
            items.filterIsInstance<AgendaItem.Milestone>().map { it.date } shouldBe expectedMilestones.sorted()
        }
        repository.observeAgenda(from, to, includeMilestones = false).test {
            awaitItem().filterIsInstance<AgendaItem.Milestone>().shouldBeEmpty()
        }
    }

    test("criterion 7.9.2: a regimen that already ended plans no dose past its end date") {
        val clock = FixedClock()
        val zone = TestZones.SAO_PAULO
        val database = testDatabase(clock)
        val profiles = ProfileRepository(database, io, clock)
        val regimens = RegimenRepository(database, io, clock)
        regimens.create(
            NewRegimen(
                medicationId(),
                Dose(100.0, DoseUnit.MG),
                Route.INTRAMUSCULAR,
                Schedule.IntervalDays(7),
                LocalTime(8, 0),
                LocalDate(2026, 1, 1),
                LocalDate(2026, 1, 15),
                null,
            )
        )
        val repository = CalendarRepository(database, io, clock, FixedTimeZoneProvider(zone), profiles, testDataset)

        repository.observeAgenda(LocalDate(2026, 1, 16), LocalDate(2026, 2, 28), includeMilestones = false).test {
            awaitItem().filterIsInstance<AgendaItem.PlannedDose>().shouldBeEmpty()
        }
    }

    test("observeAgenda expands a recurring event into one occurrence per matching date") {
        val clock = FixedClock()
        val zone = TestZones.SAO_PAULO
        val database = testDatabase(clock)
        val profiles = ProfileRepository(database, io, clock)
        val repository = CalendarRepository(database, io, clock, FixedTimeZoneProvider(zone), profiles, testDataset)
        repository.create(
            newEvent(
                title = "Semanal",
                start = LocalDate(2026, 9, 7).atStartOfDayIn(zone),
                isAllDay = true,
                recurrence = RecurrenceRule(RecurrenceRule.Frequency.WEEKLY, count = 3),
            )
        )

        repository.observeAgenda(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30), includeMilestones = false).test {
            val dates = awaitItem().filterIsInstance<AgendaItem.Event>().map { it.date }
            dates shouldBe listOf(LocalDate(2026, 9, 7), LocalDate(2026, 9, 14), LocalDate(2026, 9, 21))
        }
    }

    test(
        "observeAgenda includes a milestone exactly at its onset date, only when requested and only with hrtStartDate"
    ) {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val profiles = ProfileRepository(database, io, clock)
        val hrtStart = LocalDate(2020, 1, 1)
        val change = testDataset.expectedChanges.first()
        val onset = ExpectedChangeRules.onsetStart(change, hrtStart)
        val repository = CalendarRepository(database, io, clock, FixedTimeZoneProvider(), profiles, testDataset)

        // Sem hrtStartDate: nenhum marco, mesmo pedindo.
        repository.observeAgenda(onset, onset, includeMilestones = true).test {
            awaitItem().filterIsInstance<AgendaItem.Milestone>().shouldBeEmpty()
        }

        profiles.update { it.copy(hrtStartDate = hrtStart) }

        // Com hrtStartDate, exatamente na data do marco: aparece só se pedido. Outras mudanças podem
        // cair na mesma data (mesmo onsetMonthsMin), então checamos que a mudança específica está
        // presente, não que ela é a única.
        val expectedMilestone = AgendaItem.Milestone(change.changeTypeCode, onset)
        repository.observeAgenda(onset, onset, includeMilestones = true).test {
            awaitItem().filterIsInstance<AgendaItem.Milestone>() shouldContain expectedMilestone
        }
        repository.observeAgenda(onset, onset, includeMilestones = false).test {
            awaitItem().filterIsInstance<AgendaItem.Milestone>().shouldBeEmpty()
        }
        // Um dia fora da janela: o marco específico não aparece.
        val dayAfter = onset.plus(1, DateTimeUnit.DAY)
        repository.observeAgenda(dayAfter, dayAfter, includeMilestones = true).test {
            awaitItem().filterIsInstance<AgendaItem.Milestone>() shouldNotContain expectedMilestone
        }
    }

    test("observeAgenda excludes a logged dose and a non-recurring event outside the requested window") {
        val clock = FixedClock()
        val zone = TestZones.SAO_PAULO
        val zones = FixedTimeZoneProvider(zone)
        val database = testDatabase(clock)
        val profiles = ProfileRepository(database, io, clock)
        val doseLogs = DoseLogRepository(database, io, clock, zones)
        doseLogs.log(
            NewDoseLog(
                null,
                medicationId(),
                Dose(1.0, DoseUnit.MG),
                Route.ORAL,
                null,
                LocalDate(2026, 1, 1).atStartOfDayIn(zone),
                null,
            )
        )
        val repository = CalendarRepository(database, io, clock, zones, profiles, testDataset)
        repository.create(newEvent(title = "Fora", start = LocalDate(2026, 1, 1).atStartOfDayIn(zone), isAllDay = true))

        repository.observeAgenda(LocalDate(2026, 9, 1), LocalDate(2026, 9, 30), includeMilestones = false).test {
            val items = awaitItem()
            items.filterIsInstance<AgendaItem.LoggedDose>().shouldBeEmpty()
            items.filterIsInstance<AgendaItem.Event>().shouldBeEmpty()
        }
    }

    test("observeAgenda keeps a stable, insertion-preserving order for items on the same date") {
        val clock = FixedClock()
        val zone = TestZones.SAO_PAULO
        val zones = FixedTimeZoneProvider(zone)
        val database = testDatabase(clock)
        val profiles = ProfileRepository(database, io, clock)
        val sameDay = LocalDate(2026, 9, 10)
        val regimens = RegimenRepository(database, io, clock)
        regimens.create(
            NewRegimen(
                medicationId(),
                Dose(100.0, DoseUnit.MG),
                Route.INTRAMUSCULAR,
                Schedule.IntervalDays(30),
                LocalTime(8, 0),
                sameDay,
                null,
                null,
            )
        )
        val doseLogs = DoseLogRepository(database, io, clock, zones)
        doseLogs.log(
            NewDoseLog(
                null,
                medicationId(),
                Dose(1.0, DoseUnit.MG),
                Route.ORAL,
                null,
                sameDay.atStartOfDayIn(zone),
                null
            )
        )
        val repository = CalendarRepository(database, io, clock, zones, profiles, testDataset)
        repository.create(newEvent(title = "Mesmo dia", start = sameDay.atStartOfDayIn(zone), isAllDay = true))

        repository.observeAgenda(sameDay, sameDay, includeMilestones = false).test {
            val items = awaitItem()
            items.map { it::class } shouldBe listOf(
                AgendaItem.PlannedDose::class,
                AgendaItem.LoggedDose::class,
                AgendaItem.Event::class,
            )
        }
    }
})
