// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.calendar

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import br.com.colman.changes.core.clinical.ExpectedChangeRules
import br.com.colman.changes.core.data.CalendarRepository
import br.com.colman.changes.core.data.DoseLogRepository
import br.com.colman.changes.core.data.NewCalendarEvent
import br.com.colman.changes.core.data.NewDoseLog
import br.com.colman.changes.core.data.NewRegimen
import br.com.colman.changes.core.data.ProfileRepository
import br.com.colman.changes.core.data.RegimenRepository
import br.com.colman.changes.core.model.CalendarCategory
import br.com.colman.changes.core.model.Dose
import br.com.colman.changes.core.model.DoseUnit
import br.com.colman.changes.core.model.Route
import br.com.colman.changes.core.model.Schedule
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.FixedTimeZoneProvider
import br.com.colman.changes.core.testing.MainDispatcherListener
import br.com.colman.changes.core.testing.TestZones
import br.com.colman.changes.core.testing.testDatabase
import br.com.colman.changes.core.testing.testDataset
import br.com.colman.changes.core.testing.testLabels
import br.com.colman.changes.platform.AppSettings
import br.com.colman.changes.platform.SettingsStore
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Fake em memória do `SettingsStore`, no lugar de um mock. */
private class FakeSettingsStore(initial: AppSettings = AppSettings()) : SettingsStore {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<AppSettings> = state.asStateFlow()
    override fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}

/**
 * Spec do ViewModel da tela Calendário. Repositórios reais sobre um banco em memória
 * ([testDatabase]), relógio e fuso fixos ([FixedClock], [FixedTimeZoneProvider]).
 */
class CalendarViewModelSpec : FunSpec({
    extension(MainDispatcherListener())
    val io = UnconfinedTestDispatcher()

    fun medicationId() = testDataset.medications.first().id

    suspend fun ReceiveTurbine<CalendarUiState>.awaitLoaded(): CalendarUiState {
        var item = awaitItem()
        while (item.isLoading) item = awaitItem()
        return item
    }

    test("items are grouped by local date, with one marker per item kind present that day") {
        val clock = FixedClock()
        val timeZones = FixedTimeZoneProvider()
        val today = clock.now().toLocalDateTime(timeZones.current()).date
        val database = testDatabase(clock)
        val profiles = ProfileRepository(database, io, clock)
        val calendarRepository = CalendarRepository(database, io, clock, timeZones, profiles, testDataset)
        val regimens = RegimenRepository(database, io, clock)
        regimens.create(
            NewRegimen(
                medicationId(),
                Dose(50.0, DoseUnit.MG),
                Route.INTRAMUSCULAR,
                Schedule.IntervalDays(30),
                LocalTime(8, 0),
                today,
                null,
                null
            ),
        )
        val doseLogs = DoseLogRepository(database, io, clock, timeZones)
        doseLogs.log(
            NewDoseLog(null, medicationId(), Dose(50.0, DoseUnit.MG), Route.INTRAMUSCULAR, null, clock.now, null),
        )
        calendarRepository.create(
            NewCalendarEvent("Consulta", null, clock.now, null, false, CalendarCategory.APPOINTMENT, null, null),
        )
        val viewModel = CalendarViewModel(
            calendarRepository,
            profiles,
            testLabels,
            FakeSettingsStore(),
            clock,
            timeZones
        )

        viewModel.state.test {
            val loaded = awaitLoaded()
            loaded.selectedDate shouldBe today
            val todayCell = loaded.monthDays.first { it.date == today }
            todayCell.markers shouldBe setOf(CalendarItemKind.PLANNED_DOSE, CalendarItemKind.LOGGED_DOSE, CalendarItemKind.EVENT)
            loaded.selectedDayItems.map { it::class } shouldBe listOf(
                CalendarItemUiState.PlannedDoseItem::class,
                CalendarItemUiState.LoggedDoseItem::class,
                CalendarItemUiState.EventItem::class,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    test("criterion 7.9.2: an ended regimen plans no dose past its end date, as seen in the month grid") {
        val clock = FixedClock()
        val timeZones = FixedTimeZoneProvider()
        val today = clock.now().toLocalDateTime(timeZones.current()).date
        val database = testDatabase(clock)
        val profiles = ProfileRepository(database, io, clock)
        val calendarRepository = CalendarRepository(database, io, clock, timeZones, profiles, testDataset)
        val regimens = RegimenRepository(database, io, clock)
        regimens.create(
            NewRegimen(
                medicationId(),
                Dose(50.0, DoseUnit.MG),
                Route.INTRAMUSCULAR,
                Schedule.IntervalDays(1),
                LocalTime(8, 0),
                today.minus(30, DateTimeUnit.DAY),
                today.minus(5, DateTimeUnit.DAY),
                null,
            ),
        )
        val viewModel = CalendarViewModel(
            calendarRepository,
            profiles,
            testLabels,
            FakeSettingsStore(),
            clock,
            timeZones
        )

        viewModel.state.test {
            val loaded = awaitLoaded()
            val todayCell = loaded.monthDays.first { it.date == today }
            (CalendarItemKind.PLANNED_DOSE in todayCell.markers) shouldBe false
            loaded.selectedDayItems.filterIsInstance<CalendarItemUiState.PlannedDoseItem>().shouldBeEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    test("the milestone layer obeys the settings toggle") {
        val hrtStart = LocalDate(2020, 1, 1)
        val change = testDataset.expectedChanges.first()
        val onset = ExpectedChangeRules.onsetStart(change, hrtStart)
        val zone = TestZones.SAO_PAULO
        val clock = FixedClock(LocalDateTime(onset, LocalTime(12, 0)).toInstant(zone))
        val timeZones = FixedTimeZoneProvider(zone)
        val database = testDatabase(clock)
        val profiles = ProfileRepository(database, io, clock)
        profiles.update { it.copy(hrtStartDate = hrtStart) }
        val calendarRepository = CalendarRepository(database, io, clock, timeZones, profiles, testDataset)
        val settingsStore = FakeSettingsStore()
        val viewModel = CalendarViewModel(calendarRepository, profiles, testLabels, settingsStore, clock, timeZones)

        viewModel.state.test {
            val loaded = awaitLoaded()
            loaded.showMilestones shouldBe true
            loaded.selectedDayItems.filterIsInstance<CalendarItemUiState.MilestoneItem>()
                .any { it.changeTypeCode == change.changeTypeCode } shouldBe true

            viewModel.onEvent(CalendarUiEvent.ToggleMilestones(false))
            val hidden = expectMostRecentItem()
            hidden.showMilestones shouldBe false
            hidden.selectedDayItems.filterIsInstance<CalendarItemUiState.MilestoneItem>().shouldBeEmpty()

            viewModel.onEvent(CalendarUiEvent.ToggleMilestones(true))
            val shown = expectMostRecentItem()
            shown.selectedDayItems.filterIsInstance<CalendarItemUiState.MilestoneItem>()
                .any { it.changeTypeCode == change.changeTypeCode } shouldBe true
            cancelAndIgnoreRemainingEvents()
        }
    }

    test("selecting a date updates the items shown below the grid") {
        val clock = FixedClock()
        val timeZones = FixedTimeZoneProvider()
        val today = clock.now().toLocalDateTime(timeZones.current()).date
        val database = testDatabase(clock)
        val profiles = ProfileRepository(database, io, clock)
        val calendarRepository = CalendarRepository(database, io, clock, timeZones, profiles, testDataset)
        val otherDay = today.plus(3, DateTimeUnit.DAY)
        calendarRepository.create(
            NewCalendarEvent(
                "Exame",
                null,
                LocalDateTime(otherDay, LocalTime(10, 0)).toInstant(timeZones.current()),
                null,
                false,
                CalendarCategory.LAB,
                null,
                null,
            ),
        )
        val viewModel = CalendarViewModel(
            calendarRepository,
            profiles,
            testLabels,
            FakeSettingsStore(),
            clock,
            timeZones
        )

        viewModel.state.test {
            awaitLoaded().selectedDayItems.shouldBeEmpty()
            viewModel.onEvent(CalendarUiEvent.SelectDate(otherDay))
            val afterSelection = awaitItem()
            afterSelection.selectedDate shouldBe otherDay
            afterSelection.selectedDayItems.map { (it as CalendarItemUiState.EventItem).title } shouldBe listOf("Exame")
            cancelAndIgnoreRemainingEvents()
        }
    }

    test("switching to the Agenda view groups upcoming items by day, starting from today") {
        val clock = FixedClock()
        val timeZones = FixedTimeZoneProvider()
        val today = clock.now().toLocalDateTime(timeZones.current()).date
        val database = testDatabase(clock)
        val profiles = ProfileRepository(database, io, clock)
        val calendarRepository = CalendarRepository(database, io, clock, timeZones, profiles, testDataset)
        val firstDay = today.plus(2, DateTimeUnit.DAY)
        val secondDay = today.plus(10, DateTimeUnit.DAY)
        calendarRepository.create(
            NewCalendarEvent(
                "Primeiro",
                null,
                firstDay.atStartOfDayIn(timeZones.current()),
                null,
                true,
                CalendarCategory.PERSONAL,
                null,
                null
            ),
        )
        calendarRepository.create(
            NewCalendarEvent(
                "Segundo",
                null,
                secondDay.atStartOfDayIn(timeZones.current()),
                null,
                true,
                CalendarCategory.PERSONAL,
                null,
                null
            ),
        )
        val viewModel = CalendarViewModel(
            calendarRepository,
            profiles,
            testLabels,
            FakeSettingsStore(),
            clock,
            timeZones
        )

        viewModel.state.test {
            awaitLoaded()
            viewModel.onEvent(CalendarUiEvent.ChangeViewMode(CalendarViewMode.AGENDA))
            val agenda = awaitItem()
            agenda.viewMode shouldBe CalendarViewMode.AGENDA
            agenda.agendaGroups.map { it.date } shouldBe listOf(firstDay, secondDay)
            cancelAndIgnoreRemainingEvents()
        }
    }
})
