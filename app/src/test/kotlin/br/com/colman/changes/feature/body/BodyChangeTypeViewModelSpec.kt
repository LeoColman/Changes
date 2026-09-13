// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import app.cash.turbine.test
import br.com.colman.changes.core.model.BodyChangeCategory
import br.com.colman.changes.core.model.MediaOwnerType
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.testing.MainDispatcherListener
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.hours
import kotlin.uuid.Uuid

class BodyChangeTypeViewModelSpec : FunSpec({
    extension(MainDispatcherListener())
    extension(PtBrDefaultLocale())

    suspend fun BodyTestEnvironment.attachPhoto(entryId: Uuid) {
        mediaRepository.attach(
            MediaOwnerType.BODY_CHANGE_ENTRY,
            entryId,
            fakePhotoBytes().inputStream(),
            "image/jpeg",
            null,
        )
    }

    test("aceite 7.3: um tipo customizado sobrevive a ocultar e depois mostrar de novo") {
        val env = BodyTestEnvironment()
        val created = (
            env.bodyChangeRepository.createCustomType(
                "Marca de teste",
                BodyChangeCategory.OTHER,
                null
            ) as Result.Success
            ).value
        val viewModel = BodyChangeTypeViewModel(
            env.bodyChangeRepository,
            env.mediaRepository,
            env.bodyLabels,
            created.id.toString()
        )

        viewModel.state.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            state.isHidden shouldBe false

            viewModel.onEvent(BodyChangeTypeUiEvent.ToggleHidden)
            var hidden = awaitItem()
            while (!hidden.isHidden) hidden = awaitItem()

            viewModel.onEvent(BodyChangeTypeUiEvent.ToggleHidden)
            var shown = awaitItem()
            while (shown.isHidden) shown = awaitItem()

            shown.typeLabel shouldBe "Marca de teste"
            cancelAndIgnoreRemainingEvents()
        }
        env.bodyChangeRepository.getType(created.id).shouldNotBeNull()
    }

    test("aceite 7.3: excluir uma entrada some da linha do tempo, e Desfazer traz de volta com as fotos") {
        val env = BodyTestEnvironment()
        val type = env.typeByCode("SKIN_OILINESS_ACNE")
        val entry = (
            env.bodyChangeRepository.createEntry(type.id, env.clock.now - 1.hours, null, null, null) as Result.Success
            ).value
        env.attachPhoto(entry.id)

        val viewModel = BodyChangeTypeViewModel(
            env.bodyChangeRepository,
            env.mediaRepository,
            env.bodyLabels,
            type.id.toString()
        )
        viewModel.state.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            state.entries.map { it.entryId } shouldContainExactly listOf(entry.id.toString())
            state.entries.first().photo.shouldNotBeNull()

            viewModel.onEvent(BodyChangeTypeUiEvent.DeleteEntry(entry.id.toString()))
            var afterDelete = awaitItem()
            while (afterDelete.entries.isNotEmpty() || afterDelete.pendingDeletionEntryId == null) {
                afterDelete = awaitItem()
            }
            afterDelete.pendingDeletionEntryId shouldBe entry.id.toString()

            viewModel.onEvent(BodyChangeTypeUiEvent.UndoDeleteEntry)
            var afterUndo = awaitItem()
            while (afterUndo.entries.isEmpty()) afterUndo = awaitItem()
            afterUndo.entries.map { it.entryId } shouldContainExactly listOf(entry.id.toString())
            afterUndo.entries.first().photo.shouldNotBeNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    test("aceite 7.3: a comparação só oferece entradas com foto") {
        val env = BodyTestEnvironment()
        val type = env.typeByCode("SKIN_OILINESS_ACNE")
        val withPhoto1 = (
            env.bodyChangeRepository.createEntry(type.id, env.clock.now - 3.hours, null, null, null) as Result.Success
            ).value
        val withPhoto2 = (
            env.bodyChangeRepository.createEntry(type.id, env.clock.now - 2.hours, null, null, null) as Result.Success
            ).value
        val withoutPhoto = (
            env.bodyChangeRepository.createEntry(type.id, env.clock.now - 1.hours, null, null, null) as Result.Success
            ).value
        env.attachPhoto(withPhoto1.id)
        env.attachPhoto(withPhoto2.id)

        val viewModel = BodyChangeTypeViewModel(
            env.bodyChangeRepository,
            env.mediaRepository,
            env.bodyLabels,
            type.id.toString()
        )
        viewModel.state.test {
            var state = awaitItem()
            while (state.isLoading || state.comparison.candidates.size < 2) state = awaitItem()

            state.comparison.candidates.map {
                it.entryId
            }.toSet() shouldBe setOf(withPhoto1.id.toString(), withPhoto2.id.toString())
            state.comparison.candidates.map { it.entryId } shouldNotContain withoutPhoto.id.toString()

            viewModel.onEvent(BodyChangeTypeUiEvent.SelectComparisonLeft(withPhoto1.id.toString()))
            var withLeft = awaitItem()
            while (withLeft.comparison.leftEntryId == null) withLeft = awaitItem()

            viewModel.onEvent(BodyChangeTypeUiEvent.SelectComparisonRight(withPhoto2.id.toString()))
            var withRight = awaitItem()
            while (withRight.comparison.rightEntryId == null) withRight = awaitItem()

            withRight.comparison.leftEntryId shouldBe withPhoto1.id.toString()
            withRight.comparison.rightEntryId shouldBe withPhoto2.id.toString()
            cancelAndIgnoreRemainingEvents()
        }
    }

    test("excluir um tipo customizado remove ele do catálogo") {
        val env = BodyTestEnvironment()
        val created = (
            env.bodyChangeRepository.createCustomType("Descartável", BodyChangeCategory.OTHER, null) as Result.Success
            ).value
        val viewModel = BodyChangeTypeViewModel(
            env.bodyChangeRepository,
            env.mediaRepository,
            env.bodyLabels,
            created.id.toString()
        )

        viewModel.state.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.onEvent(BodyChangeTypeUiEvent.RequestDeleteType)
            var requested = awaitItem()
            while (!requested.deleteTypeRequested) requested = awaitItem()

            viewModel.onEvent(BodyChangeTypeUiEvent.ConfirmDeleteType)
            var confirmed = awaitItem()
            while (confirmed.deleteTypeRequested) confirmed = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        env.bodyChangeRepository.observeAllTypes().test {
            awaitItem().map { it.id } shouldNotContain created.id
            cancelAndIgnoreRemainingEvents()
        }
    }
})
