// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.trash

import app.cash.turbine.turbineScope
import br.com.colman.changes.core.data.HealthConditionRepository
import br.com.colman.changes.core.data.MoodRepository
import br.com.colman.changes.core.data.TrashItem
import br.com.colman.changes.core.data.TrashRepository
import br.com.colman.changes.core.model.ConditionSeverity
import br.com.colman.changes.core.model.ConditionStatus
import br.com.colman.changes.core.model.HealthCondition
import br.com.colman.changes.core.model.MoodLog
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.core.testing.FileMediaStorage
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.FixedTimeZoneProvider
import br.com.colman.changes.core.testing.MainDispatcherListener
import br.com.colman.changes.core.testing.testDatabase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.LocalDate
import kotlin.io.path.createTempDirectory
import kotlin.time.Duration.Companion.days
import kotlin.uuid.Uuid

/**
 * Instala a lixeira e os repositórios de origem (Seção 9, ADR 0008) sobre um banco novo, em memória.
 * `deletedCondition`/`deletedMood` colocam um item na lixeira pelo caminho real: criar e depois excluir.
 */
private class TrashFixture {
    val io = UnconfinedTestDispatcher()
    val clock = FixedClock()
    val zones = FixedTimeZoneProvider()
    val database = testDatabase(clock)
    val media = FileMediaStorage(createTempDirectory().toFile())
    val conditions = HealthConditionRepository(database, io, clock)
    val moods = MoodRepository(database, io, clock, zones)
    val trashRepository = TrashRepository(database, media, clock, io)
    val viewModel = TrashViewModel(trashRepository, zones)

    suspend fun deletedCondition(label: String = "Enxaqueca"): Uuid {
        val condition = conditions.create(
            HealthCondition(
                id = Uuid.random(),
                label = label,
                code = null,
                severity = ConditionSeverity.MILD,
                status = ConditionStatus.ACTIVE,
                diagnosedAt = null,
                resolvedAt = null,
                affectsTreatment = false,
                notes = null,
            ),
        ).getOrNull().shouldNotBeNull()
        conditions.delete(condition.id)
        return condition.id
    }

    suspend fun deletedMood(): Uuid {
        val mood = moods.upsert(
            MoodLog(
                id = Uuid.random(),
                date = LocalDate(2026, 9, 1),
                mood = 3,
                energy = 3,
                anxiety = null,
                dysphoria = null,
                sleepHours = null,
                note = null,
                tags = emptyList(),
            ),
        ).getOrNull().shouldNotBeNull()
        moods.delete(mood.id)
        return mood.id
    }
}

class TrashViewModelSpec : FunSpec({
    register(MainDispatcherListener())

    test("critério: item excluído por um repositório aparece na lixeira com o tipo certo") {
        val fixture = TrashFixture()
        fixture.deletedCondition(label = "Enxaqueca")

        turbineScope {
            val states = fixture.viewModel.state.testIn(this)
            var state = states.awaitItem()
            while (state.isLoading) state = states.awaitItem()

            state.groups shouldHaveSize 1
            val group = state.groups.single()
            group.category shouldBe TrashCategory.HEALTH_CONDITION
            group.entries.single().item.label shouldBe "Enxaqueca"

            states.cancelAndIgnoreRemainingEvents()
        }
    }

    test("itens de tipos diferentes aparecem em grupos separados") {
        val fixture = TrashFixture()
        fixture.deletedCondition()
        fixture.deletedMood()

        turbineScope {
            val states = fixture.viewModel.state.testIn(this)
            var state = states.awaitItem()
            while (state.isLoading) state = states.awaitItem()

            state.groups.map { it.category }.toSet() shouldBe setOf(TrashCategory.HEALTH_CONDITION, TrashCategory.MOOD)
            // Registro de humor sem nota vai para a lixeira com o rótulo vazio (Trash.sq).
            state.groups.single { it.category == TrashCategory.MOOD }.entries.single().item.label shouldBe ""

            states.cancelAndIgnoreRemainingEvents()
        }
    }

    test("critério: restaurar tira o item da lista e ele volta ao repositório de origem") {
        val fixture = TrashFixture()
        val id = fixture.deletedCondition()

        turbineScope {
            val states = fixture.viewModel.state.testIn(this)
            val effects = fixture.viewModel.effects.testIn(this)
            var state = states.awaitItem()
            while (state.isLoading) state = states.awaitItem()
            val item = state.groups.single().entries.single().item

            fixture.viewModel.onEvent(TrashUiEvent.Restore(item))

            effects.awaitItem() shouldBe TrashEffect.ShowRestoredSnackbar
            states.awaitItem().groups.shouldBeEmpty()

            states.cancelAndIgnoreRemainingEvents()
            effects.cancelAndIgnoreRemainingEvents()
        }

        fixture.conditions.observeAll().first().map { it.id } shouldBe listOf(id)
    }

    test("critério: excluir definitivamente tira da lista e não volta") {
        val fixture = TrashFixture()
        val id = fixture.deletedCondition()

        turbineScope {
            val states = fixture.viewModel.state.testIn(this)
            var state = states.awaitItem()
            while (state.isLoading) state = states.awaitItem()
            val item = state.groups.single().entries.single().item

            fixture.viewModel.onEvent(TrashUiEvent.RequestPurge(item))
            states.awaitItem().pendingPurge shouldBe item

            fixture.viewModel.onEvent(TrashUiEvent.ConfirmPurge)
            val afterPurge = states.awaitItem()
            afterPurge.groups.shouldBeEmpty()
            afterPurge.pendingPurge shouldBe null

            states.cancelAndIgnoreRemainingEvents()
        }

        fixture.conditions.observeAll().first().shouldBeEmpty()
    }

    test("cancelar a exclusão definitiva mantém o item na lista") {
        val fixture = TrashFixture()
        fixture.deletedCondition()

        turbineScope {
            val states = fixture.viewModel.state.testIn(this)
            var state = states.awaitItem()
            while (state.isLoading) state = states.awaitItem()
            val item = state.groups.single().entries.single().item

            fixture.viewModel.onEvent(TrashUiEvent.RequestPurge(item))
            states.awaitItem().pendingPurge shouldBe item

            fixture.viewModel.onEvent(TrashUiEvent.DismissPurge)
            val afterDismiss = states.awaitItem()
            afterDismiss.pendingPurge shouldBe null
            afterDismiss.groups.single().entries.single().item shouldBe item

            states.cancelAndIgnoreRemainingEvents()
        }
    }

    test("critério: esvaziar exige a palavra digitada e limpa a lista") {
        val fixture = TrashFixture()
        fixture.deletedCondition()
        fixture.deletedMood()

        turbineScope {
            val states = fixture.viewModel.state.testIn(this)
            var state = states.awaitItem()
            while (state.isLoading) state = states.awaitItem()
            state.groups shouldHaveSize 2

            // Sem confirmar, esvaziar não some com nada: a tela só pede a palavra digitada.
            fixture.viewModel.onEvent(TrashUiEvent.RequestEmpty)
            states.awaitItem().emptyRequested shouldBe true

            fixture.viewModel.onEvent(TrashUiEvent.ConfirmEmpty)
            val afterEmpty = states.awaitItem()
            afterEmpty.groups.shouldBeEmpty()
            afterEmpty.emptyRequested shouldBe false

            states.cancelAndIgnoreRemainingEvents()
        }

        fixture.conditions.observeAll().first().shouldBeEmpty()
        fixture.moods.observeAll().first().shouldBeEmpty()
    }

    test("cancelar esvaziar mantém os itens") {
        val fixture = TrashFixture()
        fixture.deletedCondition()

        turbineScope {
            val states = fixture.viewModel.state.testIn(this)
            var state = states.awaitItem()
            while (state.isLoading) state = states.awaitItem()
            state.groups shouldHaveSize 1

            fixture.viewModel.onEvent(TrashUiEvent.RequestEmpty)
            states.awaitItem().emptyRequested shouldBe true

            fixture.viewModel.onEvent(TrashUiEvent.DismissEmpty)
            val afterDismiss = states.awaitItem()
            afterDismiss.emptyRequested shouldBe false
            afterDismiss.groups shouldHaveSize 1

            states.cancelAndIgnoreRemainingEvents()
        }
    }

    test("critério: lista vazia mostra o estado vazio") {
        val fixture = TrashFixture()

        turbineScope {
            val states = fixture.viewModel.state.testIn(this)
            var state = states.awaitItem()
            while (state.isLoading) state = states.awaitItem()

            state.groups.shouldBeEmpty()

            states.cancelAndIgnoreRemainingEvents()
        }
    }

    test("as datas de exclusão e de saída da lixeira vêm 30 dias uma da outra") {
        val fixture = TrashFixture()
        fixture.deletedCondition()

        turbineScope {
            val states = fixture.viewModel.state.testIn(this)
            var state = states.awaitItem()
            while (state.isLoading) state = states.awaitItem()

            val entry = state.groups.single().entries.single()
            val item: TrashItem = entry.item
            item.purgeAt shouldBe item.deletedAt + THIRTY_DAYS

            states.cancelAndIgnoreRemainingEvents()
        }
    }
})

private val THIRTY_DAYS = 30.days
