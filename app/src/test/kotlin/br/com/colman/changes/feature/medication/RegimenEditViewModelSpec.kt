// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.medication

import app.cash.turbine.test
import br.com.colman.changes.core.data.MedicationRepository
import br.com.colman.changes.core.data.NewRegimen
import br.com.colman.changes.core.data.RegimenRepository
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.Dose
import br.com.colman.changes.core.model.DoseUnit
import br.com.colman.changes.core.model.RecurrenceRule
import br.com.colman.changes.core.model.Route
import br.com.colman.changes.core.model.Schedule
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.FixedTimeZoneProvider
import br.com.colman.changes.core.testing.MainDispatcherListener
import br.com.colman.changes.core.testing.testDatabase
import br.com.colman.changes.core.testing.testDataset
import br.com.colman.changes.core.testing.testLabels
import br.com.colman.changes.ui.format.Formatters
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlin.uuid.Uuid

class RegimenEditViewModelSpec : FunSpec({
    register(MainDispatcherListener())
    val io = UnconfinedTestDispatcher()
    fun medicationId() = testDataset.medications.first { it.key == "DEPOSTERON" }.id

    fun newRegimen() = NewRegimen(
        medicationId(),
        Dose(100.0, DoseUnit.MG),
        Route.INTRAMUSCULAR,
        Schedule.IntervalDays(14),
        LocalTime(8, 0),
        LocalDate(2026, 1, 1),
        null,
        null,
    )

    fun viewModel(regimens: RegimenRepository, medications: MedicationRepository, clock: FixedClock) =
        RegimenEditViewModel(
            regimens,
            medications,
            clock,
            FixedTimeZoneProvider(),
            MedicationDisplayNames(testDataset, testLabels)
        )

    test("creating a regimen with valid fields persists it and navigates back") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val medications = MedicationRepository(database, io, clock)
        val viewModel = viewModel(regimens, medications, clock)
        viewModel.onEvent(RegimenEditUiEvent.Load(null))
        val medicationOption = viewModel.state.value.availableMedications.first { it.id == medicationId().toString() }

        viewModel.onEvent(
            RegimenEditUiEvent.FieldChanged {
                it.copy(
                    selectedMedicationId = medicationOption.id,
                    doseValue = "0.25",
                    doseUnit = DoseUnit.ML,
                    route = Route.SUBCUTANEOUS,
                    scheduleOption = ScheduleOption.INTERVAL_DAYS,
                    intervalDays = "14",
                )
            },
        )

        viewModel.effects.test {
            viewModel.onEvent(RegimenEditUiEvent.Save)
            awaitItem() shouldBe RegimenEditEffect.NavigateBack
        }

        regimens.observeAll().test {
            val stored = awaitItem().single()
            stored.medicationId shouldBe medicationId()
            stored.dose shouldBe Dose(0.25, DoseUnit.ML)
            stored.route shouldBe Route.SUBCUTANEOUS
            stored.schedule shouldBe Schedule.IntervalDays(14)
            stored.isActive shouldBe true
        }
    }

    test("creating a regimen without a medication is rejected with a domain error, and nothing is persisted") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val medications = MedicationRepository(database, io, clock)
        val viewModel = viewModel(regimens, medications, clock)
        viewModel.onEvent(RegimenEditUiEvent.Load(null))

        viewModel.onEvent(RegimenEditUiEvent.Save)

        viewModel.state.value.error shouldBe DomainError.Invalid("medicationId", DomainError.Reason.REQUIRED)
        regimens.observeAll().test { awaitItem().shouldBeEmpty() }
    }

    test("an invalid schedule is rejected via DoseSchedule.validate, and nothing is persisted") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val medications = MedicationRepository(database, io, clock)
        val viewModel = viewModel(regimens, medications, clock)
        viewModel.onEvent(RegimenEditUiEvent.Load(null))
        val medicationOption = viewModel.state.value.availableMedications.first { it.id == medicationId().toString() }
        viewModel.onEvent(
            RegimenEditUiEvent.FieldChanged {
                it.copy(selectedMedicationId = medicationOption.id, doseValue = "10", intervalDays = "0")
            },
        )

        viewModel.onEvent(RegimenEditUiEvent.Save)

        viewModel.state.value.error shouldBe DomainError.Invalid("schedule", DomainError.Reason.OUT_OF_RANGE)
        regimens.observeAll().test { awaitItem().shouldBeEmpty() }
    }

    test("varying intervals: 45 days, then 90, then every 90 are saved as one series, with the next doses previewed") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val medications = MedicationRepository(database, io, clock)
        val viewModel = viewModel(regimens, medications, clock)
        viewModel.onEvent(RegimenEditUiEvent.Load(null))
        val start = viewModel.state.value.startDate

        viewModel.onEvent(
            RegimenEditUiEvent.FieldChanged {
                it.copy(
                    selectedMedicationId = medicationId().toString(),
                    doseValue = "1000",
                    scheduleOption = ScheduleOption.STEPPED,
                    stepDays = listOf("45", "90"),
                    continuous = true,
                    continuousEveryDays = "90",
                )
            },
        )

        viewModel.state.value.nextDoses shouldBe listOf(45, 135, 225, 315).map { start.plus(it, DateTimeUnit.DAY) }
        viewModel.effects.test {
            viewModel.onEvent(RegimenEditUiEvent.Save)
            awaitItem() shouldBe RegimenEditEffect.NavigateBack
        }
        regimens.observeAll().first().single().schedule shouldBe Schedule.Stepped(listOf(45, 90), thenEvery = 90)
    }

    test("without continuous use the preview stops after the last step") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val viewModel = viewModel(
            RegimenRepository(database, io, clock),
            MedicationRepository(database, io, clock),
            clock
        )
        viewModel.onEvent(RegimenEditUiEvent.Load(null))
        val start = viewModel.state.value.startDate

        viewModel.onEvent(
            RegimenEditUiEvent.FieldChanged {
                it.copy(scheduleOption = ScheduleOption.STEPPED, stepDays = listOf("0", "42"), continuous = false)
            },
        )

        viewModel.state.value.nextDoses shouldBe listOf(start, start.plus(42, DateTimeUnit.DAY))
    }

    test("an incomplete varying-interval series has no preview and is rejected on save") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val viewModel = viewModel(regimens, MedicationRepository(database, io, clock), clock)
        viewModel.onEvent(RegimenEditUiEvent.Load(null))

        viewModel.onEvent(
            RegimenEditUiEvent.FieldChanged {
                it.copy(
                    selectedMedicationId = medicationId().toString(),
                    doseValue = "10",
                    scheduleOption = ScheduleOption.STEPPED,
                    stepDays = listOf("45"),
                    continuous = true,
                    continuousEveryDays = "",
                )
            },
        )
        viewModel.state.value.nextDoses.shouldBeEmpty()

        viewModel.onEvent(RegimenEditUiEvent.Save)

        viewModel.state.value.error shouldBe DomainError.Invalid("schedule", DomainError.Reason.OUT_OF_RANGE)
        regimens.observeAll().test { awaitItem().shouldBeEmpty() }
    }

    test(
        "daily, monthly and quarterly are shortcuts for interval and monthly rules, and load back as the same option"
    ) {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val medications = MedicationRepository(database, io, clock)
        val cases = listOf(
            ScheduleOption.DAILY to Schedule.IntervalDays(1),
            ScheduleOption.MONTHLY to Schedule.Custom(RecurrenceRule(RecurrenceRule.Frequency.MONTHLY, interval = 2)),
            ScheduleOption.QUARTERLY to Schedule.Custom(RecurrenceRule(RecurrenceRule.Frequency.MONTHLY, interval = 3)),
        )

        cases.forEach { (option, expected) ->
            val viewModel = viewModel(regimens, medications, clock)
            viewModel.onEvent(RegimenEditUiEvent.Load(null))
            viewModel.onEvent(
                RegimenEditUiEvent.FieldChanged {
                    it.copy(
                        selectedMedicationId = medicationId().toString(),
                        doseValue = "10",
                        scheduleOption = option,
                        everyMonths = "2",
                        notes = option.name,
                    )
                },
            )
            viewModel.state.value.nextDoses.size shouldBe 4
            viewModel.onEvent(RegimenEditUiEvent.Save)

            val stored = regimens.observeAll().first().single { it.notes == option.name }
            stored.schedule shouldBe expected
            val reloaded = viewModel(regimens, medications, clock)
            reloaded.onEvent(RegimenEditUiEvent.Load(stored.id.toString()))
            reloaded.state.value.scheduleOption shouldBe option
        }
    }

    test("a rule the screen cannot edit loads as custom and is saved unchanged") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val medications = MedicationRepository(database, io, clock)
        val rule = Schedule.Custom(RecurrenceRule(RecurrenceRule.Frequency.MONTHLY, interval = 1, count = 6))
        val created = regimens.create(
            NewRegimen(
                medicationId(),
                Dose(10.0, DoseUnit.MG),
                Route.ORAL,
                rule,
                null,
                LocalDate(2026, 1, 1),
                null,
                null
            ),
        ).getOrNull().shouldNotBeNull()
        val viewModel = viewModel(regimens, medications, clock)

        viewModel.onEvent(RegimenEditUiEvent.Load(created.id.toString()))
        viewModel.state.value.scheduleOption shouldBe ScheduleOption.CUSTOM
        viewModel.onEvent(RegimenEditUiEvent.Save)

        regimens.observeAll().first().single().schedule shouldBe rule
    }

    test("creating a custom medication adds it to the catalog and selects it, with the field empty by default") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val medications = MedicationRepository(database, io, clock)
        val viewModel = viewModel(regimens, medications, clock)
        viewModel.onEvent(RegimenEditUiEvent.Load(null))
        viewModel.state.value.doseValue shouldBe ""

        viewModel.onEvent(RegimenEditUiEvent.StartCreatingMedication)
        viewModel.onEvent(
            RegimenEditUiEvent.FieldChanged {
                it.copy(
                    newMedicationName = "Minha medicação",
                    newMedicationSubstance = "Substância X",
                    newMedicationRoute = Route.ORAL
                )
            },
        )
        viewModel.onEvent(RegimenEditUiEvent.ConfirmNewMedication)

        val state = viewModel.state.value
        state.isCreatingMedication shouldBe false
        val createdId = state.selectedMedicationId.shouldNotBeNull()
        val created = medications.get(Uuid.parse(createdId)).shouldNotBeNull()
        created.name shouldBe "Minha medicação"
        created.substance shouldBe "Substância X"
        created.defaultRoute shouldBe Route.ORAL
    }

    test("loading an existing regimen populates every field, without creating a duplicate") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val medications = MedicationRepository(database, io, clock)
        val created = regimens.create(
            NewRegimen(
                medicationId(),
                Dose(0.5, DoseUnit.ML),
                Route.SUBCUTANEOUS,
                Schedule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)),
                LocalTime(9, 0),
                LocalDate(2026, 1, 1),
                null,
                "nota",
            ),
        ).getOrNull().shouldNotBeNull()
        val viewModel = viewModel(regimens, medications, clock)

        viewModel.onEvent(RegimenEditUiEvent.Load(created.id.toString()))

        val state = viewModel.state.value
        state.isNew shouldBe false
        state.selectedMedicationId shouldBe created.medicationId.toString()
        Formatters.parseNumber(state.doseValue) shouldBe 0.5
        state.doseUnit shouldBe DoseUnit.ML
        state.route shouldBe Route.SUBCUTANEOUS
        state.scheduleOption shouldBe ScheduleOption.WEEKLY
        state.weeklyDays shouldBe setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY)
        state.notes shouldBe "nota"

        viewModel.onEvent(RegimenEditUiEvent.Save)
        regimens.observeAll().test { awaitItem() shouldBe listOf(created) }
    }

    test(
        "hasUnsavedChanges is false right after loading, true after a field changes, and false again after saving"
    ) {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val medications = MedicationRepository(database, io, clock)
        val created = regimens.create(newRegimen()).getOrNull().shouldNotBeNull()
        val viewModel = viewModel(regimens, medications, clock)

        viewModel.onEvent(RegimenEditUiEvent.Load(created.id.toString()))
        viewModel.state.value.hasUnsavedChanges shouldBe false

        viewModel.onEvent(RegimenEditUiEvent.FieldChanged { it.copy(notes = "nova nota") })
        viewModel.state.value.hasUnsavedChanges shouldBe true

        viewModel.effects.test {
            viewModel.onEvent(RegimenEditUiEvent.Save)
            awaitItem() shouldBe RegimenEditEffect.NavigateBack
        }
        viewModel.state.value.hasUnsavedChanges shouldBe false
    }

    test("on a new regimen hasUnsavedChanges is false untouched, and true once the first field is filled") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val medications = MedicationRepository(database, io, clock)
        val viewModel = viewModel(regimens, medications, clock)

        viewModel.onEvent(RegimenEditUiEvent.Load(null))
        viewModel.state.value.hasUnsavedChanges shouldBe false

        viewModel.onEvent(RegimenEditUiEvent.FieldChanged { it.copy(doseValue = "10") })
        viewModel.state.value.hasUnsavedChanges shouldBe true
    }

    test("requesting and confirming delete removes the regimen and navigates back") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val regimens = RegimenRepository(database, io, clock)
        val medications = MedicationRepository(database, io, clock)
        val created = regimens.create(newRegimen()).getOrNull().shouldNotBeNull()
        val viewModel = viewModel(regimens, medications, clock)
        viewModel.onEvent(RegimenEditUiEvent.Load(created.id.toString()))

        viewModel.onEvent(RegimenEditUiEvent.RequestDelete)
        viewModel.state.value.showDeleteConfirm shouldBe true

        viewModel.effects.test {
            viewModel.onEvent(RegimenEditUiEvent.ConfirmDelete)
            awaitItem() shouldBe RegimenEditEffect.NavigateBack
        }
        regimens.observeAll().test { awaitItem().shouldBeEmpty() }
    }
})
