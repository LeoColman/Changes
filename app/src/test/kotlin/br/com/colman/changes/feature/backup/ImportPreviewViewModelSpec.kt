// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.backup

import app.cash.turbine.test
import br.com.colman.changes.core.data.backup.ImportMode
import br.com.colman.changes.core.testing.FixedTimeZoneProvider
import br.com.colman.changes.core.testing.MainDispatcherListener
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher

class ImportPreviewViewModelSpec : FunSpec({
    register(MainDispatcherListener())
    val io = UnconfinedTestDispatcher()

    test("loading a plan populates the preview with its counts and mode") {
        val repository = testBackupRepository(io)
        val plan = planFor(repository, newCacheDir(), ImportMode.MERGE)
        val holder = ImportPlanHolder().apply { set(plan) }
        val viewModel =
            ImportPreviewViewModel(repository, FakeDocumentStreams(newCacheDir()), holder, FixedTimeZoneProvider())

        val state = viewModel.state.value
        state.mode shouldBe ImportMode.MERGE
        state.schemaVersion shouldBe plan.schemaVersion
        state.appVersion shouldBe plan.appVersion
        state.tables shouldBe plan.tables
    }

    test("Substituir exige a palavra digitada: o modo Substituir chega marcado para a tela usar o TypedConfirmDialog") {
        val repository = testBackupRepository(io)
        val plan = planFor(repository, newCacheDir(), ImportMode.REPLACE)
        val holder = ImportPlanHolder().apply { set(plan) }
        val viewModel =
            ImportPreviewViewModel(repository, FakeDocumentStreams(newCacheDir()), holder, FixedTimeZoneProvider())

        // A tela escolhe o diálogo pelo modo: Mesclar usa ConfirmDialog, Substituir usa TypedConfirmDialog
        // com a palavra exigida (ImportPreviewScreen). O ViewModel só precisa expor o modo corretamente.
        viewModel.state.value.mode shouldBe ImportMode.REPLACE
    }

    test("applying merge succeeds, shows the result and clears the temporary copy") {
        val repository = testBackupRepository(io)
        val cacheDir = newCacheDir()
        val plan = planFor(repository, cacheDir, ImportMode.MERGE)
        val holder = ImportPlanHolder().apply { set(plan) }
        val documentStreams = FakeDocumentStreams(cacheDir)
        val viewModel = ImportPreviewViewModel(repository, documentStreams, holder, FixedTimeZoneProvider())

        viewModel.onEvent(ImportPreviewUiEvent.RequestApply)
        viewModel.state.value.showConfirm shouldBe true

        viewModel.onEvent(ImportPreviewUiEvent.ConfirmApply)

        viewModel.state.value.result.shouldNotBeNull()
        viewModel.state.value.showConfirm shouldBe false
        documentStreams.importCacheCleared shouldBe true
        holder.plan.shouldBeNull()
    }

    test("cancelando antes de confirmar apaga a copia temporaria (Cancelar apaga a copia temporaria)") {
        val repository = testBackupRepository(io)
        val cacheDir = newCacheDir()
        val plan = planFor(repository, cacheDir, ImportMode.MERGE)
        val holder = ImportPlanHolder().apply { set(plan) }
        val documentStreams = FakeDocumentStreams(cacheDir)
        val viewModel = ImportPreviewViewModel(repository, documentStreams, holder, FixedTimeZoneProvider())

        viewModel.effects.test {
            viewModel.onEvent(ImportPreviewUiEvent.Cancel)
            awaitItem() shouldBe ImportPreviewEffect.NavigateBack
        }

        documentStreams.importCacheCleared shouldBe true
        holder.plan.shouldBeNull()
    }

    test("finishing after a result sends the Finished effect") {
        val repository = testBackupRepository(io)
        val cacheDir = newCacheDir()
        val plan = planFor(repository, cacheDir, ImportMode.MERGE)
        val holder = ImportPlanHolder().apply { set(plan) }
        val viewModel =
            ImportPreviewViewModel(repository, FakeDocumentStreams(cacheDir), holder, FixedTimeZoneProvider())
        viewModel.onEvent(ImportPreviewUiEvent.ConfirmApply)

        viewModel.effects.test {
            viewModel.onEvent(ImportPreviewUiEvent.Done)
            awaitItem() shouldBe ImportPreviewEffect.Finished
        }
    }
})
