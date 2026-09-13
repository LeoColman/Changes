// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data.backup

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import br.com.colman.changes.core.db.ScratchDriverFactory
import br.com.colman.changes.core.db.Seeder
import br.com.colman.changes.core.db.createDatabase
import br.com.colman.changes.core.db.sql.ChangesDatabase
import br.com.colman.changes.core.media.MediaStorage
import br.com.colman.changes.core.model.BackupRejection
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.errorOrNull
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.core.testing.FileMediaStorage
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.inMemoryDriver
import br.com.colman.changes.core.testing.testDataset
import br.com.colman.changes.core.testing.testLabels
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

internal fun jdbcScratch(): ScratchDriverFactory = ScratchDriverFactory {
    JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { setProperty("foreign_keys", "true") })
}

/** Iterações dos property tests de backup: 1000 por padrão (Seção 11.2), 20 sob o Pitest. */
internal fun roundTripIterations(): Int = if (System.getProperty("changes.pitest") == "true") {
    20
} else {
    System.getProperty("changes.roundtrip.iterations")?.toIntOrNull() ?: 1000
}

/** Um aparelho de teste: banco seedado, pasta de mídia própria e repositório de backup. */
internal class Device(
    io: CoroutineDispatcher,
    val clock: FixedClock = FixedClock(),
    storage: (FileMediaStorage) -> MediaStorage = { it },
    scratch: ScratchDriverFactory = jdbcScratch(),
) {
    val driver: SqlDriver = inMemoryDriver()
    val database: ChangesDatabase = createDatabase(driver)
    val media = FileMediaStorage(Files.createTempDirectory("media").toFile())
    val backup = BackupRepository(
        driver = driver,
        scratch = scratch,
        media = storage(media),
        seeder = Seeder(database, testDataset, testLabels, clock),
        runtime = BackupRuntime(clock, io, AppInfo("test")),
    )

    init {
        Seeder(database, testDataset, testLabels, clock).seed()
    }

    /** Dump ordenado de todas as tabelas, exceto `app_meta` (guarda a data do último export). */
    fun dump(): Map<String, List<Row>> {
        val raw = RawDatabase(driver)
        return (raw.tables() - "app_meta").sorted().associateWith { table ->
            val rows = mutableListOf<Row>()
            raw.forEachRow(table, raw.columns(table)) { rows += it }
            rows
        }
    }

    fun mediaChecksums(): Map<String, String> = media.listAll().associateWith { path ->
        media.openRead(path)!!.use { sha(it.readBytes()) }
    }

    suspend fun exportBytes(): ByteArray {
        val out = ByteArrayOutputStream()
        backup.export(out).getOrNull().shouldNotBeNull()
        return out.toByteArray()
    }

    /** Motivo da recusa na prévia, ou `null` se o arquivo foi aceito. */
    suspend fun rejection(archive: ByteArray, mode: ImportMode = ImportMode.MERGE): BackupRejection? =
        (backup.plan(archiveOf(archive), mode).errorOrNull() as? DomainError.BackupRejected)?.reason

    suspend fun import(archive: ByteArray, mode: ImportMode): ImportSummary {
        val plan = backup.plan(archiveOf(archive), mode).getOrNull().shouldNotBeNull()
        return backup.apply(plan).getOrNull().shouldNotBeNull()
    }

    /** Muda [column] da linha [id] de [table] e avança `updated_at` em [by] ms, pelo driver cru. */
    fun touch(table: String, id: String, column: String, value: Any?, by: Long) {
        val raw = RawDatabase(driver)
        val columns = raw.columns(table)
        val row = raw.rowWhere(table, columns, RawDatabase.ID, id)!!.toMutableMap()
        row[column] = value
        row["updated_at"] = (row.getValue("updated_at") as Long) + by
        raw.update(table, columns, row)
    }
}

internal fun TestScope.device(
    storage: (FileMediaStorage) -> MediaStorage = { it },
    scratch: ScratchDriverFactory = jdbcScratch(),
): Device = Device(StandardTestDispatcher(testScheduler), storage = storage, scratch = scratch)

internal fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

internal fun archiveOf(bytes: ByteArray): File = File.createTempFile("backup", ".ttbackup.zip").apply {
    deleteOnExit()
    writeBytes(bytes)
}

// ------------------------------------------------------------------------------------------------
// Arquivos adulterados. Entradas do zip em ordem; `editData` recalcula checksum e contagens de
// data.json no manifesto, para testar a validação que vem depois do checksum.
// ------------------------------------------------------------------------------------------------

internal typealias Entries = LinkedHashMap<String, ByteArray>

internal fun unzip(bytes: ByteArray): Entries {
    val entries = Entries()
    ZipFile(archiveOf(bytes)).use { zip ->
        for (entry in zip.entries()) entries[entry.name] = zip.getInputStream(entry).readBytes()
    }
    return entries
}

internal fun zipOf(entries: Map<String, ByteArray>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        entries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name))
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

internal fun Entries.tampered(edit: Entries.() -> Unit): ByteArray = zipOf(Entries(this).apply(edit))

internal fun Entries.manifest(): JsonObject = Json.parseToJsonElement(
    getValue(BackupFormat.MANIFEST_ENTRY).decodeToString()
).jsonObject

internal fun Entries.data(): JsonObject = Json.parseToJsonElement(
    getValue(BackupFormat.DATA_ENTRY).decodeToString()
).jsonObject

internal fun Entries.editManifest(edit: (JsonObject) -> JsonObject) {
    this[BackupFormat.MANIFEST_ENTRY] = edit(manifest()).toString().toByteArray()
}

/** Troca `data.json` e acerta o checksum no manifesto; as contagens também, se [recount]. */
internal fun Entries.replaceData(bytes: ByteArray, recount: Boolean = true) {
    this[BackupFormat.DATA_ENTRY] = bytes
    editManifest { manifest ->
        val withChecksum = manifest + ("dataSha256" to JsonPrimitive(sha(bytes)))
        if (recount) withChecksum + ("counts" to countsOf(bytes)) else withChecksum
    }
}

internal fun Entries.editData(edit: (JsonObject) -> JsonObject) = replaceData(edit(data()).toString().toByteArray())

private fun countsOf(data: ByteArray): JsonObject {
    val tables = Json.parseToJsonElement(data.decodeToString()).jsonObject
    return JsonObject(tables.mapValues { JsonPrimitive((it.value as? JsonArray)?.size ?: 0) })
}

/** Edita a primeira linha de [table] que satisfaz [where]. */
internal fun JsonObject.editRow(
    table: String,
    where: (JsonObject) -> Boolean = { true },
    edit: (JsonObject) -> JsonObject,
): JsonObject {
    val rows = getValue(table).jsonArray.map { it.jsonObject }.toMutableList()
    val index = rows.indexOfFirst(where)
    check(index >= 0) { "No row in $table matches" }
    rows[index] = edit(rows[index])
    return this + (table to JsonArray(rows))
}

internal operator fun JsonObject.plus(entry: Pair<String, JsonElement>): JsonObject =
    JsonObject(LinkedHashMap(this).apply { put(entry.first, entry.second) })

/** Estraga o bloco deflate da primeira entrada: o zip abre, mas ler a entrada lança `ZipException`. */
internal fun corruptFirstEntry(zip: ByteArray): ByteArray {
    val copy = zip.copyOf()
    val nameLength = (copy[26].toInt() and 0xFF) or ((copy[27].toInt() and 0xFF) shl 8)
    val extraLength = (copy[28].toInt() and 0xFF) or ((copy[29].toInt() and 0xFF) shl 8)
    val start = 30 + nameLength + extraLength
    copy[start] = (copy[start].toInt() or 0b110).toByte() // BTYPE = 11, reservado pelo formato deflate
    return copy
}

// ------------------------------------------------------------------------------------------------
// Falhas injetadas
// ------------------------------------------------------------------------------------------------

/** Grava outro conteúdo no lugar do pedido: o checksum devolvido não bate. */
internal class CorruptingStorage(private val inner: MediaStorage) : MediaStorage by inner {
    override suspend fun write(relativePath: String, source: InputStream): String =
        inner.write(relativePath, "corrupted".byteInputStream())
}

/** Disco cheio. */
internal class FailingStorage(private val inner: MediaStorage) : MediaStorage by inner {
    override suspend fun write(relativePath: String, source: InputStream): String = throw IOException("disk full")
}

/** Guarda os bancos temporários criados, para verificar que foram fechados. */
internal class TrackingScratch : ScratchDriverFactory {
    val created = mutableListOf<SqlDriver>()

    override fun create(): SqlDriver = jdbcScratch().create().also { created += it }
}

internal fun SqlDriver.isClosed(): Boolean =
    runCatching { executeQuery(null, "SELECT 1", { QueryResult.Unit }, 0) }.isFailure
