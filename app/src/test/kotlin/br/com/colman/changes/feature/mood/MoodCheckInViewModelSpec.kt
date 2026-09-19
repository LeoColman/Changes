// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.mood

import app.cash.turbine.test
import br.com.colman.changes.core.data.MoodRepository
import br.com.colman.changes.core.model.MoodLog
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import kotlin.uuid.Uuid

/** Instala humor e o ViewModel de check-in sobre um banco novo, em memória. */
private class MoodCheckInFixture {
    val io = UnconfinedTestDispatcher()
    val clock = FixedClock()
    val zones = FixedTimeZoneProvider(TestZones.SAO_PAULO)
    val database = testDatabase(clock)
    val moods = MoodRepository(database, io, clock, zones)
    val viewModel = MoodCheckInViewModel(moods, clock, zones)
    val today: LocalDate = clock.now().toLocalDateTime(zones.zone).date
}

class MoodCheckInViewModelSpec : FunSpec({
    register(MainDispatcherListener())

    test("critério 7.7.1: salvar duas vezes no mesmo dia mantém um registro, com os valores da segunda vez") {
        val fixture = MoodCheckInFixture()
        fixture.viewModel.effects.test {
            fixture.viewModel.onEvent(MoodCheckInUiEvent.Load(null))
            fixture.viewModel.onEvent(MoodCheckInUiEvent.MoodChanged(2))
            fixture.viewModel.onEvent(MoodCheckInUiEvent.EnergyChanged(3))
            fixture.viewModel.onEvent(MoodCheckInUiEvent.Save)
            awaitItem() shouldBe MoodCheckInEffect.Saved

            fixture.viewModel.onEvent(MoodCheckInUiEvent.MoodChanged(5))
            fixture.viewModel.onEvent(MoodCheckInUiEvent.EnergyChanged(4))
            fixture.viewModel.onEvent(MoodCheckInUiEvent.Save)
            awaitItem() shouldBe MoodCheckInEffect.Saved
        }

        val all = fixture.moods.observeAll().first()
        all shouldHaveSize 1
        all.first().mood shouldBe 5
        all.first().energy shouldBe 4
        all.first().date shouldBe fixture.today
    }

    test("critério 7.7.2: o estado da tela é o mesmo com notas diferentes, nada deriva da nota") {
        suspend fun stateIgnoringNote(note: String): MoodCheckInUiState {
            val fixture = MoodCheckInFixture()
            fixture.viewModel.onEvent(MoodCheckInUiEvent.Load(null))
            fixture.viewModel.onEvent(MoodCheckInUiEvent.MoodChanged(1))
            fixture.viewModel.onEvent(MoodCheckInUiEvent.EnergyChanged(1))
            fixture.viewModel.onEvent(MoodCheckInUiEvent.NoteChanged(note))
            return fixture.viewModel.state.value.copy(note = "")
        }

        val withNeutralNote = stateIgnoringNote("Um dia comum.")
        val withHeavyNote = stateIgnoringNote("Foi o pior dia que já tive, não aguento mais.")

        withNeutralNote shouldBe withHeavyNote
    }

    test("campos opcionais em branco gravam null") {
        val fixture = MoodCheckInFixture()
        fixture.viewModel.effects.test {
            fixture.viewModel.onEvent(MoodCheckInUiEvent.Load(null))
            fixture.viewModel.onEvent(MoodCheckInUiEvent.MoodChanged(3))
            fixture.viewModel.onEvent(MoodCheckInUiEvent.EnergyChanged(3))
            fixture.viewModel.onEvent(MoodCheckInUiEvent.Save)
            awaitItem() shouldBe MoodCheckInEffect.Saved
        }

        val saved = fixture.moods.observe(fixture.today).first().shouldNotBeNull()
        saved.relief.shouldBeNull()
        saved.irritability.shouldBeNull()
        saved.emotionalIntensity.shouldBeNull()
        saved.anxiety.shouldBeNull()
        saved.dysphoria.shouldBeNull()
        saved.sleepHours.shouldBeNull()
        saved.note.shouldBeNull()
        saved.tags.shouldHaveSize(0)
    }

    test("salvar grava os sentimentos (ADR 0012): alívio, irritabilidade, intensidade emocional e ansiedade") {
        val fixture = MoodCheckInFixture()
        fixture.viewModel.effects.test {
            fixture.viewModel.onEvent(MoodCheckInUiEvent.Load(null))
            fixture.viewModel.onEvent(MoodCheckInUiEvent.MoodChanged(3))
            fixture.viewModel.onEvent(MoodCheckInUiEvent.EnergyChanged(3))
            fixture.viewModel.onEvent(MoodCheckInUiEvent.ReliefChanged(4))
            fixture.viewModel.onEvent(MoodCheckInUiEvent.IrritabilityChanged(2))
            fixture.viewModel.onEvent(MoodCheckInUiEvent.EmotionalIntensityChanged(5))
            fixture.viewModel.onEvent(MoodCheckInUiEvent.AnxietyChanged(1))
            fixture.viewModel.onEvent(MoodCheckInUiEvent.Save)
            awaitItem() shouldBe MoodCheckInEffect.Saved
        }

        val saved = fixture.moods.observe(fixture.today).first().shouldNotBeNull()
        saved.relief shouldBe 4
        saved.irritability shouldBe 2
        saved.emotionalIntensity shouldBe 5
        saved.anxiety shouldBe 1
    }

    test("limpar uma escala de sentimento já preenchida grava vazio") {
        val fixture = MoodCheckInFixture()
        fixture.viewModel.effects.test {
            fixture.viewModel.onEvent(MoodCheckInUiEvent.Load(null))
            fixture.viewModel.onEvent(MoodCheckInUiEvent.MoodChanged(3))
            fixture.viewModel.onEvent(MoodCheckInUiEvent.EnergyChanged(3))
            fixture.viewModel.onEvent(MoodCheckInUiEvent.ReliefChanged(4))
            fixture.viewModel.onEvent(MoodCheckInUiEvent.Save)
            awaitItem() shouldBe MoodCheckInEffect.Saved

            fixture.viewModel.onEvent(MoodCheckInUiEvent.ReliefChanged(null))
            fixture.viewModel.onEvent(MoodCheckInUiEvent.Save)
            awaitItem() shouldBe MoodCheckInEffect.Saved
        }

        val saved = fixture.moods.observe(fixture.today).first().shouldNotBeNull()
        saved.relief.shouldBeNull()
    }

    test("um sentimento fora de 1..5 nunca sai da tela: o registro não é gravado") {
        val fixture = MoodCheckInFixture()
        fixture.viewModel.onEvent(MoodCheckInUiEvent.Load(null))
        fixture.viewModel.onEvent(MoodCheckInUiEvent.MoodChanged(3))
        fixture.viewModel.onEvent(MoodCheckInUiEvent.EnergyChanged(3))
        fixture.viewModel.onEvent(MoodCheckInUiEvent.IrritabilityChanged(6))
        fixture.viewModel.onEvent(MoodCheckInUiEvent.Save)

        fixture.moods.observe(fixture.today).first().shouldBeNull()
    }

    test("etiquetas digitadas com espaço e repetidas saem normalizadas") {
        val fixture = MoodCheckInFixture()
        fixture.viewModel.effects.test {
            fixture.viewModel.onEvent(MoodCheckInUiEvent.Load(null))
            fixture.viewModel.onEvent(MoodCheckInUiEvent.MoodChanged(3))
            fixture.viewModel.onEvent(MoodCheckInUiEvent.EnergyChanged(3))
            fixture.viewModel.onEvent(MoodCheckInUiEvent.TagsChanged(" trabalho, trabalho ,  sono,,"))
            fixture.viewModel.onEvent(MoodCheckInUiEvent.Save)
            awaitItem() shouldBe MoodCheckInEffect.Saved
        }

        val saved = fixture.moods.observe(fixture.today).first().shouldNotBeNull()
        saved.tags shouldContainExactly listOf("trabalho", "sono")
    }

    test("humor ou energia em branco mantém o formulário com erro, sem gravar") {
        val fixture = MoodCheckInFixture()
        fixture.viewModel.onEvent(MoodCheckInUiEvent.Load(null))
        fixture.viewModel.onEvent(MoodCheckInUiEvent.Save)

        val state = fixture.viewModel.state.value
        state.moodError shouldBe true
        state.energyError shouldBe true
        fixture.moods.observe(fixture.today).first().shouldBeNull()
    }

    test("carregar um dia existente preenche o formulário com os valores gravados") {
        val fixture = MoodCheckInFixture()
        val date = fixture.today
        fixture.moods.upsert(
            MoodLog(
                id = Uuid.random(),
                date = date,
                mood = 4,
                energy = 2,
                anxiety = 3,
                dysphoria = null,
                sleepHours = 7.5,
                note = "registro anterior",
                tags = listOf("trabalho"),
                relief = 5,
                irritability = 2,
                emotionalIntensity = 4,
            ),
        )

        fixture.viewModel.onEvent(MoodCheckInUiEvent.Load(date.toEpochDays()))

        val state = fixture.viewModel.state.value
        state.mood shouldBe 4
        state.energy shouldBe 2
        state.relief shouldBe 5
        state.irritability shouldBe 2
        state.emotionalIntensity shouldBe 4
        state.anxiety shouldBe 3
        state.dysphoria.shouldBeNull()
        state.note shouldBe "registro anterior"
        state.tagsText shouldBe "trabalho"
    }

    test(
        "hasUnsavedChanges is false right after loading, true after a field changes, and false again after saving"
    ) {
        val fixture = MoodCheckInFixture()
        val date = fixture.today
        fixture.moods.upsert(
            MoodLog(
                id = Uuid.random(),
                date = date,
                mood = 3,
                energy = 3,
                anxiety = null,
                dysphoria = null,
                sleepHours = null,
                note = null,
                tags = emptyList(),
            ),
        )

        fixture.viewModel.onEvent(MoodCheckInUiEvent.Load(date.toEpochDays()))
        fixture.viewModel.state.value.hasUnsavedChanges shouldBe false

        fixture.viewModel.onEvent(MoodCheckInUiEvent.MoodChanged(4))
        fixture.viewModel.state.value.hasUnsavedChanges shouldBe true

        fixture.viewModel.effects.test {
            fixture.viewModel.onEvent(MoodCheckInUiEvent.Save)
            awaitItem() shouldBe MoodCheckInEffect.Saved
        }
        fixture.viewModel.state.value.hasUnsavedChanges shouldBe false
    }

    test("on a new check-in hasUnsavedChanges is false untouched, and true once the first field is filled") {
        val fixture = MoodCheckInFixture()

        fixture.viewModel.onEvent(MoodCheckInUiEvent.Load(null))
        fixture.viewModel.state.value.hasUnsavedChanges shouldBe false

        fixture.viewModel.onEvent(MoodCheckInUiEvent.MoodChanged(3))
        fixture.viewModel.state.value.hasUnsavedChanges shouldBe true
    }

    test("editar um dia existente e salvar sobrescreve o mesmo registro (critério 7.7.1)") {
        val fixture = MoodCheckInFixture()
        val date = fixture.today
        val existing = fixture.moods.upsert(
            MoodLog(
                id = Uuid.random(),
                date = date,
                mood = 4,
                energy = 2,
                anxiety = null,
                dysphoria = null,
                sleepHours = null,
                note = null,
                tags = emptyList(),
            ),
        )

        val existingId = existing.getOrNull().shouldNotBeNull().id
        fixture.viewModel.onEvent(MoodCheckInUiEvent.Load(date.toEpochDays()))
        fixture.viewModel.effects.test {
            fixture.viewModel.onEvent(MoodCheckInUiEvent.MoodChanged(1))
            fixture.viewModel.onEvent(MoodCheckInUiEvent.Save)
            awaitItem() shouldBe MoodCheckInEffect.Saved
        }

        val all = fixture.moods.observeAll().first()
        all shouldHaveSize 1
        all.first().id shouldBe existingId
        all.first().mood shouldBe 1
    }
})
