// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.mood

import app.cash.turbine.test
import br.com.colman.changes.core.data.MoodRepository
import br.com.colman.changes.core.model.MoodLog
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.FixedTimeZoneProvider
import br.com.colman.changes.core.testing.MainDispatcherListener
import br.com.colman.changes.core.testing.TestZones
import br.com.colman.changes.core.testing.testDatabase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.Uuid

/** Instala humor e o ViewModel de histórico sobre um banco novo, em memória. */
private class MoodHistoryFixture {
    val io = UnconfinedTestDispatcher()
    val clock = FixedClock()
    val zones = FixedTimeZoneProvider(TestZones.SAO_PAULO)
    val database = testDatabase(clock)
    val moods = MoodRepository(database, io, clock, zones)
    val viewModel = MoodHistoryViewModel(moods, clock, zones)
    val today: LocalDate = clock.now().toLocalDateTime(zones.zone).date

    suspend fun logDay(date: LocalDate, mood: Int, energy: Int) {
        moods.upsert(
            MoodLog(
                id = Uuid.random(),
                date = date,
                mood = mood,
                energy = energy,
                anxiety = null,
                dysphoria = null,
                sleepHours = null,
                note = null,
                tags = emptyList(),
            ),
        )
    }
}

class MoodHistoryViewModelSpec : FunSpec({
    register(MainDispatcherListener())

    test("calendário de calor do mês tem os dias registrados com humor e os demais vazios") {
        val fixture = MoodHistoryFixture()
        fixture.logDay(fixture.today, mood = 4, energy = 3)
        fixture.logDay(fixture.today.minus(1, DateTimeUnit.DAY), mood = 2, energy = 2)

        fixture.viewModel.state.test {
            val state = awaitItem()
            state.heatmapDays.first { it.date == fixture.today }.mood shouldBe 4
            state.heatmapDays.first { it.date == fixture.today.minus(1, DateTimeUnit.DAY) }.mood shouldBe 2
            state.heatmapDays.count { it.mood == null } shouldBe state.heatmapDays.size - 2
            state.registeredDaysInMonth shouldBe 2
        }
    }

    test("resumo acessível do calendário e da tendência presentes no estado") {
        val fixture = MoodHistoryFixture()
        fixture.logDay(fixture.today, mood = 3, energy = 3)

        fixture.viewModel.state.test {
            val state = awaitItem()
            state.registeredDaysInMonth shouldBe 1
            state.trendEntryCount shouldBe 1
            state.trendMoodPoints shouldHaveSize 1
            state.trendEnergyPoints shouldHaveSize 1
        }
    }

    test("tendência inclui só os últimos 90 dias, mesmo com registros mais antigos") {
        val fixture = MoodHistoryFixture()
        fixture.logDay(fixture.today, mood = 3, energy = 3)
        fixture.logDay(fixture.today.minus(120, DateTimeUnit.DAY), mood = 5, energy = 5)

        fixture.viewModel.state.test {
            val state = awaitItem()
            state.trendEntryCount shouldBe 1
        }
    }

    test("navegar para o mês anterior troca os dias do calendário de calor") {
        val fixture = MoodHistoryFixture()
        val lastMonthDay = fixture.today.minus(1, DateTimeUnit.MONTH)
        fixture.logDay(lastMonthDay, mood = 5, energy = 5)

        fixture.viewModel.state.test {
            awaitItem()
            fixture.viewModel.onEvent(MoodHistoryUiEvent.PreviousMonth)
            val state = awaitItem()
            state.heatmapDays.any { it.date == lastMonthDay && it.mood == 5 } shouldBe true
        }
    }

    test("não é possível navegar para depois do mês atual") {
        val fixture = MoodHistoryFixture()

        fixture.viewModel.state.test {
            val initial = awaitItem()
            initial.canGoToNextMonth shouldBe false
            cancelAndIgnoreRemainingEvents()
        }

        val before = fixture.viewModel.state.value
        fixture.viewModel.onEvent(MoodHistoryUiEvent.NextMonth)
        fixture.viewModel.state.value shouldBe before
    }

    test("lista de registros traz todos os dias, independente do mês exibido") {
        val fixture = MoodHistoryFixture()
        fixture.logDay(fixture.today, mood = 3, energy = 3)
        fixture.logDay(fixture.today.minus(2, DateTimeUnit.MONTH), mood = 1, energy = 1)

        fixture.viewModel.state.test {
            val state = awaitItem()
            state.entries shouldHaveSize 2
        }
    }

    test("sem nenhum registro a lista de entradas fica vazia") {
        val fixture = MoodHistoryFixture()

        fixture.viewModel.state.test {
            val state = awaitItem()
            state.entries.shouldHaveSize(0)
            state.trendEntryCount shouldBe 0
        }
    }
})
