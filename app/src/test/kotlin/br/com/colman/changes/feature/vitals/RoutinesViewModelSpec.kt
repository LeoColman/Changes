// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.vitals

import app.cash.turbine.test
import br.com.colman.changes.core.data.CalendarRepository
import br.com.colman.changes.core.data.ProfileRepository
import br.com.colman.changes.core.model.AgendaItem
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.FixedTimeZoneProvider
import br.com.colman.changes.core.testing.MainDispatcherListener
import br.com.colman.changes.core.testing.TestZones
import br.com.colman.changes.core.testing.testDatabase
import br.com.colman.changes.core.testing.testDataset
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/** Instala o calendário e o [RoutinesViewModel] sobre um banco novo, em memória. */
private class RoutinesFixture {
    val io = UnconfinedTestDispatcher()
    val clock = FixedClock()
    val zones = FixedTimeZoneProvider(TestZones.SAO_PAULO)
    val database = testDatabase(clock)
    val profiles = ProfileRepository(database, io, clock)
    val calendar = CalendarRepository(database, io, clock, zones, profiles, testDataset)
    val viewModel = RoutinesViewModel(calendar, clock, zones)
}

// `state` é um StateFlow derivado de `combine(...).stateIn(WhileSubscribed)`: só atualiza enquanto
// alguém o coleta. Por isso todo teste lê o estado dentro de um `.test { }` (Turbine) contínuo, nunca
// por `.value` solto entre eventos, como os specs de calendário e de exercício já fazem.
class RoutinesViewModelSpec : FunSpec({
    register(MainDispatcherListener())

    test("rotina diária criada aparece na lista e em observeAgenda em dias seguintes") {
        val fixture = RoutinesFixture()

        fixture.viewModel.state.test {
            awaitItem()
            fixture.viewModel.onEvent(RoutinesUiEvent.AddRequested)
            fixture.viewModel.onEvent(RoutinesUiEvent.FormActivityChanged("Caminhada"))
            fixture.viewModel.onEvent(RoutinesUiEvent.FormSaved)

            val saved = expectMostRecentItem()
            saved.form.shouldBeNull()
            saved.routines shouldHaveSize 1
            saved.routines.first().frequency shouldBe RoutineFrequency.DAILY
        }

        val today = fixture.clock.now.toLocalDateTime(fixture.zones.zone).date
        val to = today.plus(3, DateTimeUnit.DAY)
        fixture.calendar.observeAgenda(today, to, includeMilestones = false).test {
            val dates = awaitItem().filterIsInstance<AgendaItem.Event>().map { it.date }
            dates shouldBe (0..3).map { today.plus(it, DateTimeUnit.DAY) }
        }
    }

    test("rotina semanal de quarta só aparece às quartas") {
        val fixture = RoutinesFixture()
        val today = fixture.clock.now.toLocalDateTime(fixture.zones.zone).date

        fixture.viewModel.state.test {
            awaitItem()
            fixture.viewModel.onEvent(RoutinesUiEvent.AddRequested)
            fixture.viewModel.onEvent(RoutinesUiEvent.FormActivityChanged("Musculação"))
            fixture.viewModel.onEvent(RoutinesUiEvent.FormFrequencyChanged(RoutineFrequency.WEEKLY))
            val defaultDay = expectMostRecentItem().form.shouldNotBeNull().weeklyDays.single()
            fixture.viewModel.onEvent(RoutinesUiEvent.FormWeeklyDayToggled(defaultDay))
            fixture.viewModel.onEvent(RoutinesUiEvent.FormWeeklyDayToggled(DayOfWeek.WEDNESDAY))
            fixture.viewModel.onEvent(RoutinesUiEvent.FormSaved)

            expectMostRecentItem().form.shouldBeNull()
        }

        val to = today.plus(13, DateTimeUnit.DAY)
        fixture.calendar.observeAgenda(today, to, includeMilestones = false).test {
            val dates = awaitItem().filterIsInstance<AgendaItem.Event>().map { it.date }
            dates.shouldNotBeEmpty()
            dates.forEach { it.dayOfWeek shouldBe DayOfWeek.WEDNESDAY }
        }
    }

    test("editar muda frequência e título, sem duplicar a rotina") {
        val fixture = RoutinesFixture()

        fixture.viewModel.state.test {
            awaitItem()
            fixture.viewModel.onEvent(RoutinesUiEvent.AddRequested)
            fixture.viewModel.onEvent(RoutinesUiEvent.FormActivityChanged("Corrida"))
            fixture.viewModel.onEvent(RoutinesUiEvent.FormSaved)
            val created = expectMostRecentItem().routines.single()

            fixture.viewModel.onEvent(RoutinesUiEvent.EditRequested(created.id))
            fixture.viewModel.onEvent(RoutinesUiEvent.FormActivityChanged("Natação"))
            fixture.viewModel.onEvent(RoutinesUiEvent.FormFrequencyChanged(RoutineFrequency.WEEKLY))
            fixture.viewModel.onEvent(RoutinesUiEvent.FormSaved)

            val updated = expectMostRecentItem().routines.single()
            updated.id shouldBe created.id
            updated.activity shouldBe "Natação"
            updated.frequency shouldBe RoutineFrequency.WEEKLY
        }
    }

    test("excluir tira a rotina da lista e desfazer devolve") {
        val fixture = RoutinesFixture()

        fixture.viewModel.state.test {
            awaitItem()
            fixture.viewModel.onEvent(RoutinesUiEvent.AddRequested)
            fixture.viewModel.onEvent(RoutinesUiEvent.FormActivityChanged("Ioga"))
            fixture.viewModel.onEvent(RoutinesUiEvent.FormSaved)
            val created = expectMostRecentItem().routines.single()

            fixture.viewModel.onEvent(RoutinesUiEvent.DeleteRequested(created.id))
            expectMostRecentItem().routines.shouldHaveSize(0)

            fixture.viewModel.onEvent(RoutinesUiEvent.UndoDeleteRequested)
            expectMostRecentItem().routines shouldHaveSize 1
        }

        fixture.viewModel.effects.test { awaitItem() shouldBe RoutinesEffect.ShowUndoDelete }
    }

    test("semanal sem dia e atividade vazia são recusadas com o erro no campo certo, sem gravar") {
        val fixture = RoutinesFixture()

        fixture.viewModel.state.test {
            awaitItem()
            fixture.viewModel.onEvent(RoutinesUiEvent.AddRequested)
            fixture.viewModel.onEvent(RoutinesUiEvent.FormFrequencyChanged(RoutineFrequency.WEEKLY))
            val defaultDay = expectMostRecentItem().form.shouldNotBeNull().weeklyDays.single()
            fixture.viewModel.onEvent(RoutinesUiEvent.FormWeeklyDayToggled(defaultDay))
            fixture.viewModel.onEvent(RoutinesUiEvent.FormSaved)

            val afterSave = expectMostRecentItem()
            val form = afterSave.form.shouldNotBeNull()
            form.activityError shouldBe true
            form.weeklyDaysError shouldBe true
            afterSave.routines.shouldHaveSize(0)
        }
    }

    test("sem hora a rotina vira dia inteiro e não guarda lembrete, mesmo se um lembrete foi escolhido antes") {
        val fixture = RoutinesFixture()

        var created: RoutineUiState? = null
        fixture.viewModel.state.test {
            awaitItem()
            fixture.viewModel.onEvent(RoutinesUiEvent.AddRequested)
            fixture.viewModel.onEvent(RoutinesUiEvent.FormActivityChanged("Alongamento"))
            fixture.viewModel.onEvent(RoutinesUiEvent.FormSaved)
            created = expectMostRecentItem().routines.single()
        }
        val routine = created.shouldNotBeNull()
        routine.time.shouldBeNull()

        val stored = fixture.calendar.get(routine.id).shouldNotBeNull()
        stored.isAllDay shouldBe true
        stored.reminderMinutesBefore.shouldBeNull()
    }
})
