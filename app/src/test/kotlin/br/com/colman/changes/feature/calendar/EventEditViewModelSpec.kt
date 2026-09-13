// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.calendar

import app.cash.turbine.test
import br.com.colman.changes.core.data.CalendarRepository
import br.com.colman.changes.core.data.NewCalendarEvent
import br.com.colman.changes.core.data.ProfileRepository
import br.com.colman.changes.core.model.AgendaItem
import br.com.colman.changes.core.model.CalendarCategory
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.RecurrenceRule
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.FixedTimeZoneProvider
import br.com.colman.changes.core.testing.MainDispatcherListener
import br.com.colman.changes.core.testing.TestZones
import br.com.colman.changes.core.testing.testDatabase
import br.com.colman.changes.core.testing.testDataset
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.Uuid

/**
 * Spec do ViewModel de criar/editar evento. Repositório real sobre um banco em memória
 * ([testDatabase]), relógio e fuso fixos ([FixedClock], [FixedTimeZoneProvider]).
 */
class EventEditViewModelSpec : FunSpec({
    extension(MainDispatcherListener())
    val io = UnconfinedTestDispatcher()

    fun repository(clock: FixedClock, timeZones: FixedTimeZoneProvider): CalendarRepository {
        val database = testDatabase(clock)
        val profiles = ProfileRepository(database, io, clock)
        return CalendarRepository(database, io, clock, timeZones, profiles, testDataset)
    }

    test("creating a simple event with valid fields persists it and navigates back") {
        val clock = FixedClock()
        val timeZones = FixedTimeZoneProvider()
        val repository = repository(clock, timeZones)
        val viewModel = EventEditViewModel(repository, clock, timeZones)
        viewModel.onEvent(EventEditUiEvent.Load(null, null))

        viewModel.onEvent(
            EventEditUiEvent.FieldChanged {
                it.copy(
                    title = "Consulta com endócrino",
                    description = "Levar exames",
                    category = CalendarCategory.APPOINTMENT
                )
            },
        )
        viewModel.effects.test {
            viewModel.onEvent(EventEditUiEvent.Save)
            awaitItem() shouldBe EventEditEffect.NavigateBack
        }

        val today = clock.now().toLocalDateTime(timeZones.current()).date
        val from = today.minus(1, DateTimeUnit.DAY)
        val to = today.plus(1, DateTimeUnit.DAY)
        repository.observeAgenda(from, to, includeMilestones = false).test {
            val event = awaitItem().filterIsInstance<AgendaItem.Event>().single().event
            event.title shouldBe "Consulta com endócrino"
            event.description shouldBe "Levar exames"
            event.category shouldBe CalendarCategory.APPOINTMENT
        }
    }

    test("creating an event without a title is rejected with a domain error, and nothing is persisted") {
        val clock = FixedClock()
        val timeZones = FixedTimeZoneProvider()
        val repository = repository(clock, timeZones)
        val viewModel = EventEditViewModel(repository, clock, timeZones)
        viewModel.onEvent(EventEditUiEvent.Load(null, null))

        viewModel.onEvent(EventEditUiEvent.Save)

        viewModel.state.value.error shouldBe DomainError.Invalid("title", DomainError.Reason.REQUIRED)
        val today = clock.now().toLocalDateTime(timeZones.current()).date
        repository.observeAgenda(today.minus(1, DateTimeUnit.DAY), today.plus(1, DateTimeUnit.DAY), false).test {
            awaitItem().filterIsInstance<AgendaItem.Event>().shouldBeEmpty()
        }
    }

    test("criterion 7.9.1: an all-day event created by the editor keeps its date after the device zone changes") {
        val clock = FixedClock()
        val timeZones = FixedTimeZoneProvider(TestZones.SAO_PAULO)
        val repository = repository(clock, timeZones)
        val viewModel = EventEditViewModel(repository, clock, timeZones)
        viewModel.onEvent(EventEditUiEvent.Load(null, null))
        val chosenDate = viewModel.state.value.startDate

        viewModel.onEvent(EventEditUiEvent.FieldChanged { it.copy(title = "Cirurgia", isAllDay = true) })
        viewModel.effects.test {
            viewModel.onEvent(EventEditUiEvent.Save)
            awaitItem() shouldBe EventEditEffect.NavigateBack
        }
        var createdId: Uuid? = null
        repository.observeAgenda(chosenDate, chosenDate, false).test {
            createdId = awaitItem().filterIsInstance<AgendaItem.Event>().single().event.id
            cancelAndIgnoreRemainingEvents()
        }

        timeZones.zone = TestZones.TOKYO

        val reread = repository.get(createdId.shouldNotBeNull()).shouldNotBeNull()
        reread.start.localDate shouldBe chosenDate
    }

    test("loading an existing event populates every field") {
        val clock = FixedClock()
        val timeZones = FixedTimeZoneProvider()
        val repository = repository(clock, timeZones)
        val until = LocalDate(2027, 1, 1)
        val created = repository.create(
            NewCalendarEvent(
                title = "Exame de sangue",
                description = "Jejum de 12h",
                start = clock.now,
                end = null,
                isAllDay = false,
                category = CalendarCategory.LAB,
                reminderMinutesBefore = 60,
                recurrence = RecurrenceRule(RecurrenceRule.Frequency.MONTHLY, interval = 2, until = until),
            ),
        ).getOrNull().shouldNotBeNull()
        val viewModel = EventEditViewModel(repository, clock, timeZones)

        viewModel.onEvent(EventEditUiEvent.Load(created.id.toString(), null))

        val state = viewModel.state.value
        state.isNew shouldBe false
        state.title shouldBe "Exame de sangue"
        state.description shouldBe "Jejum de 12h"
        state.category shouldBe CalendarCategory.LAB
        state.isAllDay shouldBe false
        state.startDate shouldBe created.start.localDate
        state.startTime shouldBe created.start.localDateTime.time
        state.reminder shouldBe ReminderOption.HOUR_1
        state.recurrenceFrequency shouldBe EventRecurrenceFrequency.MONTHLY
        state.recurrenceInterval shouldBe "2"
        state.recurrenceEndOption shouldBe RecurrenceEndOption.ON_DATE
        state.recurrenceUntil shouldBe created.recurrence?.until
        state.isCompleted shouldBe false
    }

    test("requesting and confirming delete soft-deletes the event and emits the undo effect") {
        val clock = FixedClock()
        val timeZones = FixedTimeZoneProvider()
        val database = testDatabase(clock)
        val profiles = ProfileRepository(database, io, clock)
        val repository = CalendarRepository(database, io, clock, timeZones, profiles, testDataset)
        val created = repository.create(
            NewCalendarEvent("Evento", null, clock.now, null, false, CalendarCategory.PERSONAL, null, null),
        ).getOrNull().shouldNotBeNull()
        val viewModel = EventEditViewModel(repository, clock, timeZones)
        viewModel.onEvent(EventEditUiEvent.Load(created.id.toString(), null))

        viewModel.onEvent(EventEditUiEvent.RequestDelete)
        viewModel.state.value.showDeleteConfirm shouldBe true

        viewModel.effects.test {
            viewModel.onEvent(EventEditUiEvent.ConfirmDelete)
            awaitItem() shouldBe EventEditEffect.ShowUndoDelete
        }
        database.calendarQueries.selectById(created.id.toString()).executeAsOne().deleted_at.shouldNotBeNull()

        viewModel.onEvent(EventEditUiEvent.UndoDelete)
        database.calendarQueries.selectById(created.id.toString()).executeAsOne().deleted_at.shouldBeNull()
    }

    test("marking an event as completed and saving records the completion") {
        val clock = FixedClock()
        val timeZones = FixedTimeZoneProvider()
        val repository = repository(clock, timeZones)
        val created = repository.create(
            NewCalendarEvent("Cirurgia", null, clock.now, null, false, CalendarCategory.SURGERY, null, null),
        ).getOrNull().shouldNotBeNull()
        val viewModel = EventEditViewModel(repository, clock, timeZones)
        viewModel.onEvent(EventEditUiEvent.Load(created.id.toString(), null))

        viewModel.onEvent(EventEditUiEvent.ToggleCompleted)
        viewModel.effects.test {
            viewModel.onEvent(EventEditUiEvent.Save)
            awaitItem() shouldBe EventEditEffect.NavigateBack
        }

        repository.get(created.id)?.completedAt.shouldNotBeNull()
    }

    test("a weekly recurrence built in the editor produces occurrences on the correct dates") {
        val clock = FixedClock()
        val timeZones = FixedTimeZoneProvider()
        val repository = repository(clock, timeZones)
        val viewModel = EventEditViewModel(repository, clock, timeZones)
        viewModel.onEvent(EventEditUiEvent.Load(null, null))
        val startDate = viewModel.state.value.startDate

        viewModel.onEvent(
            EventEditUiEvent.FieldChanged {
                it.copy(
                    title = "Fisioterapia",
                    recurrenceFrequency = EventRecurrenceFrequency.WEEKLY,
                    recurrenceByDay = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
                )
            },
        )
        viewModel.effects.test {
            viewModel.onEvent(EventEditUiEvent.Save)
            awaitItem() shouldBe EventEditEffect.NavigateBack
        }

        val from = startDate
        val to = startDate.plus(30, DateTimeUnit.DAY)
        val rule = RecurrenceRule(RecurrenceRule.Frequency.WEEKLY, byDay = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY))
        val expectedDates = rule.occurrences(startDate, from, to)
        repository.observeAgenda(from, to, includeMilestones = false).test {
            val dates = awaitItem().filterIsInstance<AgendaItem.Event>().map { it.date }
            dates shouldBe expectedDates
        }
    }
})
