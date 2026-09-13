// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import app.cash.turbine.test
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.LabAnalyte
import br.com.colman.changes.core.model.ReferenceRange
import br.com.colman.changes.core.model.errorOrNull
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.core.model.isSuccess
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.FixedTimeZoneProvider
import br.com.colman.changes.core.testing.shouldEqual
import br.com.colman.changes.core.testing.testDatabase
import br.com.colman.changes.core.testing.testDataset
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class LabRepositorySpec : FunSpec({
    val io = UnconfinedTestDispatcher()
    fun builtinAnalyteId() = testDataset.labAnalytes.first().id

    // --- Analitos --------------------------------------------------------------------------------

    test("createCustomAnalyte generates a CUSTOM_<id> code") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = LabRepository(database, io, clock, FixedTimeZoneProvider())

        val result = repository.createCustomAnalyte("Zinco", "µg/dL")
        result.isSuccess shouldBe true
        val created = result.getOrNull().shouldNotBeNull()
        created.code shouldBe "CUSTOM_${created.id}"
        created.isBuiltin shouldBe false
        database.labQueries.selectAnalyteById(created.id.toString()).executeAsOne().toModel() shouldEqual created
    }

    test("createCustomAnalyte rejects a blank label or unit and writes nothing") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = LabRepository(database, io, clock, FixedTimeZoneProvider())
        val before = database.labQueries.selectAllAnalytes().executeAsList()

        repository.createCustomAnalyte("  ", "u").errorOrNull() shouldBe DomainError.Invalid("label", DomainError.Reason.REQUIRED)
        repository.createCustomAnalyte("l", "  ").errorOrNull() shouldBe DomainError.Invalid("defaultUnit", DomainError.Reason.REQUIRED)
        database.labQueries.selectAllAnalytes().executeAsList() shouldBe before
    }

    test("updateCustomAnalyte on a builtin analyte is rejected; on an unknown id is NotFound") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = LabRepository(database, io, clock, FixedTimeZoneProvider())
        val builtin = database.labQueries.selectAnalyteById(builtinAnalyteId().toString()).executeAsOne().toModel()

        repository.updateCustomAnalyte(builtin.copy(customLabel = "x")).errorOrNull() shouldBe
            DomainError.Invalid("labAnalyte", DomainError.Reason.BUILTIN_IMMUTABLE)

        val ghost = LabAnalyte(Uuid.random(), "CUSTOM_x", null, "x", "u", false, false)
        repository.updateCustomAnalyte(ghost).errorOrNull() shouldBe DomainError.NotFound("labAnalyte", ghost.id.toString())
    }

    test("updateCustomAnalyte changes a custom analyte's fields") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = LabRepository(database, io, clock, FixedTimeZoneProvider())
        val created = repository.createCustomAnalyte("Zinco", "µg/dL").getOrNull().shouldNotBeNull()
        clock.advance(1.minutes)

        repository.updateCustomAnalyte(created.copy(customLabel = "Cobre", defaultUnit = "mg/dL")).isSuccess shouldBe true
        val row = database.labQueries.selectAnalyteById(created.id.toString()).executeAsOne()
        row.custom_label shouldBe "Cobre"
        row.default_unit shouldBe "mg/dL"
        row.updated_at shouldBe clock.now.toEpochMilliseconds()

        repository.updateCustomAnalyte(created.copy(customLabel = "")).errorOrNull() shouldBe
            DomainError.Invalid("customLabel", DomainError.Reason.REQUIRED)
    }

    test("setAnalyteHidden toggles visibility") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = LabRepository(database, io, clock, FixedTimeZoneProvider())
        val id = builtinAnalyteId()

        repository.observeVisibleAnalytes().test {
            awaitItem().map { it.id } shouldContain id
            repository.setAnalyteHidden(id, true).isSuccess shouldBe true
            awaitItem().map { it.id } shouldNotContain id
        }
        repository.observeAllAnalytes().test { awaitItem().map { it.id } shouldContain id }
    }

    test("deleteAnalyte on a builtin analyte is rejected; on a custom one it soft-deletes and restore reverses it") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = LabRepository(database, io, clock, FixedTimeZoneProvider())

        repository.deleteAnalyte(builtinAnalyteId()).errorOrNull() shouldBe
            DomainError.Invalid("labAnalyte", DomainError.Reason.BUILTIN_IMMUTABLE)

        val custom = repository.createCustomAnalyte("Zinco", "u").getOrNull().shouldNotBeNull()
        repository.deleteAnalyte(custom.id).isSuccess shouldBe true
        database.labQueries.selectAnalyteById(custom.id.toString()).executeAsOne().deleted_at.shouldNotBeNull()
        repository.restoreAnalyte(custom.id).isSuccess shouldBe true
        database.labQueries.selectAnalyteById(custom.id.toString()).executeAsOne().deleted_at.shouldBeNull()
    }

    test("deleteAnalyte on an unknown id is NotFound") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = LabRepository(database, io, clock, FixedTimeZoneProvider())
        val id = Uuid.random()

        repository.deleteAnalyte(id).errorOrNull() shouldBe DomainError.NotFound("labAnalyte", id.toString())
    }

    // --- Resultados ------------------------------------------------------------------------------

    test("createResult stores value, unit and reference range with the recorded collection time") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = LabRepository(database, io, clock, FixedTimeZoneProvider())
        val analyteId = builtinAnalyteId()

        val result = repository.createResult(
            NewLabResult(analyteId, 52.5, "ng/dL", clock.now, ReferenceRange(40.0, 54.0), "Lab X", "n")
        )
        result.isSuccess shouldBe true
        val created = result.getOrNull().shouldNotBeNull()

        val row = database.labQueries.selectResultById(created.id.toString()).executeAsOne()
        row.analyte_id shouldBe analyteId.toString()
        row.value_ shouldBe 52.5
        row.unit shouldBe "ng/dL"
        row.reference_low shouldBe 40.0
        row.reference_high shouldBe 54.0
        row.lab_name shouldBe "Lab X"
        row.toModel() shouldEqual created
    }

    test("createResult rejects an unknown analyte, a non-finite value, and a future collection time") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = LabRepository(database, io, clock, FixedTimeZoneProvider())
        val unknown = Uuid.random()

        repository.createResult(NewLabResult(unknown, 1.0, "u", clock.now, null, null, null)).errorOrNull() shouldBe
            DomainError.NotFound("labAnalyte", unknown.toString())
        repository.createResult(NewLabResult(builtinAnalyteId(), Double.NaN, "u", clock.now, null, null, null)).errorOrNull() shouldBe
            DomainError.Invalid("value", DomainError.Reason.NOT_FINITE)
        repository.createResult(
            NewLabResult(builtinAnalyteId(), 1.0, "u", clock.now + 1.minutes, null, null, null)
        ).errorOrNull() shouldBe DomainError.Invalid("collectedAt", DomainError.Reason.IN_THE_FUTURE)
    }

    test("createResult validates the reference range: finite bounds and low <= high") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = LabRepository(database, io, clock, FixedTimeZoneProvider())
        val analyteId = builtinAnalyteId()

        repository.createResult(
            NewLabResult(analyteId, 1.0, "u", clock.now, ReferenceRange(Double.NaN, 1.0), null, null)
        ).errorOrNull() shouldBe DomainError.Invalid("referenceRange.low", DomainError.Reason.NOT_FINITE)
        repository.createResult(
            NewLabResult(analyteId, 1.0, "u", clock.now, ReferenceRange(1.0, Double.NaN), null, null)
        ).errorOrNull() shouldBe DomainError.Invalid("referenceRange.high", DomainError.Reason.NOT_FINITE)
        repository.createResult(
            NewLabResult(analyteId, 1.0, "u", clock.now, ReferenceRange(10.0, 5.0), null, null)
        ).errorOrNull() shouldBe DomainError.Invalid("referenceRange", DomainError.Reason.END_BEFORE_START)
        repository.createResult(
            NewLabResult(analyteId, 1.0, "u", clock.now, ReferenceRange(5.0, 5.0), null, null)
        ).isSuccess shouldBe true
        repository.createResult(
            NewLabResult(analyteId, 1.0, "u", clock.now, ReferenceRange(null, null), null, null)
        ).isSuccess shouldBe true
        repository.createResult(NewLabResult(analyteId, 1.0, "u", clock.now, null, null, null)).isSuccess shouldBe true
        // Só um dos limites informado: não há como violar low <= high, então é sempre válido.
        repository.createResult(
            NewLabResult(analyteId, 1.0, "u", clock.now, ReferenceRange(5.0, null), null, null)
        ).isSuccess shouldBe true
        repository.createResult(
            NewLabResult(analyteId, 1.0, "u", clock.now, ReferenceRange(null, 5.0), null, null)
        ).isSuccess shouldBe true
    }

    test("observeResults and observeAllResults reflect writes") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = LabRepository(database, io, clock, FixedTimeZoneProvider())
        val analyteId = builtinAnalyteId()
        val created = repository.createResult(
            NewLabResult(analyteId, 1.0, "u", clock.now, null, null, null)
        ).getOrNull().shouldNotBeNull()

        repository.observeResults(analyteId).test { awaitItem().map { it.id } shouldContain created.id }
        repository.observeAllResults().test { awaitItem().map { it.id } shouldContain created.id }
    }

    test("updateResult validates the same rules and a rejection writes nothing") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = LabRepository(database, io, clock, FixedTimeZoneProvider())
        val analyteId = builtinAnalyteId()
        val created = repository.createResult(
            NewLabResult(analyteId, 1.0, "u", clock.now, ReferenceRange(0.0, 2.0), null, null)
        ).getOrNull().shouldNotBeNull()

        val updated = created.copy(value = 1.5, labName = "novo")
        repository.updateResult(updated).isSuccess shouldBe true
        database.labQueries.selectResultById(created.id.toString()).executeAsOne().toModel() shouldEqual updated

        repository.updateResult(updated.copy(value = Double.NaN)).errorOrNull() shouldBe
            DomainError.Invalid("value", DomainError.Reason.NOT_FINITE)
        database.labQueries.selectResultById(created.id.toString()).executeAsOne().toModel() shouldEqual updated
    }

    test("delete then restore a result") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = LabRepository(database, io, clock, FixedTimeZoneProvider())
        val created = repository.createResult(
            NewLabResult(builtinAnalyteId(), 1.0, "u", clock.now, null, null, null)
        ).getOrNull().shouldNotBeNull()

        repository.deleteResult(created.id).isSuccess shouldBe true
        database.labQueries.selectResultById(created.id.toString()).executeAsOne().deleted_at.shouldNotBeNull()
        repository.restoreResult(created.id).isSuccess shouldBe true
        database.labQueries.selectResultById(created.id.toString()).executeAsOne().deleted_at.shouldBeNull()
    }
})
