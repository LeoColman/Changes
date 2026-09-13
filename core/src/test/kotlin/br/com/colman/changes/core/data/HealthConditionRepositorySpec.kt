// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import app.cash.turbine.test
import br.com.colman.changes.core.model.ConditionSeverity
import br.com.colman.changes.core.model.ConditionStatus
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.HealthCondition
import br.com.colman.changes.core.model.errorOrNull
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.core.model.isSuccess
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.shouldEqual
import br.com.colman.changes.core.testing.testDatabase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.LocalDate
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class HealthConditionRepositorySpec : FunSpec({
    val io = UnconfinedTestDispatcher()

    fun draft(
        label: String = "Hipertensão",
        diagnosedAt: LocalDate? = LocalDate(2020, 1, 1),
        resolvedAt: LocalDate? = null,
        affectsTreatment: Boolean = true,
    ) = HealthCondition(
        Uuid.random(), label, "I10", ConditionSeverity.MODERATE, ConditionStatus.ACTIVE, diagnosedAt, resolvedAt, affectsTreatment, "n"
    )

    test("create stores the condition with a fresh id and exact timestamps, ignoring draft.id") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = HealthConditionRepository(database, io, clock)
        val input = draft()

        val result = repository.create(input)
        result.isSuccess shouldBe true
        val created = result.getOrNull().shouldNotBeNull()
        created.id shouldNotBe input.id

        val row = database.healthConditionQueries.selectById(created.id.toString()).executeAsOne()
        row.label shouldBe "Hipertensão"
        row.code shouldBe "I10"
        row.severity shouldBe "MODERATE"
        row.status shouldBe "ACTIVE"
        row.diagnosed_at shouldBe LocalDate(2020, 1, 1).toEpochDays()
        row.resolved_at.shouldBeNull()
        row.affects_treatment shouldBe 1L
        row.created_at shouldBe clock.now.toEpochMilliseconds()
        row.toModel() shouldEqual created
    }

    test("create rejects a blank label and writes nothing") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = HealthConditionRepository(database, io, clock)

        repository.create(draft(label = "  ")).errorOrNull() shouldBe DomainError.Invalid("label", DomainError.Reason.REQUIRED)
        database.healthConditionQueries.selectAll().executeAsList().shouldContainExactly(emptyList())
    }

    test("resolvedAt must not be before diagnosedAt; equal dates are accepted") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = HealthConditionRepository(database, io, clock)

        repository.create(draft(diagnosedAt = LocalDate(2020, 6, 1), resolvedAt = LocalDate(2020, 5, 31))).errorOrNull() shouldBe
            DomainError.Invalid("resolvedAt", DomainError.Reason.END_BEFORE_START)
        repository.create(draft(diagnosedAt = LocalDate(2020, 6, 1), resolvedAt = LocalDate(2020, 6, 1))).isSuccess shouldBe true
        repository.create(draft(diagnosedAt = null, resolvedAt = LocalDate(2020, 6, 1))).isSuccess shouldBe true
        repository.create(draft(diagnosedAt = LocalDate(2020, 6, 1), resolvedAt = null)).isSuccess shouldBe true
    }

    test("observeAll lists affectsTreatment conditions first, as guaranteed by the query") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = HealthConditionRepository(database, io, clock)
        val minor = repository.create(
            draft(label = "Enxaqueca", affectsTreatment = false)
        ).getOrNull().shouldNotBeNull()
        val major = repository.create(draft(label = "TVP", affectsTreatment = true)).getOrNull().shouldNotBeNull()

        repository.observeAll().test {
            val ids = awaitItem().map { it.id }
            ids.indexOf(major.id) shouldBe 0
            ids shouldContainExactly listOf(major.id, minor.id)
        }
    }

    test("update changes fields and rejects the same invalid cases; a rejection writes nothing") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = HealthConditionRepository(database, io, clock)
        val created = repository.create(draft()).getOrNull().shouldNotBeNull()
        clock.advance(1.minutes)

        val updated = created.copy(label = "Novo rótulo", severity = ConditionSeverity.SEVERE)
        repository.update(updated).isSuccess shouldBe true
        val row = database.healthConditionQueries.selectById(created.id.toString()).executeAsOne()
        row.label shouldBe "Novo rótulo"
        row.severity shouldBe "SEVERE"
        row.updated_at shouldBe clock.now.toEpochMilliseconds()

        repository.update(updated.copy(label = "")).errorOrNull() shouldBe DomainError.Invalid("label", DomainError.Reason.REQUIRED)
        database.healthConditionQueries.selectById(created.id.toString()).executeAsOne().toModel() shouldEqual updated
    }

    test("delete then restore a condition") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val repository = HealthConditionRepository(database, io, clock)
        val created = repository.create(draft()).getOrNull().shouldNotBeNull()

        repository.delete(created.id).isSuccess shouldBe true
        database.healthConditionQueries.selectById(created.id.toString()).executeAsOne().deleted_at.shouldNotBeNull()
        repository.restore(created.id).isSuccess shouldBe true
        database.healthConditionQueries.selectById(created.id.toString()).executeAsOne().deleted_at.shouldBeNull()
    }
})
