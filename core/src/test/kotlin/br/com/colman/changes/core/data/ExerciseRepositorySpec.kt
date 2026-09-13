// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import app.cash.turbine.test
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.ExerciseIntensity
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
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class ExerciseRepositorySpec : FunSpec({
    val io = UnconfinedTestDispatcher()

    test("create stores a session with the recorded offset from the current zone") {
        val clock = FixedClock()
        val zones = FixedTimeZoneProvider(TestZones.SAO_PAULO)
        val database = testDatabase(clock)
        val repository = ExerciseRepository(database, io, clock, zones)

        val result = repository.create("corrida", 45, ExerciseIntensity.VIGOROUS, clock.now, "leve")
        result.isSuccess shouldBe true
        val created = result.getOrNull().shouldNotBeNull()

        val row = database.exerciseQueries.selectById(created.id.toString()).executeAsOne()
        row.activity shouldBe "corrida"
        row.duration_minutes shouldBe 45L
        row.intensity shouldBe "VIGOROUS"
        row.occurred_at shouldBe clock.now.toEpochMilliseconds()
        row.occurred_at_offset_seconds shouldBe created.occurredAt.offsetSeconds
        row.toModel() shouldEqual created
    }

    test("create rejects a blank activity, a non-positive duration and a future date; writes nothing") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = ExerciseRepository(database, io, clock, FixedTimeZoneProvider())

        repository.create("", 30, ExerciseIntensity.LIGHT, clock.now, null).errorOrNull() shouldBe
            DomainError.Invalid("activity", DomainError.Reason.REQUIRED)
        repository.create("x", 0, ExerciseIntensity.LIGHT, clock.now, null).errorOrNull() shouldBe
            DomainError.Invalid("durationMinutes", DomainError.Reason.NOT_POSITIVE)
        repository.create("x", 30, ExerciseIntensity.LIGHT, clock.now + 1.minutes, null).errorOrNull() shouldBe
            DomainError.Invalid("occurredAt", DomainError.Reason.IN_THE_FUTURE)
        database.exerciseQueries.selectAll().executeAsList().shouldContainExactly(emptyList())
    }

    test("update validates the same rules and a rejection writes nothing") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = ExerciseRepository(database, io, clock, FixedTimeZoneProvider())
        val created = repository.create(
            "corrida",
            30,
            ExerciseIntensity.MODERATE,
            clock.now,
            null
        ).getOrNull().shouldNotBeNull()

        val updated = created.copy(durationMinutes = 60, notes = "novo")
        repository.update(updated).isSuccess shouldBe true
        database.exerciseQueries.selectById(created.id.toString()).executeAsOne().toModel() shouldEqual updated

        repository.update(updated.copy(durationMinutes = 0)).errorOrNull() shouldBe
            DomainError.Invalid("durationMinutes", DomainError.Reason.NOT_POSITIVE)
        database.exerciseQueries.selectById(created.id.toString()).executeAsOne().toModel() shouldEqual updated
    }

    test("observeAll and observeBetween reflect writes") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = ExerciseRepository(database, io, clock, FixedTimeZoneProvider())
        val created = repository.create(
            "corrida",
            30,
            ExerciseIntensity.MODERATE,
            clock.now,
            null
        ).getOrNull().shouldNotBeNull()

        repository.observeAll().test { awaitItem().map { it.id } shouldContain created.id }
        repository.observeBetween(clock.now - 1.days, clock.now + 1.days).test {
            awaitItem().map { it.id } shouldContain created.id
        }
        repository.observeBetween(clock.now + 1.days, clock.now + 2.days).test { awaitItem() shouldBe emptyList() }
    }

    test("delete then restore a session") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = ExerciseRepository(database, io, clock, FixedTimeZoneProvider())
        val created = repository.create(
            "corrida",
            30,
            ExerciseIntensity.MODERATE,
            clock.now,
            null
        ).getOrNull().shouldNotBeNull()

        repository.delete(created.id).isSuccess shouldBe true
        database.exerciseQueries.selectById(created.id.toString()).executeAsOne().deleted_at.shouldNotBeNull()
        repository.restore(created.id).isSuccess shouldBe true
        database.exerciseQueries.selectById(created.id.toString()).executeAsOne().deleted_at.shouldBeNull()
    }

    test("weeklyMinutes buckets by the ISO week (Monday) of each session's local date, zero-filling empty weeks") {
        val clock = FixedClock()
        val zone = TestZones.SAO_PAULO
        val database = testDatabase(clock)
        val repository = ExerciseRepository(database, io, clock, FixedTimeZoneProvider(zone))
        // Uma sessão bem antes da janela pedida: não pode contaminar nenhum balde.
        repository.create("antiga", 999, ExerciseIntensity.LIGHT, LocalDate(2026, 1, 1).atStartOfDayIn(zone), null)
        // Segunda 2026-02-02: semana 1. Sem sessão na semana 2 (09 a 15). Quarta 2026-02-18: semana 3.
        repository.create("a", 30, ExerciseIntensity.LIGHT, LocalDate(2026, 2, 2).atStartOfDayIn(zone), null)
        repository.create("b", 20, ExerciseIntensity.LIGHT, LocalDate(2026, 2, 4).atStartOfDayIn(zone), null)
        repository.create("c", 45, ExerciseIntensity.MODERATE, LocalDate(2026, 2, 18).atStartOfDayIn(zone), null)

        val result = repository.weeklyMinutes(LocalDate(2026, 2, 20), 3)

        result shouldContainExactly listOf(
            WeekMinutes(LocalDate(2026, 2, 2), 50),
            WeekMinutes(LocalDate(2026, 2, 9), 0),
            WeekMinutes(LocalDate(2026, 2, 16), 45),
        )
    }

    test("weeklyMinutes with a single week returns just the current week") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = ExerciseRepository(database, io, clock, FixedTimeZoneProvider())

        repository.weeklyMinutes(LocalDate(2026, 9, 14), 1) shouldContainExactly listOf(WeekMinutes(LocalDate(2026, 9, 14), 0))
    }
})
