// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data.backup

import app.cash.sqldelight.db.SqlDriver
import br.com.colman.changes.core.db.ScratchDriverFactory
import br.com.colman.changes.core.db.Seeder
import br.com.colman.changes.core.db.createDatabase
import br.com.colman.changes.core.media.MediaStorage
import br.com.colman.changes.core.model.BackupRejection
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.model.asFailure
import br.com.colman.changes.core.model.asSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Backup completo (Seção 7.8, ADR 0009). `plan` valida tudo e calcula o efeito sem escrever; `apply`
 * valida de novo e aplica numa transação. Mídias novas vão para staging antes da transação e só ganham
 * o nome final depois do commit; se o processo morrer no meio, `recoverInterruptedImports` completa ou
 * descarta o staging na próxima abertura.
 */
public class BackupRepository(
    driver: SqlDriver,
    scratch: ScratchDriverFactory,
    private val media: MediaStorage,
    private val seeder: Seeder,
    private val runtime: BackupRuntime,
) {
    private val database = createDatabase(driver)
    private val exporter = BackupExporter(driver, media, runtime.appInfo)
    private val validator = BackupValidator(scratch)
    private val importer = BackupImporter(driver)

    /** Escreve o backup em [output] e fecha o stream. Guarda a data do export (lembrete de backup). */
    public suspend fun export(output: OutputStream): Result<ExportSummary> = withContext(runtime.io) {
        val now = runtime.clock.now()
        try {
            val summary = exporter.export(output, now)
            database.appMetaQueries.setLastExportAt(now.toEpochMilliseconds())
            summary.asSuccess()
        } catch (error: IOException) {
            DomainError.BackupRejected(BackupRejection.EXPORT_FAILED, error.message).asFailure()
        }
    }

    /** Valida [archive] inteiro e calcula o efeito do import, sem escrever nada. */
    public suspend fun plan(archive: File, mode: ImportMode): Result<ImportPlan> = withContext(runtime.io) {
        rejecting(BackupRejection.UNREADABLE) {
            val prepared = validator.prepare(archive)
            try {
                planOf(prepared, archive, mode)
            } finally {
                prepared.close()
            }
        }
    }

    /** Valida de novo (o arquivo pode ter mudado desde a prévia) e aplica. */
    public suspend fun apply(plan: ImportPlan): Result<ImportSummary> = withContext(runtime.io) {
        rejecting(BackupRejection.IMPORT_FAILED) {
            val prepared = validator.prepare(plan.archive)
            try {
                applyPrepared(prepared, plan.mode)
            } finally {
                prepared.close()
            }
        }
    }

    /** Completa ou descarta mídias de um import interrompido. Chamado na abertura do app. */
    public suspend fun recoverInterruptedImports(): Unit = withContext(runtime.io) {
        val referenced = referencedPaths()
        for (path in media.listAll()) {
            if (path.startsWith(BackupFormat.STAGING_PREFIX)) recover(path, referenced)
        }
    }

    /** Apaga arquivos que nenhum anexo referencia (nem na lixeira). Devolve quantos saíram. */
    public suspend fun sweepOrphanMedia(): Int = withContext(runtime.io) {
        val referenced = referencedPaths()
        var removed = 0
        for (path in media.listAll()) {
            if (!path.startsWith(BackupFormat.STAGING_PREFIX) && path !in referenced) {
                media.delete(path)
                removed++
            }
        }
        removed
    }

    private fun planOf(prepared: PreparedImport, archive: File, mode: ImportMode): ImportPlan = ImportPlan(
        archive = archive,
        mode = mode,
        schemaVersion = prepared.manifest.schemaVersion,
        appVersion = prepared.manifest.appVersion,
        exportedAt = prepared.exportedAt,
        tables = importer.count(prepared, mode),
        mediaToAdd = prepared.manifest.media.count { !media.exists(it.path) },
        missingMedia = prepared.manifest.missingMedia.size,
    )

    private suspend fun applyPrepared(prepared: PreparedImport, mode: ImportMode): ImportSummary {
        val staging = "${BackupFormat.STAGING_PREFIX}${Uuid.random()}/"
        val staged = mutableListOf<String>()
        var committed = false
        val tables = try {
            stage(prepared, staging, staged)
            importer.apply(prepared, mode).also { committed = true }
        } finally {
            if (!committed) discard(staging, staged)
        }
        for (path in staged) media.move(staging + path, path)
        if (mode == ImportMode.REPLACE) sweepOrphanMedia()
        seeder.seed()
        return ImportSummary(tables, staged.size)
    }

    /** Grava em staging as mídias que o aparelho ainda não tem. Arquivos existentes não são tocados. */
    private suspend fun stage(prepared: PreparedImport, staging: String, staged: MutableList<String>) {
        for (file in prepared.manifest.media) {
            if (!media.exists(file.path)) {
                staged += file.path
                val checksum = writeEntry(prepared, file, staging + file.path)
                if (checksum != file.sha256) abort(BackupRejection.CHECKSUM_MISMATCH, file.path)
            }
        }
    }

    private suspend fun writeEntry(prepared: PreparedImport, file: MediaEntry, target: String): String {
        val input = prepared.zip.getInputStream(prepared.zip.getEntry(BackupFormat.MEDIA_PREFIX + file.path))
        return try {
            media.write(target, input)
        } finally {
            input.close()
        }
    }

    private suspend fun discard(staging: String, staged: List<String>) {
        for (path in staged) media.delete(staging + path)
    }

    private suspend fun recover(staged: String, referenced: Set<String>) {
        val target = staged.substringAfter('/')
        if (target in referenced && !media.exists(target)) media.move(staged, target) else media.delete(staged)
    }

    private fun referencedPaths(): Set<String> = database.mediaQueries.selectAllPaths().executeAsList().toSet()

    private suspend fun <T> rejecting(onIoError: BackupRejection, block: suspend () -> T): Result<T> = try {
        block().asSuccess()
    } catch (error: BackupAbort) {
        DomainError.BackupRejected(error.reason, error.detail).asFailure()
    } catch (error: IOException) {
        DomainError.BackupRejected(onIoError, error.message).asFailure()
    }
}

/** Relógio, dispatcher e versão do app, agrupados para o construtor ficar curto. */
public data class BackupRuntime(val clock: Clock, val io: CoroutineDispatcher, val appInfo: AppInfo)
