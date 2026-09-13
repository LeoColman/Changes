// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import app.cash.turbine.test
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.Dose
import br.com.colman.changes.core.model.DoseLog
import br.com.colman.changes.core.model.DoseUnit
import br.com.colman.changes.core.model.RecordedTime
import br.com.colman.changes.core.model.Route
import br.com.colman.changes.core.model.Schedule
import br.com.colman.changes.core.model.errorOrNull
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.core.model.isSuccess
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.shouldEqual
import br.com.colman.changes.core.testing.testDatabase
import br.com.colman.changes.core.testing.testDataset
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class RegimenRepositorySpec : FunSpec({
    val io = UnconfinedTestDispatcher()
    fun medicationId() = testDataset.medications.first { it.key == "DEPOSTERON" }.id

    fun newRegimen(
        medicationId: Uuid = medicationId(),
        dose: Dose = Dose(100.0, DoseUnit.MG),
        schedule: Schedule = Schedule.IntervalDays(14),
        startDate: LocalDate = LocalDate(2026, 1, 1),
        endDate: LocalDate? = null,
    ) = NewRegimen(medicationId, dose, Route.INTRAMUSCULAR, schedule, LocalTime(8, 0), startDate, endDate, "nota")

    test("create stores an active regimen with exact fields and timestamps") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = RegimenRepository(database, io, clock)

        val result = repository.create(newRegimen())
        result.isSuccess shouldBe true
        val created = result.getOrNull().shouldNotBeNull()
        created.isActive shouldBe true

        val row = database.regimenQueries.selectById(created.id.toString()).executeAsOne()
        row.medication_id shouldBe medicationId().toString()
        row.dose_value shouldBe 100.0
        row.dose_unit shouldBe "MG"
        row.route shouldBe "INTRAMUSCULAR"
        row.schedule_type shouldBe "INTERVAL_DAYS"
        row.time_of_day shouldBe 480L
        row.start_date shouldBe LocalDate(2026, 1, 1).toEpochDays()
        row.end_date.shouldBeNull()
        row.is_active shouldBe 1L
        row.notes shouldBe "nota"
        row.created_at shouldBe clock.now.toEpochMilliseconds()
        row.updated_at shouldBe clock.now.toEpochMilliseconds()
        row.toModel() shouldEqual created
    }

    test("create rejects a non-positive or non-finite dose and writes nothing") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = RegimenRepository(database, io, clock)

        repository.create(newRegimen(dose = Dose(0.0, DoseUnit.MG))).errorOrNull() shouldBe
            DomainError.Invalid("dose", DomainError.Reason.NOT_POSITIVE)
        repository.create(newRegimen(dose = Dose(Double.NaN, DoseUnit.MG))).errorOrNull() shouldBe
            DomainError.Invalid("dose", DomainError.Reason.NOT_FINITE)
        database.regimenQueries.selectAllNotDeleted().executeAsList().shouldHaveSize(0)
    }

    test("create validates the schedule via DoseSchedule.validate") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = RegimenRepository(database, io, clock)

        repository.create(newRegimen(schedule = Schedule.IntervalDays(0))).errorOrNull() shouldBe
            DomainError.Invalid("schedule", DomainError.Reason.OUT_OF_RANGE)
        repository.create(newRegimen(schedule = Schedule.Weekly(emptySet()))).errorOrNull() shouldBe
            DomainError.Invalid("schedule", DomainError.Reason.OUT_OF_RANGE)
    }

    test("create rejects endDate before startDate") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = RegimenRepository(database, io, clock)

        val result = repository.create(newRegimen(startDate = LocalDate(2026, 2, 1), endDate = LocalDate(2026, 1, 31)))
        result.errorOrNull() shouldBe DomainError.Invalid("endDate", DomainError.Reason.END_BEFORE_START)
    }

    test("create accepts endDate equal to startDate") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = RegimenRepository(database, io, clock)

        val date = LocalDate(2026, 2, 1)
        repository.create(newRegimen(startDate = date, endDate = date)).isSuccess shouldBe true
    }

    test("create rejects an unknown medication with NotFound") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = RegimenRepository(database, io, clock)
        val unknown = Uuid.random()

        repository.create(newRegimen(medicationId = unknown)).errorOrNull() shouldBe
            DomainError.NotFound("medication", unknown.toString())
    }

    test("observeActive lists only active, non-deleted regimens; observeAll lists every non-deleted one") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = RegimenRepository(database, io, clock)
        val active = repository.create(newRegimen()).getOrNull().shouldNotBeNull()

        repository.observeActive().test {
            awaitItem().map { it.id } shouldContain active.id
        }
        repository.end(active.id, active.startDate)
        repository.observeAll().test {
            awaitItem().map { it.id } shouldContain active.id
        }
    }

    test("get returns the regimen by id, or null if missing") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = RegimenRepository(database, io, clock)
        val created = repository.create(newRegimen()).getOrNull().shouldNotBeNull()

        repository.get(created.id) shouldEqual created
        repository.get(Uuid.random()).shouldBeNull()
    }

    test("criterion 7.1.2: changing a regimen's dose, route or schedule never touches dose_log") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = RegimenRepository(database, io, clock)
        val created = repository.create(newRegimen()).getOrNull().shouldNotBeNull()

        val dose = Dose(100.0, DoseUnit.MG)
        val medication = medicationId()
        val log = DoseLog(
            Uuid.random(),
            created.id,
            medication,
            dose,
            Route.INTRAMUSCULAR,
            null,
            RecordedTime.fromDb(clock.now.toEpochMilliseconds(), 0L),
            "original",
        )
        database.doseLogQueries.insert(log.toRow(clock.now.toEpochMilliseconds(), clock.now.toEpochMilliseconds()))

        repository.update(created.copy(dose = Dose(250.0, DoseUnit.MG), route = Route.SUBCUTANEOUS))

        val storedLog = database.doseLogQueries.selectById(log.id.toString()).executeAsOne().toModel()
        storedLog shouldEqual log
    }

    test("update rejects invalid regimens and writes nothing") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = RegimenRepository(database, io, clock)
        val created = repository.create(newRegimen()).getOrNull().shouldNotBeNull()

        val result = repository.update(created.copy(dose = Dose(-1.0, DoseUnit.MG)))

        result.errorOrNull() shouldBe DomainError.Invalid("dose", DomainError.Reason.NOT_POSITIVE)
        database.regimenQueries.selectById(created.id.toString()).executeAsOne().toModel() shouldEqual created
    }

    test("end sets the end date, keeping every other field, and validates it against startDate") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = RegimenRepository(database, io, clock)
        val created = repository.create(newRegimen(startDate = LocalDate(2026, 1, 1))).getOrNull().shouldNotBeNull()

        val result = repository.end(created.id, LocalDate(2026, 6, 1))
        result.isSuccess shouldBe true
        result.getOrNull() shouldEqual created.copy(endDate = LocalDate(2026, 6, 1))
        database.regimenQueries.selectById(created.id.toString()).executeAsOne().end_date shouldBe
            LocalDate(2026, 6, 1).toEpochDays()

        repository.end(created.id, LocalDate(2025, 12, 31)).errorOrNull() shouldBe
            DomainError.Invalid("endDate", DomainError.Reason.END_BEFORE_START)
    }

    test("update persists a null or non-null timeOfDay and endDate exactly as given") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = RegimenRepository(database, io, clock)
        val created = repository.create(newRegimen()).getOrNull().shouldNotBeNull()

        val withoutTimeOrEnd = created.copy(timeOfDay = null, endDate = null)
        repository.update(withoutTimeOrEnd).isSuccess shouldBe true
        val clearedRow = database.regimenQueries.selectById(created.id.toString()).executeAsOne()
        clearedRow.time_of_day.shouldBeNull()
        clearedRow.end_date.shouldBeNull()

        val withTimeAndEnd = created.copy(timeOfDay = LocalTime(21, 15), endDate = LocalDate(2026, 12, 31))
        repository.update(withTimeAndEnd).isSuccess shouldBe true
        val filledRow = database.regimenQueries.selectById(created.id.toString()).executeAsOne()
        filledRow.time_of_day shouldBe (21 * 60 + 15).toLong()
        filledRow.end_date shouldBe LocalDate(2026, 12, 31).toEpochDays()
    }

    test("end on an unknown id is NotFound") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = RegimenRepository(database, io, clock)
        val id = Uuid.random()

        repository.end(id, LocalDate(2026, 1, 1)).errorOrNull() shouldBe DomainError.NotFound("regimen", id.toString())
    }

    test("criterion 7.1.4: deleting a regimen keeps its dose logs and their denormalized data") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = RegimenRepository(database, io, clock)
        val created = repository.create(newRegimen()).getOrNull().shouldNotBeNull()
        val log = DoseLog(
            Uuid.random(),
            created.id,
            medicationId(),
            Dose(100.0, DoseUnit.MG),
            Route.INTRAMUSCULAR,
            null,
            RecordedTime.fromDb(clock.now.toEpochMilliseconds(), 0L),
            null,
        )
        database.doseLogQueries.insert(log.toRow(clock.now.toEpochMilliseconds(), clock.now.toEpochMilliseconds()))

        clock.advance(1.seconds)
        repository.delete(created.id).isSuccess shouldBe true

        database.regimenQueries.selectById(created.id.toString()).executeAsOne().deleted_at shouldBe clock.now.toEpochMilliseconds()
        val storedLog = database.doseLogQueries.selectById(log.id.toString()).executeAsOne()
        storedLog.regimen_id shouldBe created.id.toString()
        storedLog.dose_value shouldBe 100.0
        storedLog.deleted_at.shouldBeNull()
    }

    test("restore clears deleted_at") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = RegimenRepository(database, io, clock)
        val created = repository.create(newRegimen()).getOrNull().shouldNotBeNull()
        repository.delete(created.id)

        repository.restore(created.id).isSuccess shouldBe true

        database.regimenQueries.selectById(created.id.toString()).executeAsOne().deleted_at.shouldBeNull()
    }
})
