// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.backup

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import br.com.colman.changes.core.data.backup.AppInfo
import br.com.colman.changes.core.data.backup.BackupRepository
import br.com.colman.changes.core.data.backup.BackupRuntime
import br.com.colman.changes.core.data.backup.ImportMode
import br.com.colman.changes.core.data.backup.ImportPlan
import br.com.colman.changes.core.db.ScratchDriverFactory
import br.com.colman.changes.core.db.Seeder
import br.com.colman.changes.core.db.createDatabase
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.core.testing.FileMediaStorage
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.inMemoryDriver
import br.com.colman.changes.core.testing.testDataset
import br.com.colman.changes.core.testing.testLabels
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.CoroutineDispatcher
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val DATA_ENTRY_NAME = "data.json"

/** Repositório de backup real sobre um banco em memória seedado (Seção 7.8), para os specs de ViewModel. */
fun testBackupRepository(io: CoroutineDispatcher, clock: FixedClock = FixedClock()): BackupRepository {
    val driver = inMemoryDriver()
    val database = createDatabase(driver)
    Seeder(database, testDataset, testLabels, clock).seed()
    return BackupRepository(
        driver = driver,
        scratch = ScratchDriverFactory { JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY) },
        media = FileMediaStorage(Files.createTempDirectory("backup-media").toFile()),
        seeder = Seeder(database, testDataset, testLabels, clock),
        runtime = BackupRuntime(clock, io, AppInfo("test")),
    )
}

fun newCacheDir(): File = Files.createTempDirectory("backup-cache").toFile()

/** Exporta [repository] para um arquivo em [cacheDir] e planeja o import no modo [mode]. */
suspend fun planFor(repository: BackupRepository, cacheDir: File, mode: ImportMode): ImportPlan {
    val bytes = ByteArrayOutputStream().also { repository.export(it) }.toByteArray()
    val archive = File.createTempFile("backup", ".ttbackup.zip", cacheDir).apply { writeBytes(bytes) }
    return repository.plan(archive, mode).getOrNull().shouldNotBeNull()
}

/**
 * Estraga o checksum de `data.json` (critério 7.8.3): troca um byte do meio da entrada e reescreve
 * o zip com o manifesto original, cujo `dataSha256` fica então divergente.
 */
fun tamperDataEntry(bytes: ByteArray): ByteArray {
    val entries = LinkedHashMap<String, ByteArray>()
    ZipInputStream(bytes.inputStream()).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            entries[entry.name] = zip.readBytes()
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
    val data = entries.getValue(DATA_ENTRY_NAME).copyOf()
    val middle = data.size / 2
    data[middle] = data[middle].inc()
    entries[DATA_ENTRY_NAME] = data

    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
        entries.forEach { (name, content) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(content)
            zip.closeEntry()
        }
    }
    return output.toByteArray()
}
