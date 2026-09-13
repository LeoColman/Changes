// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import app.cash.turbine.test
import br.com.colman.changes.core.model.Concentration
import br.com.colman.changes.core.model.ConcentrationUnit
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.Medication
import br.com.colman.changes.core.model.Route
import br.com.colman.changes.core.model.errorOrNull
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.core.model.isSuccess
import br.com.colman.changes.core.testing.FixedClock
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
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

class MedicationRepositorySpec : FunSpec({
    val io = UnconfinedTestDispatcher()
    fun builtinId() = testDataset.medications.first { it.key == "DEPOSTERON" }.id

    test("observeVisible only lists visible, non-deleted medications; observeAll ignores hidden but keeps them") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = MedicationRepository(database, io, clock)
        val builtin = builtinId()

        repository.observeVisible().test {
            awaitItem().map { it.id } shouldContain builtin
            repository.setHidden(builtin, true)
            awaitItem().map { it.id } shouldNotContain builtin
        }
        repository.observeAll().test {
            awaitItem().map { it.id } shouldContain builtin
        }
    }

    test("get returns the medication by id, or null if it doesn't exist") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = MedicationRepository(database, io, clock)

        repository.get(builtinId())?.name shouldBe "Deposteron"
        repository.get(Uuid.random()).shouldBeNull()
    }

    test("createCustom stores a new, non-builtin, non-hidden medication with exact timestamps") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = MedicationRepository(database, io, clock)

        val result = repository.createCustom(
            "Minha hormona",
            "substancia",
            Route.ORAL,
            Concentration(10.0, ConcentrationUnit.MG_PER_ML)
        )
        result.isSuccess shouldBe true
        val created = result.getOrNull().shouldNotBeNull()
        created.isBuiltin shouldBe false
        created.isHidden shouldBe false

        val row = database.medicationQueries.selectById(created.id.toString()).executeAsOne()
        row.name shouldBe "Minha hormona"
        row.substance shouldBe "substancia"
        row.default_route shouldBe "ORAL"
        row.concentration_value shouldBe 10.0
        row.concentration_unit shouldBe "MG_PER_ML"
        row.created_at shouldBe clock.now.toEpochMilliseconds()
        row.updated_at shouldBe clock.now.toEpochMilliseconds()
        row.toModel() shouldEqual created
    }

    test("createCustom without substance, route or concentration is allowed") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = MedicationRepository(database, io, clock)

        val result = repository.createCustom("Simples", null, null, null)
        result.isSuccess shouldBe true
        result.getOrNull()?.concentration.shouldBeNull()
    }

    test("createCustom rejects a blank name and writes nothing") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = MedicationRepository(database, io, clock)
        val before = database.medicationQueries.selectAllActive().executeAsList()

        val result = repository.createCustom("   ", null, null, null)

        result.errorOrNull() shouldBe DomainError.Invalid("name", DomainError.Reason.REQUIRED)
        database.medicationQueries.selectAllActive().executeAsList() shouldBe before
    }

    test("createCustom concentration must be finite and positive") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = MedicationRepository(database, io, clock)

        repository.createCustom("x", null, null, Concentration(0.0, ConcentrationUnit.MG_PER_ML)).errorOrNull() shouldBe
            DomainError.Invalid("concentration", DomainError.Reason.NOT_POSITIVE)
        repository.createCustom("x", null, null, Concentration(Double.NaN, ConcentrationUnit.MG_PER_ML)).errorOrNull() shouldBe
            DomainError.Invalid("concentration", DomainError.Reason.NOT_FINITE)
        repository.createCustom("x", null, null, Concentration(0.001, ConcentrationUnit.MG_PER_ML)).isSuccess shouldBe true
    }

    test("updateCustom changes a custom medication's fields and updated_at") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = MedicationRepository(database, io, clock)
        val created = repository.createCustom("Antiga", null, null, null).getOrNull().shouldNotBeNull()
        clock.advance(1.seconds)

        val updated = created.copy(name = "Nova", substance = "s", defaultRoute = Route.TOPICAL)
        repository.updateCustom(updated).isSuccess shouldBe true

        val row = database.medicationQueries.selectById(created.id.toString()).executeAsOne()
        row.name shouldBe "Nova"
        row.substance shouldBe "s"
        row.default_route shouldBe "TOPICAL"
        row.updated_at shouldBe clock.now.toEpochMilliseconds()
    }

    test("updateCustom on a builtin medication is rejected with BUILTIN_IMMUTABLE and writes nothing") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = MedicationRepository(database, io, clock)
        val builtin = repository.get(builtinId()).shouldNotBeNull()
        val before = database.medicationQueries.selectById(builtin.id.toString()).executeAsOne()

        val result = repository.updateCustom(builtin.copy(name = "Hacked"))

        result.errorOrNull() shouldBe DomainError.Invalid("medication", DomainError.Reason.BUILTIN_IMMUTABLE)
        database.medicationQueries.selectById(builtin.id.toString()).executeAsOne() shouldBe before
    }

    test("updateCustom on an unknown id is rejected with NotFound") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = MedicationRepository(database, io, clock)
        val ghost = Medication(Uuid.random(), "x", null, null, null, false, false)

        repository.updateCustom(ghost).errorOrNull() shouldBe DomainError.NotFound("medication", ghost.id.toString())
    }

    test("updateCustom rejects an invalid name or concentration on an existing custom row") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = MedicationRepository(database, io, clock)
        val created = repository.createCustom("Antiga", null, null, null).getOrNull().shouldNotBeNull()

        repository.updateCustom(created.copy(name = "")).errorOrNull() shouldBe
            DomainError.Invalid("name", DomainError.Reason.REQUIRED)
        repository.updateCustom(created.copy(concentration = Concentration(-1.0, ConcentrationUnit.MG_PER_ML))).errorOrNull() shouldBe
            DomainError.Invalid("concentration", DomainError.Reason.NOT_POSITIVE)
    }

    test("setHidden toggles visibility and updates the timestamp") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = MedicationRepository(database, io, clock)
        val builtin = builtinId()
        clock.advance(1.seconds)

        repository.setHidden(builtin, true).isSuccess shouldBe true
        val row = database.medicationQueries.selectById(builtin.toString()).executeAsOne()
        row.is_hidden shouldBe 1L
        row.updated_at shouldBe clock.now.toEpochMilliseconds()

        repository.setHidden(builtin, false)
        database.medicationQueries.selectById(builtin.toString()).executeAsOne().is_hidden shouldBe 0L
    }

    test("delete on a builtin medication is rejected and does not soft-delete it") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = MedicationRepository(database, io, clock)
        val builtin = builtinId()

        repository.delete(builtin).errorOrNull() shouldBe DomainError.Invalid("medication", DomainError.Reason.BUILTIN_IMMUTABLE)
        database.medicationQueries.selectById(builtin.toString()).executeAsOne().deleted_at.shouldBeNull()
    }

    test("delete on an unknown id is NotFound") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = MedicationRepository(database, io, clock)
        val id = Uuid.random()

        repository.delete(id).errorOrNull() shouldBe DomainError.NotFound("medication", id.toString())
    }

    test("delete then restore a custom medication") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = MedicationRepository(database, io, clock)
        val created = repository.createCustom("Removível", null, null, null).getOrNull().shouldNotBeNull()

        repository.delete(created.id).isSuccess shouldBe true
        database.medicationQueries.selectById(created.id.toString()).executeAsOne().deleted_at.shouldNotBeNull()
        repository.observeVisible().test { awaitItem().map { it.id } shouldNotContain created.id }

        repository.restore(created.id).isSuccess shouldBe true
        database.medicationQueries.selectById(created.id.toString()).executeAsOne().deleted_at.shouldBeNull()
        repository.get(created.id).shouldNotBeNull()
    }
})
