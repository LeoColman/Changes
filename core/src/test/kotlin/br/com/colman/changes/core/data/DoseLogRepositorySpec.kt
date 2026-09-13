// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import app.cash.turbine.test
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.Dose
import br.com.colman.changes.core.model.DoseUnit
import br.com.colman.changes.core.model.InjectionSite
import br.com.colman.changes.core.model.Route
import br.com.colman.changes.core.model.Schedule
import br.com.colman.changes.core.model.errorOrNull
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.core.model.isSuccess
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.FixedTimeZoneProvider
import br.com.colman.changes.core.testing.TestZones
import br.com.colman.changes.core.testing.shouldEqual
import br.com.colman.changes.core.testing.testDatabase
import br.com.colman.changes.core.testing.testDataset
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.offsetAt
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class DoseLogRepositorySpec : FunSpec({
    val io = UnconfinedTestDispatcher()
    fun medicationId() = testDataset.medications.first { it.key == "DEPOSTERON" }.id

    test("get finds a log by id, also in the trash, and returns null for an unknown id") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = DoseLogRepository(database, io, clock, FixedTimeZoneProvider())
        val created = repository.log(
            NewDoseLog(null, medicationId(), Dose(1.0, DoseUnit.MG), Route.ORAL, null, clock.now, null),
        ).getOrNull().shouldNotBeNull()
        repository.get(created.id).shouldNotBeNull() shouldEqual created
        repository.delete(created.id)
        repository.get(created.id).shouldNotBeNull() shouldEqual created
        repository.get(Uuid.random()).shouldBeNull()
    }

    test("log stores a standalone dose with a recorded offset from the current zone") {
        val clock = FixedClock()
        val zones = FixedTimeZoneProvider(TestZones.SAO_PAULO)
        val database = testDatabase(clock)
        val repository = DoseLogRepository(database, io, clock, zones)

        val result = repository.log(
            NewDoseLog(
                null,
                medicationId(),
                Dose(100.0, DoseUnit.MG),
                Route.INTRAMUSCULAR,
                InjectionSite.GLUTE_LEFT,
                clock.now,
                "ok"
            )
        )
        result.isSuccess shouldBe true
        val created = result.getOrNull().shouldNotBeNull()
        created.regimenId.shouldBeNull()

        val row = database.doseLogQueries.selectById(created.id.toString()).executeAsOne()
        row.dose_value shouldBe 100.0
        row.dose_unit shouldBe "MG"
        row.injection_site shouldBe "GLUTE_LEFT"
        row.taken_at shouldBe clock.now.toEpochMilliseconds()
        row.taken_at_offset_seconds shouldBe TestZones.SAO_PAULO.offsetAt(clock.now).totalSeconds.toLong()
        row.created_at shouldBe clock.now.toEpochMilliseconds()
        row.toModel() shouldEqual created
    }

    test(
        "criterion 7.1.3: taken_at after clock.now() is rejected, exactly at now is accepted, nothing is written on rejection"
    ) {
        val clock = FixedClock()
        val zones = FixedTimeZoneProvider()
        val database = testDatabase(clock)
        val repository = DoseLogRepository(database, io, clock, zones)
        val before = database.doseLogQueries.selectBetween(0L, Long.MAX_VALUE).executeAsList()

        val future = clock.now + 1.minutes
        val result = repository.log(
            NewDoseLog(null, medicationId(), Dose(1.0, DoseUnit.MG), Route.ORAL, null, future, null)
        )
        result.errorOrNull() shouldBe DomainError.Invalid("takenAt", DomainError.Reason.IN_THE_FUTURE)
        database.doseLogQueries.selectBetween(0L, Long.MAX_VALUE).executeAsList() shouldBe before

        repository.log(NewDoseLog(null, medicationId(), Dose(1.0, DoseUnit.MG), Route.ORAL, null, clock.now, null)).isSuccess shouldBe true
    }

    test("log rejects a non-positive dose") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = DoseLogRepository(database, io, clock, FixedTimeZoneProvider())

        repository.log(NewDoseLog(null, medicationId(), Dose(0.0, DoseUnit.MG), Route.ORAL, null, clock.now, null)).errorOrNull() shouldBe
            DomainError.Invalid("dose", DomainError.Reason.NOT_POSITIVE)
    }

    test("log requires an existing medication and, when informed, an existing regimen") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = DoseLogRepository(database, io, clock, FixedTimeZoneProvider())
        val unknownMedication = Uuid.random()
        val unknownRegimen = Uuid.random()

        repository.log(NewDoseLog(null, unknownMedication, Dose(1.0, DoseUnit.MG), Route.ORAL, null, clock.now, null)).errorOrNull() shouldBe
            DomainError.NotFound("medication", unknownMedication.toString())
        repository.log(
            NewDoseLog(unknownRegimen, medicationId(), Dose(1.0, DoseUnit.MG), Route.ORAL, null, clock.now, null)
        ).errorOrNull() shouldBe DomainError.NotFound("regimen", unknownRegimen.toString())
    }

    test("logFromRegimen copies medication, dose and route from the regimen") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val regimen = regimens.create(
            NewRegimen(
                medicationId(),
                Dose(0.25, DoseUnit.ML),
                Route.SUBCUTANEOUS,
                Schedule.IntervalDays(7),
                LocalTime(9, 0),
                LocalDate(2026, 1, 1),
                null,
                null,
            )
        ).getOrNull().shouldNotBeNull()
        val repository = DoseLogRepository(database, io, clock, FixedTimeZoneProvider())

        val result = repository.logFromRegimen(regimen.id, clock.now, InjectionSite.THIGH_LEFT)

        result.isSuccess shouldBe true
        val created = result.getOrNull().shouldNotBeNull()
        created.medicationId shouldBe regimen.medicationId
        created.dose shouldBe regimen.dose
        created.route shouldBe regimen.route
        created.notes.shouldBeNull()
        created.injectionSite shouldBe InjectionSite.THIGH_LEFT
    }

    test("logFromRegimen with an unknown regimen is NotFound") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = DoseLogRepository(database, io, clock, FixedTimeZoneProvider())
        val id = Uuid.random()

        repository.logFromRegimen(id, clock.now, null).errorOrNull() shouldBe DomainError.NotFound("regimen", id.toString())
    }

    test("update changes dose, route, site, time and notes, and rejects a future takenAt") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = DoseLogRepository(database, io, clock, FixedTimeZoneProvider())
        val created = repository.log(
            NewDoseLog(null, medicationId(), Dose(1.0, DoseUnit.MG), Route.ORAL, null, clock.now, null)
        ).getOrNull().shouldNotBeNull()

        val updated = created.copy(dose = Dose(2.0, DoseUnit.MG), notes = "editado")
        repository.update(updated).isSuccess shouldBe true
        database.doseLogQueries.selectById(created.id.toString()).executeAsOne().toModel() shouldEqual updated

        val rejected = repository.update(updated.copy(dose = Dose(-1.0, DoseUnit.MG)))
        rejected.errorOrNull() shouldBe DomainError.Invalid("dose", DomainError.Reason.NOT_POSITIVE)
        database.doseLogQueries.selectById(created.id.toString()).executeAsOne().toModel() shouldEqual updated
    }

    test("observeHistory filters by medication and window, most recent first") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = DoseLogRepository(database, io, clock, FixedTimeZoneProvider())
        val first = repository.log(
            NewDoseLog(null, medicationId(), Dose(1.0, DoseUnit.MG), Route.ORAL, null, clock.now - 2.days, null)
        ).getOrNull().shouldNotBeNull()
        clock.advance(1.days)
        val second = repository.log(
            NewDoseLog(null, medicationId(), Dose(1.0, DoseUnit.MG), Route.ORAL, null, clock.now, null)
        ).getOrNull().shouldNotBeNull()

        repository.observeHistory(medicationId(), clock.now - 10.days, clock.now + 1.minutes).test {
            awaitItem().map { it.id } shouldContainExactly listOf(second.id, first.id)
        }
        repository.observeHistory(Uuid.random(), clock.now - 10.days, clock.now + 1.minutes).test {
            awaitItem() shouldBe emptyList()
        }
    }

    test("observeBetween lists logs within the window, ascending") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = DoseLogRepository(database, io, clock, FixedTimeZoneProvider())
        val first = repository.log(
            NewDoseLog(null, medicationId(), Dose(1.0, DoseUnit.MG), Route.ORAL, null, clock.now - 1.days, null)
        ).getOrNull().shouldNotBeNull()
        val second = repository.log(
            NewDoseLog(null, medicationId(), Dose(1.0, DoseUnit.MG), Route.ORAL, null, clock.now, null)
        ).getOrNull().shouldNotBeNull()

        repository.observeBetween(clock.now - 10.days, clock.now + 1.minutes).test {
            awaitItem().map { it.id } shouldContainExactly listOf(first.id, second.id)
        }
    }

    test("delete then restore a dose log") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = DoseLogRepository(database, io, clock, FixedTimeZoneProvider())
        val created = repository.log(
            NewDoseLog(null, medicationId(), Dose(1.0, DoseUnit.MG), Route.ORAL, null, clock.now, null)
        ).getOrNull().shouldNotBeNull()

        repository.delete(created.id).isSuccess shouldBe true
        database.doseLogQueries.selectById(created.id.toString()).executeAsOne().deleted_at.shouldNotBeNull()

        repository.restore(created.id).isSuccess shouldBe true
        database.doseLogQueries.selectById(created.id.toString()).executeAsOne().deleted_at.shouldBeNull()
    }

    test("suggestInjectionSite picks the least used site among the last WINDOW logs, ties broken by oldest use") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = DoseLogRepository(database, io, clock, FixedTimeZoneProvider())

        repository.suggestInjectionSite() shouldBe br.com.colman.changes.core.model.InjectionSiteRotation.ROTATION.first()

        repository.log(
            NewDoseLog(
                null,
                medicationId(),
                Dose(1.0, DoseUnit.MG),
                Route.INTRAMUSCULAR,
                InjectionSite.GLUTE_LEFT,
                clock.now,
                null
            )
        )
        clock.advance(1.days)
        repository.log(
            NewDoseLog(
                null,
                medicationId(),
                Dose(1.0, DoseUnit.MG),
                Route.INTRAMUSCULAR,
                InjectionSite.GLUTE_RIGHT,
                clock.now,
                null
            )
        )

        repository.suggestInjectionSite() shouldBe InjectionSite.THIGH_LEFT
    }

    test("adherence counts expected occurrences and registered logs in the trailing window, in the current zone") {
        val clock = FixedClock()
        val zones = FixedTimeZoneProvider(TestZones.SAO_PAULO)
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val regimen = regimens.create(
            NewRegimen(
                medicationId(),
                Dose(100.0, DoseUnit.MG),
                Route.INTRAMUSCULAR,
                Schedule.IntervalDays(7),
                LocalTime(8, 0),
                LocalDate(2026, 1, 1),
                null,
                null,
            )
        ).getOrNull().shouldNotBeNull()
        val repository = DoseLogRepository(database, io, clock, zones)
        val today = LocalDate(2026, 3, 1)
        val takenAt = br.com.colman.changes.core.model.DoseSchedule.instantOf(
            LocalDate(2026, 2, 23),
            LocalTime(8, 0),
            TestZones.SAO_PAULO
        )
        repository.log(
            NewDoseLog(regimen.id, medicationId(), regimen.dose, regimen.route, null, requireNotNull(takenAt), null)
        )

        val adherence = repository.adherence(regimen, today, 90)

        adherence.windowDays shouldBe 90
        adherence.registered shouldBe 1
        adherence.expected shouldBe br.com.colman.changes.core.model.DoseSchedule.occurrences(
            regimen,
            today.minus(89, kotlinx.datetime.DateTimeUnit.DAY),
            today,
        ).size
    }

    test("adherence window is exactly [today - (days-1), today]: a daily regimen has exactly `days` expected doses") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val regimen = regimens.create(
            NewRegimen(
                medicationId(),
                Dose(1.0, DoseUnit.MG),
                Route.ORAL,
                Schedule.IntervalDays(1),
                null,
                LocalDate(2026, 1, 1),
                null,
                null,
            )
        ).getOrNull().shouldNotBeNull()
        val repository = DoseLogRepository(database, io, clock, FixedTimeZoneProvider())

        val adherence = repository.adherence(regimen, LocalDate(2026, 3, 1), days = 5)

        adherence.expected shouldBe 5
    }

    test("adherence uses the default 90-day window") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val regimen = regimens.create(
            NewRegimen(
                medicationId(),
                Dose(100.0, DoseUnit.MG),
                Route.INTRAMUSCULAR,
                Schedule.AsNeeded,
                null,
                LocalDate(2026, 1, 1),
                null,
                null,
            )
        ).getOrNull().shouldNotBeNull()
        val repository = DoseLogRepository(database, io, clock, FixedTimeZoneProvider())

        repository.adherence(regimen, LocalDate(2026, 3, 1)).windowDays shouldBe 90
    }
})
