// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.medication

import app.cash.turbine.test
import br.com.colman.changes.core.data.DoseLogRepository
import br.com.colman.changes.core.data.MedicationRepository
import br.com.colman.changes.core.data.NewDoseLog
import br.com.colman.changes.core.data.NewRegimen
import br.com.colman.changes.core.data.RegimenRepository
import br.com.colman.changes.core.model.Dose
import br.com.colman.changes.core.model.DoseSchedule
import br.com.colman.changes.core.model.DoseUnit
import br.com.colman.changes.core.model.Route
import br.com.colman.changes.core.model.Schedule
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.FixedTimeZoneProvider
import br.com.colman.changes.core.testing.MainDispatcherListener
import br.com.colman.changes.core.testing.testDatabase
import br.com.colman.changes.core.testing.testDataset
import br.com.colman.changes.core.testing.testLabels
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlin.time.Duration.Companion.days

class DoseHistoryViewModelSpec : FunSpec({
    register(MainDispatcherListener())
    val io = UnconfinedTestDispatcher()
    fun medicationId() = testDataset.medications.first { it.key == "DEPOSTERON" }.id
    fun otherMedicationId() = testDataset.medications.first { it.key != "DEPOSTERON" }.id

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

    fun viewModel(regimens: RegimenRepository, medications: MedicationRepository, doseLogs: DoseLogRepository, clock: FixedClock) =
        DoseHistoryViewModel(
            doseLogs,
            regimens,
            medications,
            clock,
            FixedTimeZoneProvider(),
            MedicationDisplayNames(testDataset, testLabels)
        )

    test("criterion 7.1.2: editing a regimen's dose does not change doses already logged, seen in the history") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val doseLogs = DoseLogRepository(database, io, clock, FixedTimeZoneProvider())
        val medications = MedicationRepository(database, io, clock)
        val regimen = regimens.create(newRegimen()).getOrNull().shouldNotBeNull()
        val log = doseLogs.logFromRegimen(regimen.id, clock.now, null).getOrNull().shouldNotBeNull()

        regimens.update(regimen.copy(dose = Dose(250.0, DoseUnit.MG))).getOrNull().shouldNotBeNull()

        viewModel(regimens, medications, doseLogs, clock).state.test {
            val entry = awaitItem().monthGroups.single().entries.single()
            entry.log.id shouldBe log.id
            entry.log.dose shouldBe Dose(100.0, DoseUnit.MG)
        }
    }

    test("criterion 7.1.4: deleting a regimen keeps its doses in the history") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val doseLogs = DoseLogRepository(database, io, clock, FixedTimeZoneProvider())
        val medications = MedicationRepository(database, io, clock)
        val regimen = regimens.create(newRegimen()).getOrNull().shouldNotBeNull()
        val log = doseLogs.logFromRegimen(regimen.id, clock.now, null).getOrNull().shouldNotBeNull()

        regimens.delete(regimen.id)

        viewModel(regimens, medications, doseLogs, clock).state.test {
            val state = awaitItem()
            state.monthGroups.single().entries.single().log.id shouldBe log.id
            state.adherenceSentences.shouldBeEmpty()
        }
    }

    test("entries are grouped by month, most recent month first") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val doseLogs = DoseLogRepository(database, io, clock, FixedTimeZoneProvider())
        val medications = MedicationRepository(database, io, clock)
        doseLogs.log(
            NewDoseLog(null, medicationId(), Dose(1.0, DoseUnit.MG), Route.ORAL, null, clock.now - 60.days, null),
        )
        doseLogs.log(
            NewDoseLog(null, medicationId(), Dose(1.0, DoseUnit.MG), Route.ORAL, null, clock.now, null),
        )

        viewModel(regimens, medications, doseLogs, clock).state.test {
            val groups = awaitItem().monthGroups
            groups shouldHaveSize 2
            groups.map { it.key } shouldBe groups.map { it.key }.sortedDescending()
        }
    }

    test("the adherence sentence reflects registered versus expected doses for an active regimen") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val doseLogs = DoseLogRepository(database, io, clock, FixedTimeZoneProvider())
        val medications = MedicationRepository(database, io, clock)
        val regimen = regimens.create(newRegimen()).getOrNull().shouldNotBeNull()
        doseLogs.logFromRegimen(regimen.id, clock.now, null)
        val today = LocalDate(2026, 9, 12)
        val expected = DoseSchedule.occurrences(
            regimen,
            today.minus(89, DateTimeUnit.DAY),
            today,
        ).size

        viewModel(regimens, medications, doseLogs, clock).state.test {
            val summary = awaitItem().adherenceSentences.single()
            summary.regimenId shouldBe regimen.id.toString()
            summary.registered shouldBe 1
            summary.expected shouldBe expected
            summary.windowDays shouldBe 90
        }
    }

    test("deleting an entry removes it from the history, and undo brings it back") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val doseLogs = DoseLogRepository(database, io, clock, FixedTimeZoneProvider())
        val medications = MedicationRepository(database, io, clock)
        val log = doseLogs.log(
            NewDoseLog(null, medicationId(), Dose(1.0, DoseUnit.MG), Route.ORAL, null, clock.now, null),
        ).getOrNull().shouldNotBeNull()
        val viewModel = viewModel(regimens, medications, doseLogs, clock)

        viewModel.state.test {
            awaitItem().monthGroups.shouldHaveSize(1)

            viewModel.onEvent(DoseHistoryUiEvent.RequestDelete(log.id.toString()))
            awaitItem().monthGroups.shouldBeEmpty()

            viewModel.onEvent(DoseHistoryUiEvent.Undo(log.id.toString()))
            awaitItem().monthGroups.shouldHaveSize(1)
        }
    }

    test("filtering by medication narrows the history to that medication only") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val doseLogs = DoseLogRepository(database, io, clock, FixedTimeZoneProvider())
        val medications = MedicationRepository(database, io, clock)
        doseLogs.log(
            NewDoseLog(null, medicationId(), Dose(1.0, DoseUnit.MG), Route.ORAL, null, clock.now, null),
        )
        doseLogs.log(
            NewDoseLog(null, otherMedicationId(), Dose(1.0, DoseUnit.MG), Route.ORAL, null, clock.now, null),
        )
        val viewModel = viewModel(regimens, medications, doseLogs, clock)

        viewModel.state.test {
            awaitItem().monthGroups.single().entries.shouldHaveSize(2)

            viewModel.onEvent(DoseHistoryUiEvent.FilterByMedication(medicationId().toString()))
            awaitItem().monthGroups.single().entries.shouldHaveSize(1)
        }
    }
})
