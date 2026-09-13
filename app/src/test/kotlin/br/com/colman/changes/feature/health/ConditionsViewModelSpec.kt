// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.health

import app.cash.turbine.test
import br.com.colman.changes.core.data.HealthConditionRepository
import br.com.colman.changes.core.data.ProfileRepository
import br.com.colman.changes.core.model.ConditionSeverity
import br.com.colman.changes.core.model.ConditionStatus
import br.com.colman.changes.core.model.HealthCondition
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.MainDispatcherListener
import br.com.colman.changes.core.testing.testDatabase
import br.com.colman.changes.core.testing.testLabels
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.uuid.Uuid

/** Instala condições, perfil e o ViewModel sobre um banco novo, em memória. */
private class ConditionsFixture {
    val io = UnconfinedTestDispatcher()
    val clock = FixedClock()
    val database = testDatabase(clock)
    val profiles = ProfileRepository(database, io, clock)
    val conditions = HealthConditionRepository(database, io, clock)
    val viewModel = ConditionsViewModel(conditions, profiles, testLabels)

    fun draft(label: String = "Enxaqueca com aura", affectsTreatment: Boolean = false) = HealthCondition(
        id = Uuid.random(),
        label = label,
        code = null,
        severity = ConditionSeverity.UNKNOWN,
        status = ConditionStatus.ACTIVE,
        diagnosedAt = null,
        resolvedAt = null,
        affectsTreatment = affectsTreatment,
        notes = null,
    )
}

class ConditionsViewModelSpec : FunSpec({
    register(MainDispatcherListener())

    test("condição relevante para o tratamento aparece na seção fixada, e as demais na seção de outras") {
        val fixture = ConditionsFixture()
        fixture.conditions.create(fixture.draft(label = "Trombose venosa profunda", affectsTreatment = true))
        fixture.conditions.create(fixture.draft(label = "Enxaqueca sem relação", affectsTreatment = false))

        fixture.viewModel.state.test {
            val state = awaitItem()
            state.pinned.map { it.label } shouldBe listOf("Trombose venosa profunda")
            state.others.map { it.label } shouldBe listOf("Enxaqueca sem relação")
        }
    }

    test("sugestões de rótulo vêm do dataset clínico do locale do perfil") {
        val fixture = ConditionsFixture()
        fixture.profiles.update { it.copy(locale = "pt-BR") }

        fixture.viewModel.state.test {
            awaitItem().suggestions shouldBe testLabels.forLocale("pt-BR").conditionSuggestions

            fixture.profiles.update { it.copy(locale = "en") }
            expectMostRecentItem().suggestions shouldBe testLabels.forLocale("en").conditionSuggestions
        }
    }

    test("adicionar uma condição pelo formulário grava e fecha o formulário") {
        val fixture = ConditionsFixture()

        fixture.viewModel.state.test {
            awaitItem()
            fixture.viewModel.onEvent(ConditionsUiEvent.AddRequested)
            fixture.viewModel.onEvent(ConditionsUiEvent.FormChanged { it.copy(label = "Apneia obstrutiva do sono") })
            fixture.viewModel.onEvent(ConditionsUiEvent.FormSaved)

            val saved = expectMostRecentItem()
            saved.form.shouldBeNull()
            saved.others.map { it.label } shouldBe listOf("Apneia obstrutiva do sono")
        }
    }

    test("editar uma condição existente atualiza o mesmo registro, sem duplicar") {
        val fixture = ConditionsFixture()
        val id = fixture.conditions.create(fixture.draft(label = "Hipertensão")).getOrNull().shouldNotBeNull().id

        fixture.viewModel.state.test {
            awaitItem()
            fixture.viewModel.onEvent(ConditionsUiEvent.EditRequested(id))
            fixture.viewModel.onEvent(ConditionsUiEvent.FormChanged { it.copy(affectsTreatment = true) })
            fixture.viewModel.onEvent(ConditionsUiEvent.FormSaved)

            val saved = expectMostRecentItem()
            saved.form.shouldBeNull()
            saved.pinned shouldHaveSize 1
            saved.others shouldHaveSize 0
            saved.pinned.first().label shouldBe "Hipertensão"
        }
    }

    test("excluir mostra o efeito de desfazer e a condição some; desfazer traz de volta") {
        val fixture = ConditionsFixture()
        val id = fixture.conditions.create(fixture.draft(label = "Dislipidemia")).getOrNull().shouldNotBeNull().id

        fixture.viewModel.state.test {
            awaitItem()
            fixture.viewModel.onEvent(ConditionsUiEvent.DeleteRequested(id))
            val afterDelete = expectMostRecentItem()
            afterDelete.others.shouldHaveSize(0)

            fixture.viewModel.onEvent(ConditionsUiEvent.UndoDeleteRequested)
            val afterUndo = expectMostRecentItem()
            afterUndo.others shouldHaveSize 1
        }

        fixture.viewModel.effects.test { awaitItem() shouldBe ConditionsEffect.ShowUndoDelete }
    }

    test("rótulo em branco não grava e devolve erro de domínio no formulário") {
        val fixture = ConditionsFixture()

        fixture.viewModel.state.test {
            awaitItem()
            fixture.viewModel.onEvent(ConditionsUiEvent.AddRequested)
            fixture.viewModel.onEvent(ConditionsUiEvent.FormSaved)

            val afterSave = expectMostRecentItem()
            afterSave.form.shouldNotBeNull().error.shouldNotBeNull()
            afterSave.others.shouldHaveSize(0)
        }
    }
})
