// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import app.cash.turbine.test
import br.com.colman.changes.core.model.BodyRegion
import br.com.colman.changes.core.model.BodyVocabulary
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.Profile
import br.com.colman.changes.core.model.TreatmentProtocol
import br.com.colman.changes.core.model.UnitSystem
import br.com.colman.changes.core.model.VocabularyChoice
import br.com.colman.changes.core.model.errorOrNull
import br.com.colman.changes.core.model.isSuccess
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.shouldEqual
import br.com.colman.changes.core.testing.testDatabase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.hours

private val DEFAULT_PROFILE = Profile(
    displayName = null,
    heightCm = null,
    birthYear = null,
    treatmentProtocol = TreatmentProtocol.MASCULINIZING,
    hrtStartDate = null,
    locale = null,
    unitSystem = UnitSystem.METRIC,
    showBmi = false,
    bodyVocabulary = BodyVocabulary(),
)

class ProfileRepositorySpec : FunSpec({
    val io = UnconfinedTestDispatcher()

    test("observe emits the default profile before any seed or write exists") {
        val clock = FixedClock()
        val database = testDatabase(clock, seed = false)
        val repository = ProfileRepository(database, io, clock)
        repository.observe().test {
            awaitItem() shouldEqual DEFAULT_PROFILE
        }
    }

    test("observe emits the seeded default row, then every update") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = ProfileRepository(database, io, clock)
        repository.observe().test {
            awaitItem() shouldEqual DEFAULT_PROFILE
            repository.update { it.copy(displayName = "Ana", heightCm = 170.0) }
            awaitItem() shouldEqual DEFAULT_PROFILE.copy(displayName = "Ana", heightCm = 170.0)
        }
    }

    test("update writes every field exactly, including updated_at, and applies the transform to the current profile") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = ProfileRepository(database, io, clock)
        clock.advance(1.hours)

        val expected = Profile(
            displayName = "Bea",
            heightCm = 165.5,
            birthYear = 1995,
            treatmentProtocol = TreatmentProtocol.MASCULINIZING,
            hrtStartDate = LocalDate(2024, 3, 1),
            locale = "pt-BR",
            unitSystem = UnitSystem.IMPERIAL,
            showBmi = true,
            bodyVocabulary = BodyVocabulary().with(BodyRegion.CHEST, VocabularyChoice.Preset("CHEST")),
        )
        val result = repository.update { expected }
        result.isSuccess shouldBe true

        val row = database.profileQueries.get().executeAsOne()
        row.display_name shouldBe "Bea"
        row.height_cm shouldBe 165.5
        row.birth_year shouldBe 1995L
        row.treatment_protocol shouldBe "MASCULINIZING"
        row.hrt_start_date shouldBe LocalDate(2024, 3, 1).toEpochDays()
        row.locale shouldBe "pt-BR"
        row.unit_system shouldBe "IMPERIAL"
        row.show_bmi shouldBe 1L
        row.body_vocabulary shouldBe Codecs.encodeVocabulary(expected.bodyVocabulary)
        row.updated_at shouldBe clock.now.toEpochMilliseconds()
        row.created_at shouldBe FixedClock.DEFAULT_NOW.toEpochMilliseconds()

        repository.observe().test { awaitItem() shouldEqual expected }
    }

    test("update on an unseeded database applies the transform to the default profile") {
        val clock = FixedClock()
        val database = testDatabase(clock, seed = false)
        val repository = ProfileRepository(database, io, clock)

        var seen: Profile? = null
        repository.update { current ->
            seen = current
            current.copy(displayName = "Ana")
        }

        seen shouldEqual DEFAULT_PROFILE
    }

    test("update transform receives the profile currently stored, not a stale copy") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = ProfileRepository(database, io, clock)
        repository.update { it.copy(displayName = "First") }

        var seenDuringSecondUpdate: String? = null
        repository.update { current ->
            seenDuringSecondUpdate = current.displayName
            current.copy(displayName = "Second")
        }

        seenDuringSecondUpdate shouldBe "First"
        repository.observe().test { awaitItem().displayName shouldBe "Second" }
    }

    test(
        "heightCm boundaries: null is valid, 0 and negative are rejected, up to 300 inclusive is valid, above is rejected"
    ) {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = ProfileRepository(database, io, clock)

        repository.update { it.copy(heightCm = null) }.isSuccess shouldBe true
        repository.update { it.copy(heightCm = 300.0) }.isSuccess shouldBe true
        repository.update { it.copy(heightCm = 0.1) }.isSuccess shouldBe true

        repository.update { it.copy(heightCm = 0.0) }.errorOrNull() shouldBe
            DomainError.Invalid("heightCm", DomainError.Reason.OUT_OF_RANGE)
        repository.update { it.copy(heightCm = -1.0) }.errorOrNull() shouldBe
            DomainError.Invalid("heightCm", DomainError.Reason.OUT_OF_RANGE)
        repository.update { it.copy(heightCm = 300.0001) }.errorOrNull() shouldBe
            DomainError.Invalid("heightCm", DomainError.Reason.OUT_OF_RANGE)
        repository.update { it.copy(heightCm = Double.NaN) }.errorOrNull() shouldBe
            DomainError.Invalid("heightCm", DomainError.Reason.NOT_FINITE)
        repository.update { it.copy(heightCm = Double.POSITIVE_INFINITY) }.errorOrNull() shouldBe
            DomainError.Invalid("heightCm", DomainError.Reason.NOT_FINITE)

        database.profileQueries.get().executeAsOne().height_cm shouldBe 0.1
    }

    test("birthYear boundaries: 1900 and the current year are valid, outside that is rejected") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = ProfileRepository(database, io, clock)

        repository.update { it.copy(birthYear = null) }.isSuccess shouldBe true
        repository.update { it.copy(birthYear = 1900) }.isSuccess shouldBe true
        repository.update { it.copy(birthYear = 2026) }.isSuccess shouldBe true

        repository.update { it.copy(birthYear = 1899) }.errorOrNull() shouldBe
            DomainError.Invalid("birthYear", DomainError.Reason.OUT_OF_RANGE)
        repository.update { it.copy(birthYear = 2027) }.errorOrNull() shouldBe
            DomainError.Invalid("birthYear", DomainError.Reason.OUT_OF_RANGE)

        database.profileQueries.get().executeAsOne().birth_year shouldBe 2026L
    }

    test("a rejected update does not write anything") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = ProfileRepository(database, io, clock)
        repository.update { it.copy(displayName = "Kept") }
        val before = database.profileQueries.get().executeAsOne()

        repository.update { it.copy(displayName = "Should not be saved", heightCm = -5.0) }

        database.profileQueries.get().executeAsOne() shouldBe before
    }
})
