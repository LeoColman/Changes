// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.vitals

import app.cash.turbine.test
import br.com.colman.changes.core.data.ExerciseRepository
import br.com.colman.changes.core.model.ExerciseIntensity
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.FixedTimeZoneProvider
import br.com.colman.changes.core.testing.MainDispatcherListener
import br.com.colman.changes.core.testing.TestZones
import br.com.colman.changes.core.testing.testDatabase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/** Instala exercício e o ViewModel sobre um banco novo, em memória. */
private class ExerciseFixture {
    val io = UnconfinedTestDispatcher()
    val clock = FixedClock()
    val zones = FixedTimeZoneProvider(TestZones.SAO_PAULO)
    val database = testDatabase(clock)
    val exercises = ExerciseRepository(database, io, clock, zones)
    val viewModel = ExerciseViewModel(exercises, clock, zones)
}

class ExerciseViewModelSpec : FunSpec({
    register(MainDispatcherListener())

    test("resumo semanal com semanas sem sessão em 0") {
        val fixture = ExerciseFixture()
        val zone = fixture.zones.zone
        // Quarta 2026-02-18 é a única sessão, na semana de segunda 2026-02-16 (a semana "atual").
        fixture.exercises.create(
            "corrida",
            45,
            ExerciseIntensity.MODERATE,
            LocalDate(2026, 2, 18).atStartOfDayIn(zone),
            null,
        )
        fixture.clock.now = LocalDate(2026, 2, 20).atStartOfDayIn(zone)

        fixture.viewModel.state.test {
            val weekly = awaitItem().weeklyMinutes
            weekly shouldHaveSize 8
            weekly.dropLast(1).map { it.minutes } shouldContainExactly List(7) { 0 }
            weekly.last().minutes shouldBe 45
            weekly.last().weekStart shouldBe LocalDate(2026, 2, 16)
        }
    }

    test("resumo acessível do gráfico presente no estado") {
        val fixture = ExerciseFixture()

        fixture.viewModel.state.test {
            val state = awaitItem()
            state.weeklyChartPoints shouldHaveSize state.weeklyMinutes.size
        }
    }

    test("adicionar uma sessão pelo formulário grava e limpa o formulário") {
        val fixture = ExerciseFixture()

        fixture.viewModel.state.test {
            awaitItem()
            fixture.viewModel.onEvent(ExerciseUiEvent.AddRequested)
            fixture.viewModel.onEvent(ExerciseUiEvent.FormActivityChanged("corrida"))
            fixture.viewModel.onEvent(ExerciseUiEvent.FormDurationChanged("40"))
            fixture.viewModel.onEvent(ExerciseUiEvent.FormSaved)

            val saved = expectMostRecentItem()
            saved.form.shouldBeNull()
            saved.sessions shouldHaveSize 1
            saved.sessions.first().durationMinutes shouldBe 40
        }
    }

    test("editar uma sessão existente atualiza o mesmo registro, sem duplicar") {
        val fixture = ExerciseFixture()
        val created = fixture.exercises.create(
            "corrida",
            30,
            ExerciseIntensity.MODERATE,
            fixture.clock.now,
            null,
        ).getOrNull().shouldNotBeNull()

        fixture.viewModel.state.test {
            awaitItem()
            fixture.viewModel.onEvent(ExerciseUiEvent.EditRequested(created.id))
            fixture.viewModel.onEvent(ExerciseUiEvent.FormDurationChanged("60"))
            fixture.viewModel.onEvent(ExerciseUiEvent.FormSaved)

            val saved = expectMostRecentItem()
            saved.form.shouldBeNull()
            saved.sessions shouldHaveSize 1
            saved.sessions.first().durationMinutes shouldBe 60
        }
    }

    test("atividade em branco ou duração inválida mantém o formulário aberto com erro, sem gravar") {
        val fixture = ExerciseFixture()

        fixture.viewModel.state.test {
            awaitItem()
            fixture.viewModel.onEvent(ExerciseUiEvent.AddRequested)
            fixture.viewModel.onEvent(ExerciseUiEvent.FormDurationChanged("0"))
            fixture.viewModel.onEvent(ExerciseUiEvent.FormSaved)

            val afterSave = expectMostRecentItem()
            val form = afterSave.form.shouldNotBeNull()
            form.activityError shouldBe true
            form.durationError shouldBe true
            afterSave.sessions.shouldHaveSize(0)
        }
    }

    test("relato: recusa por data futura marca o erro na data, nunca sempre na duração") {
        val fixture = ExerciseFixture()
        val today = fixture.clock.now.toLocalDateTime(fixture.zones.zone).date
        val future = today.plus(1, DateTimeUnit.DAY)

        fixture.viewModel.state.test {
            awaitItem()
            fixture.viewModel.onEvent(ExerciseUiEvent.AddRequested)
            fixture.viewModel.onEvent(ExerciseUiEvent.FormActivityChanged("corrida"))
            fixture.viewModel.onEvent(ExerciseUiEvent.FormDurationChanged("30"))
            fixture.viewModel.onEvent(ExerciseUiEvent.FormDateChanged(future))
            fixture.viewModel.onEvent(ExerciseUiEvent.FormSaved)

            val afterSave = expectMostRecentItem()
            val form = afterSave.form.shouldNotBeNull()
            form.dateError shouldBe true
            form.durationError shouldBe false
            form.activityError shouldBe false
            afterSave.sessions.shouldHaveSize(0)
        }
    }

    test("data futura recusa a sessão em fusos e horas diferentes, sempre com o erro na data") {
        val cases = listOf(
            TestZones.SAO_PAULO to 23,
            TestZones.TOKYO to 0,
            TestZones.NEW_YORK to 12,
            TestZones.LORD_HOWE to 6,
        )
        for ((zone, hour) in cases) {
            val fixture = ExerciseFixture()
            fixture.zones.zone = zone
            fixture.clock.now = LocalDate(2026, 6, 15).atTime(hour, 30).toInstant(zone)
            val today = fixture.clock.now.toLocalDateTime(zone).date

            fixture.viewModel.state.test {
                awaitItem()
                fixture.viewModel.onEvent(ExerciseUiEvent.AddRequested)
                fixture.viewModel.onEvent(ExerciseUiEvent.FormActivityChanged("caminhada"))
                fixture.viewModel.onEvent(ExerciseUiEvent.FormDurationChanged("30"))
                fixture.viewModel.onEvent(ExerciseUiEvent.FormDateChanged(today.plus(3, DateTimeUnit.DAY)))
                fixture.viewModel.onEvent(ExerciseUiEvent.FormSaved)

                val form = expectMostRecentItem().form.shouldNotBeNull()
                form.dateError shouldBe true
                form.durationError shouldBe false
            }
        }
    }

    test("duração aceita número inteiro digitado com decimal zero, vírgula ou ponto") {
        listOf("30,0", "30.0").forEach { text ->
            val fixture = ExerciseFixture()

            fixture.viewModel.state.test {
                awaitItem()
                fixture.viewModel.onEvent(ExerciseUiEvent.AddRequested)
                fixture.viewModel.onEvent(ExerciseUiEvent.FormActivityChanged("corrida"))
                fixture.viewModel.onEvent(ExerciseUiEvent.FormDurationChanged(text))
                fixture.viewModel.onEvent(ExerciseUiEvent.FormSaved)

                val saved = expectMostRecentItem()
                saved.form.shouldBeNull()
                saved.sessions.single().durationMinutes shouldBe 30
            }
        }
    }

    test("excluir mostra o efeito de desfazer e a sessão some; desfazer traz de volta") {
        val fixture = ExerciseFixture()
        val created = fixture.exercises.create(
            "corrida",
            30,
            ExerciseIntensity.MODERATE,
            fixture.clock.now,
            null,
        ).getOrNull().shouldNotBeNull()

        fixture.viewModel.state.test {
            awaitItem()
            fixture.viewModel.onEvent(ExerciseUiEvent.DeleteRequested(created.id))
            expectMostRecentItem().sessions.shouldHaveSize(0)

            fixture.viewModel.onEvent(ExerciseUiEvent.UndoDeleteRequested)
            expectMostRecentItem().sessions shouldHaveSize 1
        }

        fixture.viewModel.effects.test { awaitItem() shouldBe ExerciseEffect.ShowUndoDelete }
    }
})
