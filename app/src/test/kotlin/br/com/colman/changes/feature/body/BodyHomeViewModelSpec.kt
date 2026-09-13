// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import app.cash.turbine.test
import br.com.colman.changes.core.model.BodyChangeCategory
import br.com.colman.changes.core.model.BodyMeasurementUnit
import br.com.colman.changes.core.model.BodyRegion
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.model.VocabularyChoice
import br.com.colman.changes.core.testing.MainDispatcherListener
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe

class BodyHomeViewModelSpec : FunSpec({
    extension(MainDispatcherListener())
    extension(PtBrDefaultLocale())

    test("shows the visible types grouped by category, with the label resolved by the vocabulary") {
        val env = BodyTestEnvironment()
        val viewModel = BodyHomeViewModel(env.bodyChangeRepository, env.mediaRepository, env.bodyLabels)

        viewModel.state.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            val labels = state.sections.flatMap { it.types }.map { it.label }
            labels shouldContain "Crescimento genital"
            cancelAndIgnoreRemainingEvents()
        }
    }

    test("aceite 7.3: o rótulo de um tipo builtin muda quando o vocabulário do perfil muda") {
        val env = BodyTestEnvironment()
        val viewModel = BodyHomeViewModel(env.bodyChangeRepository, env.mediaRepository, env.bodyLabels)

        viewModel.state.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            state.sections.flatMap { it.types }.map { it.label } shouldContain "Crescimento genital"

            env.profileRepository.update { profile ->
                profile.copy(
                    bodyVocabulary = profile.bodyVocabulary.with(BodyRegion.GENITAL, VocabularyChoice.Preset("DICK"))
                )
            }

            var updated = awaitItem()
            while (updated.sections.flatMap { it.types }.map { it.label }.let { "Crescimento genital" in it }) {
                updated = awaitItem()
            }
            updated.sections.flatMap { it.types }.map { it.label } shouldContain "Crescimento do dick"
            updated.sections.flatMap { it.types }.map { it.label } shouldNotContain "Crescimento genital"
            cancelAndIgnoreRemainingEvents()
        }
    }

    test("aceite 7.3: um tipo customizado criado aparece na tela inicial") {
        val env = BodyTestEnvironment()
        val viewModel = BodyHomeViewModel(env.bodyChangeRepository, env.mediaRepository, env.bodyLabels)

        viewModel.state.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.onEvent(BodyHomeUiEvent.OpenNewTypeDialog)
            var withDialog = awaitItem()
            while (withDialog.newTypeDialog == null) withDialog = awaitItem()

            viewModel.onEvent(BodyHomeUiEvent.NewTypeNameChanged("Textura de pele nas mãos"))
            var named = awaitItem()
            while (named.newTypeDialog?.name != "Textura de pele nas mãos") named = awaitItem()

            viewModel.onEvent(BodyHomeUiEvent.NewTypeCategoryChanged(BodyChangeCategory.SKIN))
            var withCategory = awaitItem()
            while (withCategory.newTypeDialog?.category != BodyChangeCategory.SKIN) withCategory = awaitItem()

            viewModel.onEvent(BodyHomeUiEvent.ConfirmNewType)
            var afterCreate = awaitItem()
            while (
                afterCreate.newTypeDialog != null ||
                afterCreate.sections.flatMap { it.types }.none { it.label == "Textura de pele nas mãos" }
            ) {
                afterCreate = awaitItem()
            }

            afterCreate.sections.flatMap { it.types }.map { it.label } shouldContain "Textura de pele nas mãos"
            cancelAndIgnoreRemainingEvents()
        }
    }

    test("um nome em branco no novo tipo mostra erro e não cria nada") {
        val env = BodyTestEnvironment()
        val viewModel = BodyHomeViewModel(env.bodyChangeRepository, env.mediaRepository, env.bodyLabels)

        viewModel.state.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.onEvent(BodyHomeUiEvent.OpenNewTypeDialog)
            var withDialog = awaitItem()
            while (withDialog.newTypeDialog == null) withDialog = awaitItem()

            viewModel.onEvent(BodyHomeUiEvent.ConfirmNewType)
            val afterConfirm = awaitItem()
            afterConfirm.newTypeDialog?.nameError shouldBe true
            cancelAndIgnoreRemainingEvents()
        }
    }

    test("a unidade de medida escolhida no novo tipo é aplicada") {
        val env = BodyTestEnvironment()
        val result = env.bodyChangeRepository.createCustomType(
            "Circunferência de teste",
            BodyChangeCategory.BODY,
            BodyMeasurementUnit.CM,
        )
        val created = (result as Result.Success).value
        created.supportsMeasurement shouldBe true
        created.measurementUnit shouldBe BodyMeasurementUnit.CM
    }
})
