// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.expected

import app.cash.turbine.test
import br.com.colman.changes.core.clinical.SourceKey
import br.com.colman.changes.core.data.ExpectedChangeRepository
import br.com.colman.changes.core.data.ProfileRepository
import br.com.colman.changes.core.db.sql.Body_change_entry
import br.com.colman.changes.core.model.BodyRegion
import br.com.colman.changes.core.model.BodyVocabulary
import br.com.colman.changes.core.model.TreatmentProtocol
import br.com.colman.changes.core.model.VocabularyChoice
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.FixedTimeZoneProvider
import br.com.colman.changes.core.testing.MainDispatcherListener
import br.com.colman.changes.core.testing.testDatabase
import br.com.colman.changes.core.testing.testDataset
import br.com.colman.changes.core.testing.testLabels
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.uuid.Uuid

private const val TOLERANCE = 0.001

/** Repositórios reais sobre um banco de teste em memória, para os specs desta feature (Seção 11). */
private class ExpectedChangesFixture {
    val io = UnconfinedTestDispatcher()
    val clock = FixedClock()
    val zones = FixedTimeZoneProvider()
    val database = testDatabase(clock)
    val profiles = ProfileRepository(database, io, clock)
    val repository = ExpectedChangeRepository(database, profiles, testLabels, clock, zones, io)
    val viewModel = ExpectedChangesViewModel(repository, profiles, testLabels)
}

private val masculinizingCodes = testDataset.expectedChanges
    .filter { it.protocol == TreatmentProtocol.MASCULINIZING }
    .map { it.changeTypeCode }
    .toSet()

/** Uma entrada de mudança corporal registrada em [at], para o tipo builtin de [code]. */
private fun entryFor(code: String, at: String) = Body_change_entry(
    id = Uuid.random().toString(),
    change_type_id = testDataset.bodyChangeTypes.first { it.code == code }.id.toString(),
    observed_at = Instant.parse(at).toEpochMilliseconds(),
    observed_at_offset_seconds = 0,
    intensity = 1,
    measurement_value = null,
    measurement_unit = null,
    notes = null,
    created_at = 1L,
    updated_at = 1L,
    deleted_at = null,
)

/** Seção 7.2: linha do tempo de mudanças esperadas, na tela. */
class ExpectedChangesViewModelSpec : FunSpec({
    register(MainDispatcherListener())

    test("criterion 7.2.1: without a start date every item of the protocol appears with no relative state") {
        val fixture = ExpectedChangesFixture()

        fixture.viewModel.state.test {
            val state = awaitItem()
            state.hrtStart.shouldBeNull()
            state.items.map { it.changeTypeCode }.toSet() shouldBe masculinizingCodes
            state.items.map { it.status }.toSet() shouldBe setOf(null)
        }
    }

    test("criterion 7.2.1: with a start date every item has a relative state") {
        val fixture = ExpectedChangesFixture()
        fixture.profiles.update { it.copy(hrtStartDate = LocalDate(2026, 1, 1)) }

        fixture.viewModel.state.test {
            val state = awaitItem()
            state.hrtStart shouldBe LocalDate(2026, 1, 1)
            state.items.shouldNotBeEmpty()
            state.items.forEach { it.status.shouldNotBeNull() }
            state.items.forEach { it.windowStart.shouldNotBeNull() }
        }
    }

    test("criterion 7.2.3: every rendered item has a non-blank source citation") {
        val fixture = ExpectedChangesFixture()

        fixture.viewModel.state.test {
            val items = awaitItem().items
            items.shouldNotBeEmpty()
            items.forEach { it.sourceCitation.shouldNotBeBlank() }
        }
    }

    test("the references section lists every source with a non-blank citation") {
        val fixture = ExpectedChangesFixture()

        fixture.viewModel.state.test {
            val references = awaitItem().references
            references.map { it.source }.toSet() shouldBe SourceKey.entries.toSet()
            references.forEach { it.citation.shouldNotBeBlank() }
        }
    }

    test("the first observation appears on the item when there is a matching entry") {
        val fixture = ExpectedChangesFixture()
        val code = masculinizingCodes.first()
        fixture.database.bodyChangeQueries.insertEntry(entryFor(code, "2026-04-01T12:00:00Z"))

        fixture.viewModel.state.test {
            val item = awaitItem().items.single { it.changeTypeCode == code }
            item.firstObserved.shouldNotBeNull()
        }
    }

    test("the change name changes when the profile's body vocabulary changes") {
        val fixture = ExpectedChangesFixture()
        fixture.profiles.update { it.copy(locale = "pt-BR") }

        fixture.viewModel.state.test {
            val before = awaitItem().items.single { it.changeTypeCode == "CLITORAL_ENLARGEMENT" }.name

            fixture.profiles.update {
                it.copy(bodyVocabulary = BodyVocabulary().with(BodyRegion.GENITAL, VocabularyChoice.Preset("CLITORIS")))
            }

            val after = awaitItem().items.single { it.changeTypeCode == "CLITORAL_ENLARGEMENT" }.name
            after shouldNotBe before
        }
    }

    test("a range above 12 months is presented in years") {
        val fixture = ExpectedChangesFixture()

        fixture.viewModel.state.test {
            val item = awaitItem().items.single { it.changeTypeCode == "FACIAL_BODY_HAIR" }
            item.onsetRange.unit shouldBe ExpectedRangeUnit.MONTHS
            val range = item.maxEffectRange.shouldNotBeNull()
            range.unit shouldBe ExpectedRangeUnit.YEARS
            range.min shouldBe (4.0 plusOrMinus TOLERANCE)
            range.max shouldBe (5.0 plusOrMinus TOLERANCE)
        }
    }

    test("a range exactly at 12 months converts to whole years") {
        val fixture = ExpectedChangesFixture()

        fixture.viewModel.state.test {
            val item = awaitItem().items.single { it.changeTypeCode == "SKIN_OILINESS_ACNE" }
            val range = item.maxEffectRange.shouldNotBeNull()
            range.unit shouldBe ExpectedRangeUnit.YEARS
            range.min shouldBe (1.0 plusOrMinus TOLERANCE)
            range.max shouldBe (2.0 plusOrMinus TOLERANCE)
        }
    }

    test("a max effect the source does not state is null in the item state") {
        val fixture = ExpectedChangesFixture()

        fixture.viewModel.state.test {
            val item = awaitItem().items.single { it.changeTypeCode == "SCALP_HAIR_LOSS" }
            item.maxEffectRange.shouldBeNull()
        }
    }
})
