// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import app.cash.turbine.test
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.MoodLog
import br.com.colman.changes.core.model.errorOrNull
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.core.model.isSuccess
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.FixedTimeZoneProvider
import br.com.colman.changes.core.testing.TestZones
import br.com.colman.changes.core.testing.shouldEqual
import br.com.colman.changes.core.testing.testDatabase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.uuid.Uuid

class MoodRepositorySpec : FunSpec({
    val io = UnconfinedTestDispatcher()

    // Modelo padrão para os testes; cada caso ajusta só os campos que importam via `.copy(...)`
    // (uma função com 8 parâmetros violaria o limite do Detekt, que não isenta funções de teste).
    val defaultMood = MoodLog(Uuid.random(), LocalDate(2026, 9, 1), 3, 3, null, null, 7.5, "dia ok", listOf("trabalho"))

    test("upsert inserts a new row, ignoring draft.id, and cleans the tags") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = MoodRepository(database, io, clock, FixedTimeZoneProvider())

        val result = repository.upsert(defaultMood.copy(tags = listOf(" a ", "", "a", "b")))
        result.isSuccess shouldBe true
        val created = result.getOrNull().shouldNotBeNull()
        created.tags shouldBe listOf("a", "b")

        val row = database.moodQueries.selectById(created.id.toString()).executeAsOne()
        row.entry_date shouldBe LocalDate(2026, 9, 1).toEpochDays()
        row.mood shouldBe 3L
        row.created_at shouldBe clock.now.toEpochMilliseconds()
        row.toModel() shouldEqual created
    }

    test("criterion 7.7.1: upserting again on the same date overwrites the same row, even if it was deleted") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = MoodRepository(database, io, clock, FixedTimeZoneProvider())
        val first = repository.upsert(defaultMood.copy(mood = 2)).getOrNull().shouldNotBeNull()
        repository.delete(first.id)

        val second = repository.upsert(defaultMood.copy(mood = 5, note = "novo"))
        second.isSuccess shouldBe true
        val updated = second.getOrNull().shouldNotBeNull()
        updated.id shouldBe first.id

        val row = database.moodQueries.selectById(first.id.toString()).executeAsOne()
        row.mood shouldBe 5L
        row.note shouldBe "novo"
        row.deleted_at.shouldBeNull()
        database.moodQueries.selectAll().executeAsList() shouldContainExactly listOf(row)
    }

    test("scale boundaries: 1 and 5 are valid for mood/energy/anxiety/dysphoria, outside that is rejected") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = MoodRepository(database, io, clock, FixedTimeZoneProvider())

        repository.upsert(defaultMood.copy(mood = 1)).isSuccess shouldBe true
        repository.upsert(defaultMood.copy(mood = 5)).isSuccess shouldBe true
        repository.upsert(defaultMood.copy(mood = 0)).errorOrNull() shouldBe DomainError.Invalid("mood", DomainError.Reason.OUT_OF_RANGE)
        repository.upsert(defaultMood.copy(mood = 6)).errorOrNull() shouldBe DomainError.Invalid("mood", DomainError.Reason.OUT_OF_RANGE)
        repository.upsert(defaultMood.copy(energy = 6)).errorOrNull() shouldBe DomainError.Invalid("energy", DomainError.Reason.OUT_OF_RANGE)
        repository.upsert(defaultMood.copy(anxiety = 6)).errorOrNull() shouldBe DomainError.Invalid("anxiety", DomainError.Reason.OUT_OF_RANGE)
        repository.upsert(defaultMood.copy(anxiety = 1)).isSuccess shouldBe true
        repository.upsert(defaultMood.copy(dysphoria = 0)).errorOrNull() shouldBe DomainError.Invalid("dysphoria", DomainError.Reason.OUT_OF_RANGE)
    }

    test("sleepHours must be within 0..24") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = MoodRepository(database, io, clock, FixedTimeZoneProvider())

        repository.upsert(defaultMood.copy(sleepHours = 0.0)).isSuccess shouldBe true
        repository.upsert(defaultMood.copy(sleepHours = 24.0)).isSuccess shouldBe true
        repository.upsert(defaultMood.copy(sleepHours = -0.1)).errorOrNull() shouldBe
            DomainError.Invalid("sleepHours", DomainError.Reason.OUT_OF_RANGE)
        repository.upsert(defaultMood.copy(sleepHours = 24.1)).errorOrNull() shouldBe
            DomainError.Invalid("sleepHours", DomainError.Reason.OUT_OF_RANGE)
        repository.upsert(defaultMood.copy(sleepHours = null)).isSuccess shouldBe true
    }

    test(
        "the date cannot be after today in the current zone; today itself is accepted; nothing is written on rejection"
    ) {
        val clock = FixedClock()
        val zones = FixedTimeZoneProvider(TestZones.SAO_PAULO)
        val database = testDatabase(clock)
        val repository = MoodRepository(database, io, clock, zones)
        val today = LocalDate(2026, 9, 12)

        repository.upsert(defaultMood.copy(date = today)).isSuccess shouldBe true
        val before = database.moodQueries.selectAll().executeAsList()

        repository.upsert(defaultMood.copy(date = today.plus(1, DateTimeUnit.DAY))).errorOrNull() shouldBe
            DomainError.Invalid("date", DomainError.Reason.IN_THE_FUTURE)

        database.moodQueries.selectAll().executeAsList() shouldBe before
    }

    test("observeAll, observeBetween and observe reflect writes; observe hides soft-deleted rows") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = MoodRepository(database, io, clock, FixedTimeZoneProvider())
        val date = LocalDate(2026, 9, 1)
        val created = repository.upsert(defaultMood.copy(date = date)).getOrNull().shouldNotBeNull()

        repository.observeAll().test { awaitItem().map { it.id } shouldBe listOf(created.id) }
        repository.observeBetween(date.minus(1, DateTimeUnit.DAY), date.plus(1, DateTimeUnit.DAY)).test {
            awaitItem().map { it.id } shouldBe listOf(created.id)
        }
        repository.observe(date).test {
            awaitItem()?.id shouldBe created.id
            repository.delete(created.id)
            awaitItem().shouldBeNull()
        }
        repository.observe(date.plus(5, DateTimeUnit.DAY)).test { awaitItem().shouldBeNull() }
    }

    test("delete then restore a mood log") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = MoodRepository(database, io, clock, FixedTimeZoneProvider())
        val created = repository.upsert(defaultMood.copy()).getOrNull().shouldNotBeNull()

        repository.delete(created.id).isSuccess shouldBe true
        database.moodQueries.selectById(created.id.toString()).executeAsOne().deleted_at.shouldNotBeNull()
        repository.restore(created.id).isSuccess shouldBe true
        database.moodQueries.selectById(created.id.toString()).executeAsOne().deleted_at.shouldBeNull()
    }
})
