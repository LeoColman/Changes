// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import app.cash.turbine.test
import br.com.colman.changes.core.model.BodyChangeCategory
import br.com.colman.changes.core.model.BodyMeasurementUnit
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.Intensity
import br.com.colman.changes.core.model.MediaOwnerType
import br.com.colman.changes.core.model.errorOrNull
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.core.model.isSuccess
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.FixedTimeZoneProvider
import br.com.colman.changes.core.testing.TestZones
import br.com.colman.changes.core.testing.shouldEqual
import br.com.colman.changes.core.testing.testDatabase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class BodyChangeRepositorySpec : FunSpec({
    val io = UnconfinedTestDispatcher()

    fun measurableTypeId(database: br.com.colman.changes.core.db.sql.ChangesDatabase) =
        database.bodyChangeQueries.selectTypeByCode("VOICE_DEEPENING").executeAsOne().id.let { Uuid.parse(it) }

    fun plainTypeId(database: br.com.colman.changes.core.db.sql.ChangesDatabase) =
        database.bodyChangeQueries.selectTypeByCode("LIBIDO").executeAsOne().id.let { Uuid.parse(it) }

    // --- Tipos ---------------------------------------------------------------------------------

    test("createCustomType generates a CUSTOM_<id> code and derives supportsMeasurement from the unit") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = BodyChangeRepository(database, io, clock, FixedTimeZoneProvider())

        val result = repository.createCustomType("Minha mudança", BodyChangeCategory.SKIN, BodyMeasurementUnit.CM)
        result.isSuccess shouldBe true
        val created = result.getOrNull().shouldNotBeNull()
        created.code shouldBe "CUSTOM_${created.id}"
        created.supportsMeasurement shouldBe true
        created.isBuiltin shouldBe false
        created.isHidden shouldBe false
        created.isReversible.shouldBeNull()

        val withoutUnit = repository.createCustomType(
            "Outra",
            BodyChangeCategory.OTHER,
            null
        ).getOrNull().shouldNotBeNull()
        withoutUnit.supportsMeasurement shouldBe false
    }

    test("createCustomType rejects a blank label and writes nothing") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = BodyChangeRepository(database, io, clock, FixedTimeZoneProvider())
        val before = database.bodyChangeQueries.selectAllTypes().executeAsList()

        repository.createCustomType("  ", BodyChangeCategory.OTHER, null).errorOrNull() shouldBe
            DomainError.Invalid("label", DomainError.Reason.REQUIRED)
        database.bodyChangeQueries.selectAllTypes().executeAsList() shouldBe before
    }

    test("updateCustomType on a builtin type is rejected with BUILTIN_IMMUTABLE") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = BodyChangeRepository(database, io, clock, FixedTimeZoneProvider())
        val builtin = repository.getType(plainTypeId(database)).shouldNotBeNull()

        repository.updateCustomType(builtin.copy(customLabel = "hack")).errorOrNull() shouldBe
            DomainError.Invalid("bodyChangeType", DomainError.Reason.BUILTIN_IMMUTABLE)
    }

    test("updateCustomType on an unknown id is NotFound") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = BodyChangeRepository(database, io, clock, FixedTimeZoneProvider())
        val ghost = br.com.colman.changes.core.model.BodyChangeType(
            Uuid.random(), "CUSTOM_x", null, "x", BodyChangeCategory.OTHER, null, false, null, false, false
        )

        repository.updateCustomType(ghost).errorOrNull() shouldBe DomainError.NotFound("bodyChangeType", ghost.id.toString())
    }

    test("updateCustomType changes a custom type's fields and rejects a blank label") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = BodyChangeRepository(database, io, clock, FixedTimeZoneProvider())
        val created = repository.createCustomType(
            "Antiga",
            BodyChangeCategory.OTHER,
            null
        ).getOrNull().shouldNotBeNull()
        clock.advance(1.minutes)

        val updated = created.copy(
            customLabel = "Nova",
            category = BodyChangeCategory.SKIN,
            measurementUnit = BodyMeasurementUnit.CM
        )
        repository.updateCustomType(updated).isSuccess shouldBe true
        val row = database.bodyChangeQueries.selectTypeById(created.id.toString()).executeAsOne()
        row.custom_label shouldBe "Nova"
        row.category shouldBe "SKIN"
        row.supports_measurement shouldBe 1L
        row.measurement_unit shouldBe "CM"
        row.updated_at shouldBe clock.now.toEpochMilliseconds()

        repository.updateCustomType(updated.copy(customLabel = "")).errorOrNull() shouldBe
            DomainError.Invalid("customLabel", DomainError.Reason.REQUIRED)
        repository.updateCustomType(updated.copy(customLabel = null)).errorOrNull() shouldBe
            DomainError.Invalid("customLabel", DomainError.Reason.REQUIRED)

        repository.updateCustomType(updated.copy(measurementUnit = null)).isSuccess shouldBe true
        val clearedRow = database.bodyChangeQueries.selectTypeById(created.id.toString()).executeAsOne()
        clearedRow.supports_measurement shouldBe 0L
        clearedRow.measurement_unit.shouldBeNull()
    }

    test("setTypeHidden toggles visibility") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = BodyChangeRepository(database, io, clock, FixedTimeZoneProvider())
        val id = plainTypeId(database)

        repository.observeVisibleTypes().test {
            awaitItem().map { it.id } shouldContain id
            repository.setTypeHidden(id, true)
            awaitItem().map { it.id } shouldNotContain id
        }
        repository.observeAllTypes().test { awaitItem().map { it.id } shouldContain id }
    }

    test("deleteType on a builtin type is rejected; on a custom type it soft-deletes and restore reverses it") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = BodyChangeRepository(database, io, clock, FixedTimeZoneProvider())

        repository.deleteType(plainTypeId(database)).errorOrNull() shouldBe
            DomainError.Invalid("bodyChangeType", DomainError.Reason.BUILTIN_IMMUTABLE)

        val custom = repository.createCustomType(
            "Removível",
            BodyChangeCategory.OTHER,
            null
        ).getOrNull().shouldNotBeNull()
        repository.deleteType(custom.id).isSuccess shouldBe true
        database.bodyChangeQueries.selectTypeById(custom.id.toString()).executeAsOne().deleted_at.shouldNotBeNull()

        repository.restoreType(custom.id).isSuccess shouldBe true
        database.bodyChangeQueries.selectTypeById(custom.id.toString()).executeAsOne().deleted_at.shouldBeNull()
    }

    test("deleteType on an unknown id is NotFound") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = BodyChangeRepository(database, io, clock, FixedTimeZoneProvider())
        val id = Uuid.random()

        repository.deleteType(id).errorOrNull() shouldBe DomainError.NotFound("bodyChangeType", id.toString())
    }

    // --- Entradas --------------------------------------------------------------------------------

    test("createEntry stores an entry with the recorded time and, when supported, the type's measurement unit") {
        val clock = FixedClock()
        val zones = FixedTimeZoneProvider(TestZones.SAO_PAULO)
        val database = testDatabase(clock)
        val repository = BodyChangeRepository(database, io, clock, zones)
        val typeId = measurableTypeId(database)

        val result = repository.createEntry(typeId, clock.now, Intensity.MODERATE, 220.5, "nota")
        result.isSuccess shouldBe true
        val created = result.getOrNull().shouldNotBeNull()
        created.measurementUnit shouldBe BodyMeasurementUnit.HZ
        created.observedAt.epochMillis shouldBe clock.now.toEpochMilliseconds()

        val row = database.bodyChangeQueries.selectEntryById(created.id.toString()).executeAsOne()
        row.change_type_id shouldBe typeId.toString()
        row.intensity shouldBe 2L
        row.measurement_value shouldBe 220.5
        row.measurement_unit shouldBe "HZ"
        row.toModel() shouldEqual created
    }

    test("createEntry on an unsupported type ignores a given value only through OUT_OF_RANGE rejection") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = BodyChangeRepository(database, io, clock, FixedTimeZoneProvider())
        val typeId = plainTypeId(database)

        repository.createEntry(typeId, clock.now, null, 1.0, null).errorOrNull() shouldBe
            DomainError.Invalid("measurementValue", DomainError.Reason.OUT_OF_RANGE)
        repository.createEntry(typeId, clock.now, Intensity.MILD, null, null).isSuccess shouldBe true
    }

    test("createEntry rejects a non-finite measurement and a future observedAt; unknown type is NotFound") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = BodyChangeRepository(database, io, clock, FixedTimeZoneProvider())
        val typeId = measurableTypeId(database)

        repository.createEntry(typeId, clock.now, null, Double.NaN, null).errorOrNull() shouldBe
            DomainError.Invalid("measurementValue", DomainError.Reason.NOT_FINITE)
        repository.createEntry(typeId, clock.now + 1.minutes, null, null, null).errorOrNull() shouldBe
            DomainError.Invalid("observedAt", DomainError.Reason.IN_THE_FUTURE)
        val unknown = Uuid.random()
        repository.createEntry(unknown, clock.now, null, null, null).errorOrNull() shouldBe
            DomainError.NotFound("bodyChangeType", unknown.toString())
    }

    test("updateEntry re-derives the measurement unit from the type and validates the same rules") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = BodyChangeRepository(database, io, clock, FixedTimeZoneProvider())
        val typeId = measurableTypeId(database)
        val created = repository.createEntry(
            typeId,
            clock.now,
            Intensity.MILD,
            200.0,
            null
        ).getOrNull().shouldNotBeNull()

        val result = repository.updateEntry(created.copy(measurementValue = null, intensity = null, notes = "novo"))
        result.isSuccess shouldBe true
        result.getOrNull()?.measurementUnit.shouldBeNull()
        val clearedRow = database.bodyChangeQueries.selectEntryById(created.id.toString()).executeAsOne()
        clearedRow.measurement_value.shouldBeNull()
        clearedRow.measurement_unit.shouldBeNull()
        clearedRow.intensity.shouldBeNull()
        clearedRow.notes shouldBe "novo"

        val restored = repository.updateEntry(created.copy(measurementValue = 50.0, intensity = Intensity.COMPLETE))
        restored.isSuccess shouldBe true
        val filledRow = database.bodyChangeQueries.selectEntryById(created.id.toString()).executeAsOne()
        filledRow.measurement_value shouldBe 50.0
        filledRow.measurement_unit shouldBe "HZ"
        filledRow.intensity shouldBe 4L

        repository.updateEntry(created.copy(observedAt = created.observedAt.let { it.copy(instant = clock.now + 1.minutes) }))
            .errorOrNull() shouldBe DomainError.Invalid("observedAt", DomainError.Reason.IN_THE_FUTURE)
        repository.updateEntry(created.copy(measurementValue = Double.NaN)).errorOrNull() shouldBe
            DomainError.Invalid("measurementValue", DomainError.Reason.NOT_FINITE)

        val unknownType = Uuid.random()
        repository.updateEntry(created.copy(changeTypeId = unknownType)).errorOrNull() shouldBe
            DomainError.NotFound("bodyChangeType", unknownType.toString())
    }

    test("observeEntriesByType and observeAllEntries reflect writes") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = BodyChangeRepository(database, io, clock, FixedTimeZoneProvider())
        val typeId = plainTypeId(database)
        val created = repository.createEntry(
            typeId,
            clock.now,
            Intensity.MILD,
            null,
            null
        ).getOrNull().shouldNotBeNull()

        repository.observeEntriesByType(typeId).test { awaitItem().map { it.id } shouldContain created.id }
        repository.observeAllEntries().test { awaitItem().map { it.id } shouldContain created.id }
        repository.getEntry(created.id) shouldEqual created
        repository.getEntry(Uuid.random()).shouldBeNull()
    }

    test(
        "critério 7.3.2 / ADR 0008: deleting an entry soft-deletes its media with the same deleted_at, and restore reverses it"
    ) {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = BodyChangeRepository(database, io, clock, FixedTimeZoneProvider())
        val typeId = plainTypeId(database)
        val entry = repository.createEntry(typeId, clock.now, null, null, null).getOrNull().shouldNotBeNull()
        val media = br.com.colman.changes.core.model.MediaAttachment(
            Uuid.random(),
            MediaOwnerType.BODY_CHANGE_ENTRY,
            entry.id,
            "${Uuid.random()}.jpg",
            "image/jpeg",
            null,
            "abc",
            null
        )
        database.mediaQueries.insert(media.toRow(clock.now.toEpochMilliseconds(), clock.now.toEpochMilliseconds()))

        clock.advance(1.days)
        repository.deleteEntry(entry.id).isSuccess shouldBe true
        val deletedAt = clock.now.toEpochMilliseconds()
        database.bodyChangeQueries.selectEntryById(entry.id.toString()).executeAsOne().deleted_at shouldBe deletedAt
        database.mediaQueries.selectById(media.id.toString()).executeAsOne().deleted_at shouldBe deletedAt

        clock.advance(1.days)
        repository.restoreEntry(entry.id).isSuccess shouldBe true
        database.bodyChangeQueries.selectEntryById(entry.id.toString()).executeAsOne().deleted_at.shouldBeNull()
        database.mediaQueries.selectById(media.id.toString()).executeAsOne().deleted_at.shouldBeNull()
    }

    test("restoreEntry on an entry that was never deleted, or that does not exist, does not crash or restore media") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = BodyChangeRepository(database, io, clock, FixedTimeZoneProvider())
        val typeId = plainTypeId(database)
        val entry = repository.createEntry(typeId, clock.now, null, null, null).getOrNull().shouldNotBeNull()

        repository.restoreEntry(entry.id).isSuccess shouldBe true
        database.bodyChangeQueries.selectEntryById(entry.id.toString()).executeAsOne().deleted_at.shouldBeNull()

        repository.restoreEntry(Uuid.random()).isSuccess shouldBe true
    }

    test("restoreEntry does not touch media deleted at a different time than the entry") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = BodyChangeRepository(database, io, clock, FixedTimeZoneProvider())
        val typeId = plainTypeId(database)
        val entry = repository.createEntry(typeId, clock.now, null, null, null).getOrNull().shouldNotBeNull()
        val media = br.com.colman.changes.core.model.MediaAttachment(
            Uuid.random(),
            MediaOwnerType.BODY_CHANGE_ENTRY,
            entry.id,
            "${Uuid.random()}.jpg",
            "image/jpeg",
            null,
            "abc",
            null
        )
        database.mediaQueries.insert(media.toRow(clock.now.toEpochMilliseconds(), clock.now.toEpochMilliseconds()))
        // Mídia excluída num momento diferente do da entrada (ex.: excluída manualmente antes).
        database.mediaQueries.softDelete(clock.now.toEpochMilliseconds() - 1, media.id.toString())

        clock.advance(1.days)
        repository.deleteEntry(entry.id)
        clock.advance(1.days)
        repository.restoreEntry(entry.id)

        database.mediaQueries.selectById(media.id.toString()).executeAsOne().deleted_at.shouldNotBeNull()
    }

    test("firstObservations maps each type's code to the local date of its earliest non-deleted entry") {
        val clock = FixedClock()
        val zones = FixedTimeZoneProvider(TestZones.SAO_PAULO)
        val database = testDatabase(clock)
        val repository = BodyChangeRepository(database, io, clock, zones)
        val typeId = plainTypeId(database)

        val later = repository.createEntry(typeId, clock.now, null, null, null).getOrNull().shouldNotBeNull()
        clock.advance(-2.days)
        val earlier = repository.createEntry(typeId, clock.now, null, null, null).getOrNull().shouldNotBeNull()

        val result = repository.firstObservations()

        result["LIBIDO"] shouldBe earlier.observedAt.localDate
        result["LIBIDO"] shouldNotBe later.observedAt.localDate
    }
})
