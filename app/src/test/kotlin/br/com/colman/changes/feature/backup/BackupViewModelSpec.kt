// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.backup

import br.com.colman.changes.core.data.backup.BackupRepository
import br.com.colman.changes.core.data.backup.ImportMode
import br.com.colman.changes.core.model.BackupRejection
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.FixedTimeZoneProvider
import br.com.colman.changes.core.testing.MainDispatcherListener
import br.com.colman.changes.platform.DocumentStreams
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.toLocalDateTime
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.OutputStream

private const val EXPORT_URI = "content://export"

class BackupViewModelSpec : FunSpec({
    register(MainDispatcherListener())
    val io = UnconfinedTestDispatcher()

    fun viewModel(
        repository: BackupRepository = testBackupRepository(io),
        documentStreams: FakeDocumentStreams = FakeDocumentStreams(newCacheDir()),
        planHolder: ImportPlanHolder = ImportPlanHolder(),
        clock: FixedClock = FixedClock(),
    ) = BackupViewModel(repository, documentStreams, planHolder, clock, FixedTimeZoneProvider())

    test("suggests a file name with today's date, from the injected clock and time zone") {
        val clock = FixedClock()
        val viewModel = viewModel(clock = clock)

        val today = clock.now().toLocalDateTime(FixedTimeZoneProvider().current()).date
        viewModel.state.value.suggestedFileName shouldBe "changes-$today.ttbackup.zip"
    }

    test("exporting writes the summary of the seeded database and clears a previous error") {
        val documentStreams = FakeDocumentStreams(newCacheDir())
        val viewModel = viewModel(documentStreams = documentStreams)

        viewModel.onEvent(BackupUiEvent.ExportRequested(EXPORT_URI))

        val state = viewModel.state.value
        state.isExporting shouldBe false
        state.error.shouldBeNull()
        val summary = state.exportSummary.shouldNotBeNull()
        (summary.rows["profile"] ?: 0) shouldBe 1
    }

    test("picking a file to import copies it to the cache and shows the mode choice") {
        val documentStreams = FakeDocumentStreams(newCacheDir())
        val viewModel = viewModel(documentStreams = documentStreams)
        viewModel.onEvent(BackupUiEvent.ExportRequested(EXPORT_URI))

        viewModel.onEvent(BackupUiEvent.ImportFilePicked(EXPORT_URI))

        viewModel.state.value.choosingImportMode shouldBe true
    }

    test("criterio 7.8 (adaptado): export seguido de plan em modo Mesclar sobre o mesmo banco mostra tudo igual") {
        val documentStreams = FakeDocumentStreams(newCacheDir())
        val planHolder = ImportPlanHolder()
        val viewModel = viewModel(documentStreams = documentStreams, planHolder = planHolder)
        viewModel.onEvent(BackupUiEvent.ExportRequested(EXPORT_URI))
        viewModel.onEvent(BackupUiEvent.ImportFilePicked(EXPORT_URI))

        viewModel.onEvent(BackupUiEvent.ImportModeChosen(ImportMode.MERGE))

        val plan = planHolder.plan.shouldNotBeNull()
        plan.mode shouldBe ImportMode.MERGE
        plan.tables.forEach { table ->
            withClue(table.toString()) {
                table.inserted shouldBe 0
                table.updated shouldBe 0
                table.kept shouldBe 0
                table.removed shouldBe 0
            }
        }
        viewModel.state.value.choosingImportMode shouldBe false
        viewModel.state.value.error.shouldBeNull()
    }

    test("erro de checksum mostra a mensagem certa e nada eh planejado") {
        val repository = testBackupRepository(io)
        val cacheDir = newCacheDir()
        val bytes = ByteArrayOutputStream().also { repository.export(it) }.toByteArray()
        val tampered = tamperDataEntry(bytes)
        val tamperedFile = File.createTempFile("tampered", ".ttbackup.zip", cacheDir).apply { writeBytes(tampered) }
        val documentStreams = FakeDocumentStreams(cacheDir)
        val planHolder = ImportPlanHolder()
        val viewModel = viewModel(repository = repository, documentStreams = documentStreams, planHolder = planHolder)

        viewModel.onEvent(BackupUiEvent.ImportFilePicked(tamperedFile.path))
        viewModel.onEvent(BackupUiEvent.ImportModeChosen(ImportMode.MERGE))

        viewModel.state.value.error shouldBe BackupRejection.CHECKSUM_MISMATCH
        viewModel.state.value.choosingImportMode shouldBe false
        planHolder.plan.shouldBeNull()
        documentStreams.importCacheCleared shouldBe true
    }

    test("canceling the import pick clears the temporary copy") {
        val documentStreams = FakeDocumentStreams(newCacheDir())
        val viewModel = viewModel(documentStreams = documentStreams)
        viewModel.onEvent(BackupUiEvent.ExportRequested(EXPORT_URI))
        viewModel.onEvent(BackupUiEvent.ImportFilePicked(EXPORT_URI))

        viewModel.onEvent(BackupUiEvent.CancelImportPick)

        viewModel.state.value.choosingImportMode shouldBe false
        documentStreams.importCacheCleared shouldBe true
    }

    test("a storage failure opening the destination or copying the picked file is shown, never a crash") {
        val failing = object : DocumentStreams {
            override suspend fun openOutput(uri: String): OutputStream = throw IOException("no space")

            override suspend fun copyToCache(uri: String): File = throw IOException("gone")

            override suspend fun clearImportCache() = Unit
        }
        val viewModel =
            BackupViewModel(
                testBackupRepository(io),
                failing,
                ImportPlanHolder(),
                FixedClock(),
                FixedTimeZoneProvider()
            )

        viewModel.onEvent(BackupUiEvent.ExportRequested(EXPORT_URI))
        viewModel.state.value.isExporting shouldBe false
        viewModel.state.value.error shouldBe BackupRejection.EXPORT_FAILED

        viewModel.onEvent(BackupUiEvent.ImportFilePicked(EXPORT_URI))
        viewModel.state.value.error shouldBe BackupRejection.UNREADABLE
        viewModel.state.value.choosingImportMode shouldBe false
    }
})
