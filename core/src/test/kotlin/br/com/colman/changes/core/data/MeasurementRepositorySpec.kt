// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import app.cash.turbine.test
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.MeasurementType
import br.com.colman.changes.core.model.MeasurementUnit
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
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.time.Duration.Companion.minutes

class MeasurementRepositorySpec : FunSpec({
    val io = UnconfinedTestDispatcher()

    test("create stores a weight measurement with the recorded time and exact fields") {
        val clock = FixedClock()
        val zones = FixedTimeZoneProvider(TestZones.SAO_PAULO)
        val database = testDatabase(clock)
        val profiles = ProfileRepository(database, io, clock)
        val repository = MeasurementRepository(database, io, clock, zones, profiles)

        val result = repository.create(MeasurementType.WEIGHT, null, 82.5, MeasurementUnit.KG, clock.now, "manha")
        result.isSuccess shouldBe true
        val created = result.getOrNull().shouldNotBeNull()

        val row = database.measurementQueries.selectById(created.id.toString()).executeAsOne()
        row.type shouldBe "WEIGHT"
        row.value_ shouldBe 82.5
        row.unit shouldBe "KG"
        row.measured_at shouldBe clock.now.toEpochMilliseconds()
        row.measured_at_offset_seconds shouldBe created.measuredAt.offsetSeconds
        row.notes shouldBe "manha"
        row.toModel() shouldEqual created
    }

    test("unit compatibility: weight only accepts KG/LB, girths only CM/IN, body fat only PERCENT 0..100") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository =
            MeasurementRepository(database, io, clock, FixedTimeZoneProvider(), ProfileRepository(database, io, clock))

        repository.create(MeasurementType.WEIGHT, null, 1.0, MeasurementUnit.CM, clock.now, null).errorOrNull() shouldBe
            DomainError.Invalid("unit", DomainError.Reason.OUT_OF_RANGE)
        repository.create(MeasurementType.WEIGHT, null, 1.0, MeasurementUnit.KG, clock.now, null).isSuccess shouldBe true
        repository.create(MeasurementType.WEIGHT, null, 1.0, MeasurementUnit.LB, clock.now, null).isSuccess shouldBe true
        repository.create(MeasurementType.WAIST, null, 1.0, MeasurementUnit.KG, clock.now, null).errorOrNull() shouldBe
            DomainError.Invalid("unit", DomainError.Reason.OUT_OF_RANGE)
        repository.create(MeasurementType.WAIST, null, 1.0, MeasurementUnit.CM, clock.now, null).isSuccess shouldBe true
        repository.create(MeasurementType.HIP, null, 1.0, MeasurementUnit.IN, clock.now, null).isSuccess shouldBe true
        repository.create(MeasurementType.CHEST, null, 1.0, MeasurementUnit.CM, clock.now, null).isSuccess shouldBe true
        repository.create(MeasurementType.BICEP, null, 1.0, MeasurementUnit.CM, clock.now, null).isSuccess shouldBe true
        repository.create(MeasurementType.NECK, null, 1.0, MeasurementUnit.CM, clock.now, null).isSuccess shouldBe true

        repository.create(MeasurementType.BODY_FAT_PCT, null, 1.0, MeasurementUnit.CM, clock.now, null).errorOrNull() shouldBe
            DomainError.Invalid("unit", DomainError.Reason.OUT_OF_RANGE)
        repository.create(MeasurementType.BODY_FAT_PCT, null, 0.0, MeasurementUnit.PERCENT, clock.now, null).isSuccess shouldBe true
        repository.create(MeasurementType.BODY_FAT_PCT, null, 100.0, MeasurementUnit.PERCENT, clock.now, null).isSuccess shouldBe true
        repository.create(MeasurementType.BODY_FAT_PCT, null, 100.1, MeasurementUnit.PERCENT, clock.now, null).errorOrNull() shouldBe
            DomainError.Invalid("value", DomainError.Reason.OUT_OF_RANGE)
        repository.create(MeasurementType.BODY_FAT_PCT, null, -0.1, MeasurementUnit.PERCENT, clock.now, null).errorOrNull() shouldBe
            DomainError.Invalid("value", DomainError.Reason.OUT_OF_RANGE)
        // O limite 0..100 é só para BODY_FAT_PCT: um peso fora dessa faixa numérica continua válido.
        repository.create(MeasurementType.WEIGHT, null, 220.462, MeasurementUnit.LB, clock.now, null).isSuccess shouldBe true
    }

    test("custom type requires a non-blank label but accepts any unit") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository =
            MeasurementRepository(database, io, clock, FixedTimeZoneProvider(), ProfileRepository(database, io, clock))

        repository.create(MeasurementType.CUSTOM, null, 1.0, MeasurementUnit.PERCENT, clock.now, null).errorOrNull() shouldBe
            DomainError.Invalid("customLabel", DomainError.Reason.REQUIRED)
        repository.create(MeasurementType.CUSTOM, "  ", 1.0, MeasurementUnit.PERCENT, clock.now, null).errorOrNull() shouldBe
            DomainError.Invalid("customLabel", DomainError.Reason.REQUIRED)
        repository.create(MeasurementType.CUSTOM, "panturrilha", 1.0, MeasurementUnit.CM, clock.now, null).isSuccess shouldBe true
    }

    test("value must be finite and measuredAt cannot be in the future") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository =
            MeasurementRepository(database, io, clock, FixedTimeZoneProvider(), ProfileRepository(database, io, clock))

        repository.create(MeasurementType.WEIGHT, null, Double.NaN, MeasurementUnit.KG, clock.now, null).errorOrNull() shouldBe
            DomainError.Invalid("value", DomainError.Reason.NOT_FINITE)
        repository.create(MeasurementType.WEIGHT, null, 1.0, MeasurementUnit.KG, clock.now + 1.minutes, null).errorOrNull() shouldBe
            DomainError.Invalid("measuredAt", DomainError.Reason.IN_THE_FUTURE)
    }

    test("update validates the same rules and a rejection writes nothing") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository =
            MeasurementRepository(database, io, clock, FixedTimeZoneProvider(), ProfileRepository(database, io, clock))
        val created = repository.create(
            MeasurementType.WEIGHT,
            null,
            80.0,
            MeasurementUnit.KG,
            clock.now,
            null
        ).getOrNull().shouldNotBeNull()

        val updated = created.copy(value = 79.0, notes = "novo")
        repository.update(updated).isSuccess shouldBe true
        database.measurementQueries.selectById(created.id.toString()).executeAsOne().toModel() shouldEqual updated

        repository.update(updated.copy(value = Double.NaN)).errorOrNull() shouldBe
            DomainError.Invalid("value", DomainError.Reason.NOT_FINITE)
        database.measurementQueries.selectById(created.id.toString()).executeAsOne().toModel() shouldEqual updated
    }

    test("observeByType and observeAll reflect writes") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository =
            MeasurementRepository(database, io, clock, FixedTimeZoneProvider(), ProfileRepository(database, io, clock))
        val created = repository.create(
            MeasurementType.WEIGHT,
            null,
            80.0,
            MeasurementUnit.KG,
            clock.now,
            null
        ).getOrNull().shouldNotBeNull()

        repository.observeByType(MeasurementType.WEIGHT).test { awaitItem().map { it.id } shouldContain created.id }
        repository.observeAll().test { awaitItem().map { it.id } shouldContain created.id }
    }

    test("delete then restore a measurement") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository =
            MeasurementRepository(database, io, clock, FixedTimeZoneProvider(), ProfileRepository(database, io, clock))
        val created = repository.create(
            MeasurementType.WEIGHT,
            null,
            80.0,
            MeasurementUnit.KG,
            clock.now,
            null
        ).getOrNull().shouldNotBeNull()

        repository.delete(created.id).isSuccess shouldBe true
        database.measurementQueries.selectById(created.id.toString()).executeAsOne().deleted_at.shouldNotBeNull()
        repository.restore(created.id).isSuccess shouldBe true
        database.measurementQueries.selectById(created.id.toString()).executeAsOne().deleted_at.shouldBeNull()
    }

    test("criteria 7.4.1/7.4.2: BMI is null without showBmi, without height, or without a weight measurement") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val profiles = ProfileRepository(database, io, clock)
        val repository = MeasurementRepository(database, io, clock, FixedTimeZoneProvider(), profiles)

        repository.observeBmi().test { awaitItem().shouldBeNull() }

        profiles.update { it.copy(showBmi = true) }
        repository.observeBmi().test { awaitItem().shouldBeNull() }

        profiles.update { it.copy(heightCm = 180.0) }
        repository.observeBmi().test { awaitItem().shouldBeNull() }

        repository.create(MeasurementType.WEIGHT, null, 90.0, MeasurementUnit.KG, clock.now, null)
        repository.observeBmi().test { awaitItem() shouldBe (90.0 / (1.8 * 1.8)) }

        profiles.update { it.copy(showBmi = false) }
        repository.observeBmi().test { awaitItem().shouldBeNull() }
    }

    test(
        "BMI condition isolation: each of showBmi, height and weight alone gates the result, the other two satisfied"
    ) {
        val clock = FixedClock()

        val missingHeightOnly = testDatabase(clock)
        val profilesA = ProfileRepository(missingHeightOnly, io, clock)
        profilesA.update { it.copy(showBmi = true) }
        val repositoryA = MeasurementRepository(missingHeightOnly, io, clock, FixedTimeZoneProvider(), profilesA)
        repositoryA.create(MeasurementType.WEIGHT, null, 70.0, MeasurementUnit.KG, clock.now, null)
        repositoryA.observeBmi().test { awaitItem().shouldBeNull() }

        val missingWeightOnly = testDatabase(clock)
        val profilesB = ProfileRepository(missingWeightOnly, io, clock)
        profilesB.update { it.copy(showBmi = true, heightCm = 170.0) }
        val repositoryB = MeasurementRepository(missingWeightOnly, io, clock, FixedTimeZoneProvider(), profilesB)
        repositoryB.observeBmi().test { awaitItem().shouldBeNull() }

        val missingShowBmiOnly = testDatabase(clock)
        val profilesC = ProfileRepository(missingShowBmiOnly, io, clock)
        profilesC.update { it.copy(showBmi = false, heightCm = 170.0) }
        val repositoryC = MeasurementRepository(missingShowBmiOnly, io, clock, FixedTimeZoneProvider(), profilesC)
        repositoryC.create(MeasurementType.WEIGHT, null, 70.0, MeasurementUnit.KG, clock.now, null)
        repositoryC.observeBmi().test { awaitItem().shouldBeNull() }
    }

    test("BMI uses the most recent weight and converts pounds to kilograms") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val profiles = ProfileRepository(database, io, clock)
        profiles.update { it.copy(showBmi = true, heightCm = 200.0) }
        val repository = MeasurementRepository(database, io, clock, FixedTimeZoneProvider(), profiles)
        repository.create(MeasurementType.WEIGHT, null, 100.0, MeasurementUnit.KG, clock.now, null).isSuccess shouldBe true
        clock.advance(1.minutes)
        // 242.508 lb ~= 110 kg: valor claramente diferente do primeiro, para provar que é o mais
        // recente (e convertido) que entra no cálculo, não só o primeiro registrado.
        repository.create(MeasurementType.WEIGHT, null, 242.508, MeasurementUnit.LB, clock.now, null).isSuccess shouldBe true

        repository.observeBmi().test {
            val bmi = awaitItem().shouldNotBeNull()
            bmi shouldBe (110.0 / 4.0 plusOrMinus 0.01)
        }
    }
})
