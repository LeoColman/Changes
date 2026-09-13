// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import app.cash.turbine.test
import br.com.colman.changes.core.clinical.ExpectedChangeRules
import br.com.colman.changes.core.clinical.SourceKey
import br.com.colman.changes.core.db.sql.Body_change_entry
import br.com.colman.changes.core.db.sql.ChangesDatabase
import br.com.colman.changes.core.model.TreatmentProtocol
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.FixedTimeZoneProvider
import br.com.colman.changes.core.testing.shouldEqual
import br.com.colman.changes.core.testing.testDatabase
import br.com.colman.changes.core.testing.testDataset
import br.com.colman.changes.core.testing.testLabels
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Seção 7.2: linha do tempo de mudanças esperadas. */
class ExpectedChangeRepositorySpec : FunSpec({
    val io = UnconfinedTestDispatcher()
    lateinit var clock: FixedClock
    lateinit var database: ChangesDatabase
    lateinit var profiles: ProfileRepository
    lateinit var repository: ExpectedChangeRepository

    beforeTest {
        clock = FixedClock()
        database = testDatabase(clock)
        profiles = ProfileRepository(database, io, clock)
        repository = ExpectedChangeRepository(database, profiles, testLabels, clock, FixedTimeZoneProvider(), io)
    }

    val masculinizing = testDataset.expectedChanges.filter { it.protocol == TreatmentProtocol.MASCULINIZING }
    val observed = masculinizing.first()
    val observedType = testDataset.bodyChangeTypes.first { it.code == observed.changeTypeCode }.id.toString()

    fun entry(at: String, deletedAt: Long? = null) = Body_change_entry(
        id = Uuid.random().toString(),
        change_type_id = observedType,
        observed_at = Instant.parse(at).toEpochMilliseconds(),
        observed_at_offset_seconds = 0,
        intensity = 1,
        measurement_value = null,
        measurement_unit = null,
        notes = null,
        created_at = 1L,
        updated_at = 1L,
        deleted_at = deletedAt,
    )

    test("criterion 7.2.1: without an HRT start date every change is listed, with no relative state") {
        val timeline = repository.observeTimeline().first()
        timeline.hrtStart.shouldBeNull()
        timeline.monthsOnTreatment.shouldBeNull()
        timeline.items.map { it.timeline.change }.toSet() shouldEqual masculinizing.toSet()
        timeline.items.map { it.timeline.status }.toSet() shouldBe setOf(null)
    }

    test("with a start date, months and status count up to today in the device's time zone") {
        clock.now = Instant.parse("2026-09-12T02:00:00Z") // ainda 11 de setembro em São Paulo
        val start = LocalDate(2026, 3, 11)
        profiles.update { it.copy(hrtStartDate = start) }
        val today = LocalDate(2026, 9, 11)
        val timeline = repository.observeTimeline().first()
        timeline.hrtStart shouldBe start
        timeline.monthsOnTreatment shouldBe ExpectedChangeRules.monthsBetween(start, today)
        timeline.items.map {
            it.timeline
        }.toSet() shouldEqual ExpectedChangeRules.timeline(masculinizing, start, today, emptyMap()).toSet()
    }

    test("the first observation is the earliest entry not in the trash, as a local date of the device") {
        database.bodyChangeQueries.insertEntry(entry("2026-04-01T12:00:00Z", deletedAt = 5L))
        database.bodyChangeQueries.insertEntry(entry("2026-06-01T12:00:00Z"))
        database.bodyChangeQueries.insertEntry(entry("2026-05-01T01:00:00Z"))
        profiles.update { it.copy(hrtStartDate = LocalDate(2026, 1, 1)) }
        val items = repository.observeTimeline().first().items
        val firsts = items.associate { it.timeline.change.changeTypeCode to it.timeline.firstObserved }
        firsts.getValue(observed.changeTypeCode) shouldBe LocalDate(2026, 4, 30)
        (firsts - observed.changeTypeCode).values.toSet() shouldBe setOf(null)
    }

    test("the timeline follows new observations as they are recorded") {
        repository.observeTimeline().test {
            awaitItem().items.map { it.timeline.firstObserved }.toSet() shouldBe setOf(null)
            database.bodyChangeQueries.insertEntry(entry("2026-07-10T15:00:00Z"))
            val item = awaitItem().items.single { it.timeline.change.changeTypeCode == observed.changeTypeCode }
            item.timeline.firstObserved shouldBe LocalDate(2026, 7, 10)
            cancelAndIgnoreRemainingEvents()
        }
    }

    test("criterion 7.2.3: each item cites its source, and its permanence source when the dataset has one") {
        val items = repository.observeTimeline().first().items
        items.forEach { item ->
            item.sourceCitation shouldBe testLabels.reference(item.timeline.change.source)
            item.permanenceCitation shouldBe item.timeline.change.permanenceSource?.let(testLabels::reference)
        }
        items.any { it.permanenceCitation != null } shouldBe true
    }

    test("a protocol without rows in the dataset has an empty timeline") {
        profiles.update { it.copy(treatmentProtocol = TreatmentProtocol("OTHER")) }
        repository.observeTimeline().first().items.shouldBeEmpty()
    }

    test("references cite every source of the dataset") {
        val references = repository.references()
        references shouldBe SourceKey.entries.associateWith { testLabels.reference(it) }
        references.values.forEach { it.shouldNotBeBlank() }
    }
})
