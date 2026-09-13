// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.medication

import app.cash.turbine.test
import br.com.colman.changes.core.data.MedicationRepository
import br.com.colman.changes.core.data.NewRegimen
import br.com.colman.changes.core.data.RegimenRepository
import br.com.colman.changes.core.model.Dose
import br.com.colman.changes.core.model.DoseUnit
import br.com.colman.changes.core.model.Route
import br.com.colman.changes.core.model.Schedule
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.FixedTimeZoneProvider
import br.com.colman.changes.core.testing.MainDispatcherListener
import br.com.colman.changes.core.testing.TestZones
import br.com.colman.changes.core.testing.testDatabase
import br.com.colman.changes.core.testing.testDataset
import br.com.colman.changes.core.testing.testLabels
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class RegimenListViewModelSpec : FunSpec({
    register(MainDispatcherListener())
    val io = UnconfinedTestDispatcher()
    fun medicationId() = testDataset.medications.first { it.key == "DEPOSTERON" }.id

    fun newRegimen(startDate: LocalDate = LocalDate(2026, 1, 1)) = NewRegimen(
        medicationId(),
        Dose(100.0, DoseUnit.MG),
        Route.INTRAMUSCULAR,
        Schedule.IntervalDays(14),
        LocalTime(8, 0),
        startDate,
        null,
        null,
    )

    test("active regimens come before ended ones, each with a medication name and a next dose") {
        val clock = FixedClock()
        val zones = FixedTimeZoneProvider(TestZones.SAO_PAULO)
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val medications = MedicationRepository(database, io, clock)
        val names = MedicationDisplayNames(testDataset, testLabels)
        val active = regimens.create(newRegimen()).getOrNull().shouldNotBeNull()
        val ended = regimens.create(newRegimen(startDate = LocalDate(2025, 1, 1))).getOrNull().shouldNotBeNull()
        regimens.update(ended.copy(isActive = false))

        val viewModel = RegimenListViewModel(regimens, medications, clock, zones, names)

        viewModel.state.test {
            val state = awaitItem()
            state.activeRegimens.map { it.regimen.id } shouldBe listOf(active.id)
            state.endedRegimens.map { it.regimen.id } shouldBe listOf(ended.id)
            val medication = medications.get(medicationId()).shouldNotBeNull()
            state.activeRegimens.first().medicationName shouldBe names.name(medication)
            state.activeRegimens.first().nextDose.shouldNotBeNull()
            state.endedRegimens.first().nextDose shouldBe null
        }
    }

    test("an as-needed schedule never has a next dose, even while active") {
        val clock = FixedClock()
        val zones = FixedTimeZoneProvider()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val medications = MedicationRepository(database, io, clock)
        val names = MedicationDisplayNames(testDataset, testLabels)
        regimens.create(newRegimen().copy(schedule = Schedule.AsNeeded))

        val viewModel = RegimenListViewModel(regimens, medications, clock, zones, names)

        viewModel.state.test {
            awaitItem().activeRegimens.first().nextDose shouldBe null
        }
    }

    test("with no regimens, both lists are empty") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val medications = MedicationRepository(database, io, clock)
        val names = MedicationDisplayNames(testDataset, testLabels)

        val viewModel = RegimenListViewModel(regimens, medications, clock, FixedTimeZoneProvider(), names)

        viewModel.state.test {
            val state = awaitItem()
            state.activeRegimens.shouldBeEmpty()
            state.endedRegimens.shouldBeEmpty()
        }
    }

    test("onEvent turns every navigation intent into the matching effect") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val medications = MedicationRepository(database, io, clock)
        val names = MedicationDisplayNames(testDataset, testLabels)
        val viewModel = RegimenListViewModel(regimens, medications, clock, FixedTimeZoneProvider(), names)

        viewModel.effects.test {
            viewModel.onEvent(RegimenListUiEvent.OpenRegimen("abc"))
            awaitItem() shouldBe RegimenListEffect.NavigateToRegimen("abc")
            viewModel.onEvent(RegimenListUiEvent.CreateRegimen)
            awaitItem() shouldBe RegimenListEffect.NavigateToCreate
            viewModel.onEvent(RegimenListUiEvent.OpenHistory)
            awaitItem() shouldBe RegimenListEffect.NavigateToHistory
            viewModel.onEvent(RegimenListUiEvent.Back)
            awaitItem() shouldBe RegimenListEffect.NavigateBack
        }
    }
})
