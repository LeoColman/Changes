// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.medication

import app.cash.turbine.test
import br.com.colman.changes.core.data.DoseLogRepository
import br.com.colman.changes.core.data.MedicationRepository
import br.com.colman.changes.core.data.NewDoseLog
import br.com.colman.changes.core.data.NewRegimen
import br.com.colman.changes.core.data.RegimenRepository
import br.com.colman.changes.core.model.DomainError
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
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

class LogDoseViewModelSpec : FunSpec({
    register(MainDispatcherListener())
    val io = UnconfinedTestDispatcher()
    fun medicationId() = testDataset.medications.first { it.key == "DEPOSTERON" }.id

    fun history(repository: DoseLogRepository, clock: FixedClock) =
        repository.observeHistory(null, clock.now - 3650.days, clock.now + 3650.days)

    fun newViewModel(
        doseLogs: DoseLogRepository,
        regimens: RegimenRepository,
        medications: MedicationRepository,
        clock: FixedClock,
        zones: FixedTimeZoneProvider,
    ) = LogDoseViewModel(doseLogs, regimens, medications, MedicationDisplayNames(testDataset, testLabels), clock, zones)

    test("prefilling from a regimen copies dose, route and medication, and suggests a site only for injections") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val doseLogs = DoseLogRepository(database, io, clock, FixedTimeZoneProvider())
        val medications = MedicationRepository(database, io, clock)
        val injectable = regimens.create(
            NewRegimen(
                medicationId(),
                Dose(0.25, DoseUnit.ML),
                Route.SUBCUTANEOUS,
                Schedule.IntervalDays(7),
                LocalTime(8, 0),
                LocalDate(2026, 1, 1),
                null,
                null,
            ),
        ).getOrNull().shouldNotBeNull()
        val viewModel = newViewModel(doseLogs, regimens, medications, clock, FixedTimeZoneProvider())

        viewModel.onEvent(LogDoseUiEvent.Load(injectable.id.toString(), null, null))

        val state = viewModel.state.value
        state.selectedMedicationId shouldBe injectable.medicationId.toString()
        state.doseUnit shouldBe DoseUnit.ML
        state.route shouldBe Route.SUBCUTANEOUS
        state.medicationEditable shouldBe false
        state.injectionSite.shouldNotBeNull()
    }

    test("prefilling from a non-injectable regimen suggests no injection site") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val doseLogs = DoseLogRepository(database, io, clock, FixedTimeZoneProvider())
        val medications = MedicationRepository(database, io, clock)
        val oral = regimens.create(
            NewRegimen(
                medicationId(),
                Dose(1.0, DoseUnit.MG),
                Route.ORAL,
                Schedule.IntervalDays(1),
                null,
                LocalDate(2026, 1, 1),
                null,
                null
            ),
        ).getOrNull().shouldNotBeNull()
        val viewModel = newViewModel(doseLogs, regimens, medications, clock, FixedTimeZoneProvider())

        viewModel.onEvent(LogDoseUiEvent.Load(oral.id.toString(), null, null))

        viewModel.state.value.injectionSite.shouldBeNull()
    }

    test("criterion 7.1.3: a future date and time is rejected, and nothing is written") {
        val clock = FixedClock()
        val zones = FixedTimeZoneProvider(TestZones.SAO_PAULO)
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val doseLogs = DoseLogRepository(database, io, clock, zones)
        val medications = MedicationRepository(database, io, clock)
        val viewModel = newViewModel(doseLogs, regimens, medications, clock, zones)
        viewModel.onEvent(LogDoseUiEvent.Load(null, null, null))
        val medicationOption = viewModel.state.value.availableMedications.first { it.id == medicationId().toString() }
        val future = (clock.now + 90.minutes).toLocalDateTime(zones.current())
        viewModel.onEvent(
            LogDoseUiEvent.FieldChanged {
                it.copy(
                    selectedMedicationId = medicationOption.id,
                    doseValue = "1",
                    date = future.date,
                    time = future.time,
                )
            },
        )

        viewModel.onEvent(LogDoseUiEvent.Save)

        viewModel.state.value.error shouldBe DomainError.Invalid("takenAt", DomainError.Reason.IN_THE_FUTURE)
        history(doseLogs, clock).test { awaitItem().shouldBeEmpty() }
    }

    test("a standalone log keeps the medication editable; one linked to a regimen keeps it fixed") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val doseLogs = DoseLogRepository(database, io, clock, FixedTimeZoneProvider())
        val medications = MedicationRepository(database, io, clock)
        val regimen = regimens.create(
            NewRegimen(
                medicationId(),
                Dose(1.0, DoseUnit.MG),
                Route.ORAL,
                Schedule.IntervalDays(1),
                null,
                LocalDate(2026, 1, 1),
                null,
                null
            ),
        ).getOrNull().shouldNotBeNull()

        val standalone = newViewModel(doseLogs, regimens, medications, clock, FixedTimeZoneProvider())
        standalone.onEvent(LogDoseUiEvent.Load(null, null, null))
        standalone.state.value.medicationEditable shouldBe true

        val fromRegimen = newViewModel(doseLogs, regimens, medications, clock, FixedTimeZoneProvider())
        fromRegimen.onEvent(LogDoseUiEvent.Load(regimen.id.toString(), null, null))
        fromRegimen.state.value.medicationEditable shouldBe false
    }

    test(
        "hasUnsavedChanges is false right after loading, true after a field changes, and false again after saving"
    ) {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val doseLogs = DoseLogRepository(database, io, clock, FixedTimeZoneProvider())
        val medications = MedicationRepository(database, io, clock)
        val created = doseLogs.log(
            NewDoseLog(null, medicationId(), Dose(1.0, DoseUnit.MG), Route.ORAL, null, clock.now, "original"),
        ).getOrNull().shouldNotBeNull()
        val viewModel = newViewModel(doseLogs, regimens, medications, clock, FixedTimeZoneProvider())

        viewModel.onEvent(LogDoseUiEvent.Load(null, null, created.id.toString()))
        viewModel.state.value.hasUnsavedChanges shouldBe false

        viewModel.onEvent(LogDoseUiEvent.FieldChanged { it.copy(notes = "editado") })
        viewModel.state.value.hasUnsavedChanges shouldBe true

        viewModel.effects.test {
            viewModel.onEvent(LogDoseUiEvent.Save)
            awaitItem() shouldBe LogDoseEffect.NavigateBack
        }
        viewModel.state.value.hasUnsavedChanges shouldBe false
    }

    test("on a new dose log hasUnsavedChanges is false untouched, and true once the first field is filled") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val doseLogs = DoseLogRepository(database, io, clock, FixedTimeZoneProvider())
        val medications = MedicationRepository(database, io, clock)
        val viewModel = newViewModel(doseLogs, regimens, medications, clock, FixedTimeZoneProvider())

        viewModel.onEvent(LogDoseUiEvent.Load(null, null, null))
        viewModel.state.value.hasUnsavedChanges shouldBe false

        viewModel.onEvent(LogDoseUiEvent.FieldChanged { it.copy(doseValue = "1") })
        viewModel.state.value.hasUnsavedChanges shouldBe true
    }

    test("editing an existing log loads its fields and updates it in place, and can be deleted") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val doseLogs = DoseLogRepository(database, io, clock, FixedTimeZoneProvider())
        val medications = MedicationRepository(database, io, clock)
        val created = doseLogs.log(
            NewDoseLog(null, medicationId(), Dose(1.0, DoseUnit.MG), Route.ORAL, null, clock.now, "original"),
        ).getOrNull().shouldNotBeNull()
        val viewModel = newViewModel(doseLogs, regimens, medications, clock, FixedTimeZoneProvider())

        viewModel.onEvent(LogDoseUiEvent.Load(null, null, created.id.toString()))
        viewModel.state.value.isEditing shouldBe true
        viewModel.state.value.notes shouldBe "original"

        viewModel.onEvent(LogDoseUiEvent.FieldChanged { it.copy(notes = "editado") })
        viewModel.effects.test {
            viewModel.onEvent(LogDoseUiEvent.Save)
            awaitItem() shouldBe LogDoseEffect.NavigateBack
        }
        history(doseLogs, clock).test { awaitItem().single().notes shouldBe "editado" }

        viewModel.effects.test {
            viewModel.onEvent(LogDoseUiEvent.ConfirmDelete)
            awaitItem() shouldBe LogDoseEffect.NavigateBack
        }
        history(doseLogs, clock).test { awaitItem().shouldBeEmpty() }
    }
})
