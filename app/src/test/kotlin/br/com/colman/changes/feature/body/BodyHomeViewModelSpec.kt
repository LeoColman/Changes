// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import app.cash.turbine.test
import br.com.colman.changes.core.model.BodyChangeCategory
import br.com.colman.changes.core.model.BodyMeasurementUnit
import br.com.colman.changes.core.model.BodyRegion
import br.com.colman.changes.core.model.MediaOwnerType
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.model.VocabularyChoice
import br.com.colman.changes.core.testing.MainDispatcherListener
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlin.time.Duration.Companion.hours

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

    test("ocultar um tipo tira ele das seções, sem apagar os registros nem as fotos") {
        val env = BodyTestEnvironment()
        val type = env.typeByCode("CLITORAL_ENLARGEMENT")
        val entry = (
            env.bodyChangeRepository.createEntry(type.id, env.clock.now - 1.hours, null, null, null) as Result.Success
            ).value
        env.mediaRepository.attach(
            MediaOwnerType.BODY_CHANGE_ENTRY,
            entry.id,
            fakePhotoBytes().inputStream(),
            "image/jpeg",
            null,
        )
        val viewModel = BodyHomeViewModel(env.bodyChangeRepository, env.mediaRepository, env.bodyLabels)

        viewModel.state.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            state.sections.flatMap { it.types }.map { it.typeId } shouldContain type.id.toString()

            env.bodyChangeRepository.setTypeHidden(type.id, true)
            while (state.sections.flatMap { it.types }.any { it.typeId == type.id.toString() }) state = awaitItem()

            state.sections.flatMap { it.types }.map { it.typeId } shouldNotContain type.id.toString()
            cancelAndIgnoreRemainingEvents()
        }
        env.bodyChangeRepository.getEntry(entry.id).shouldNotBeNull()
        env.mediaRepository.observeByOwner(MediaOwnerType.BODY_CHANGE_ENTRY, entry.id).first().size shouldBe 1
    }

    test("um tipo oculto continua alcançável na tela inicial, com o rótulo do vocabulário") {
        val env = BodyTestEnvironment()
        val type = env.typeByCode("CLITORAL_ENLARGEMENT")
        env.bodyChangeRepository.setTypeHidden(type.id, true)
        env.profileRepository.update { profile ->
            profile.copy(
                bodyVocabulary = profile.bodyVocabulary.with(BodyRegion.GENITAL, VocabularyChoice.Preset("DICK"))
            )
        }
        val viewModel = BodyHomeViewModel(env.bodyChangeRepository, env.mediaRepository, env.bodyLabels)

        viewModel.state.test {
            var state = awaitItem()
            while (state.hiddenTypes.none { it.label == "Crescimento do dick" }) state = awaitItem()
            state.hiddenTypes shouldContainExactly listOf(HiddenBodyType(type.id.toString(), "Crescimento do dick"))
            state.hiddenTypesExpanded shouldBe false
            cancelAndIgnoreRemainingEvents()
        }
    }

    test("com todos os tipos ocultos, nenhuma seção sobra e todos continuam alcançáveis") {
        val env = BodyTestEnvironment()
        val all = env.bodyChangeRepository.observeAllTypes().first()
        all.forEach { env.bodyChangeRepository.setTypeHidden(it.id, true) }
        val viewModel = BodyHomeViewModel(env.bodyChangeRepository, env.mediaRepository, env.bodyLabels)

        viewModel.state.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            state.sections.shouldBeEmpty()
            state.hiddenTypes.map { it.typeId } shouldContainExactlyInAnyOrder all.map { it.id.toString() }
            cancelAndIgnoreRemainingEvents()
        }
    }

    test("a lista de ocultos abre e recolhe") {
        val env = BodyTestEnvironment()
        val viewModel = BodyHomeViewModel(env.bodyChangeRepository, env.mediaRepository, env.bodyLabels)

        viewModel.state.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            state.hiddenTypesExpanded shouldBe false

            viewModel.onEvent(BodyHomeUiEvent.ToggleHiddenTypes)
            while (!state.hiddenTypesExpanded) state = awaitItem()

            viewModel.onEvent(BodyHomeUiEvent.ToggleHiddenTypes)
            while (state.hiddenTypesExpanded) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    test("mostrar um tipo oculto devolve ele à sua seção, com a última foto") {
        val env = BodyTestEnvironment()
        val type = env.typeByCode("CLITORAL_ENLARGEMENT")
        val entry = (
            env.bodyChangeRepository.createEntry(type.id, env.clock.now - 1.hours, null, null, null) as Result.Success
            ).value
        env.mediaRepository.attach(
            MediaOwnerType.BODY_CHANGE_ENTRY,
            entry.id,
            fakePhotoBytes().inputStream(),
            "image/jpeg",
            null,
        )
        env.bodyChangeRepository.setTypeHidden(type.id, true)
        val viewModel = BodyHomeViewModel(env.bodyChangeRepository, env.mediaRepository, env.bodyLabels)

        viewModel.state.test {
            var state = awaitItem()
            while (state.hiddenTypes.isEmpty()) state = awaitItem()

            viewModel.onEvent(BodyHomeUiEvent.ShowType(type.id.toString()))
            while (state.hiddenTypes.isNotEmpty()) state = awaitItem()

            val section = state.sections.single { it.category == BodyChangeCategory.SEXUAL_REPRODUCTIVE }
            section.types.single { it.typeId == type.id.toString() }.lastPhoto.shouldNotBeNull()
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
