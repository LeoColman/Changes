// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data.backup

import app.cash.sqldelight.db.SqlDriver
import br.com.colman.changes.core.data.toModel
import br.com.colman.changes.core.db.CURRENT_SCHEMA_VERSION
import br.com.colman.changes.core.db.SchemaHistory
import br.com.colman.changes.core.db.ScratchDriverFactory
import br.com.colman.changes.core.db.createDatabase
import br.com.colman.changes.core.db.sql.ChangesDatabase
import br.com.colman.changes.core.db.sql.Media_attachment
import br.com.colman.changes.core.model.BackupRejection
import br.com.colman.changes.core.model.MediaPaths
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.security.DigestInputStream
import java.util.zip.ZipFile
import kotlin.time.Instant

private const val MEBIBYTE = 1024L * 1024L
private const val MANIFEST_LIMIT = 16 * MEBIBYTE
private const val DATA_LIMIT = 64 * MEBIBYTE
private const val ATTACHMENTS = "media_attachment"
private const val PATH = "relative_path"
private const val CHECKSUM = "checksum_sha256"
private const val DELETED_AT = "deleted_at"

/** Tetos de leitura de arquivo não confiável. Parâmetro só para os testes exercitarem os limites. */
internal data class BackupLimits(val manifestBytes: Long = MANIFEST_LIMIT, val dataBytes: Long = DATA_LIMIT)

/** Backup validado e carregado num banco temporário na versão atual. Fechar libera o zip e o banco. */
internal class PreparedImport(
    val manifest: BackupManifest,
    val exportedAt: Instant,
    val zip: ZipFile,
    val scratch: SqlDriver,
) : Closeable {
    override fun close() {
        scratch.close()
        zip.close()
    }
}

/**
 * Validação de backups (ADR 0009). Nada toca o banco principal: o arquivo inteiro passa por manifesto,
 * versão, checksums, carga estrita num banco temporário com CHECK, UNIQUE e FK, migração até a versão
 * atual, leitura pelos mesmos mapeadores do app e verificação das mídias.
 */
internal class BackupValidator(
    private val scratchFactory: ScratchDriverFactory,
    private val limits: BackupLimits = BackupLimits(),
) {
    // Campos novos de um manifesto futuro não impedem ler a versão e rejeitar com NEWER_VERSION.
    private val json = Json { ignoreUnknownKeys = true }

    /** Valida [archive]. Em qualquer rejeição, zip e banco temporário são fechados antes de propagar. */
    fun prepare(archive: File): PreparedImport {
        val zip = openZip(archive)
        val scratch = scratchFactory.create()
        var valid = false
        try {
            val prepared = load(zip, scratch)
            valid = true
            return prepared
        } finally {
            if (!valid) {
                scratch.close()
                zip.close()
            }
        }
    }

    private fun load(zip: ZipFile, scratch: SqlDriver): PreparedImport {
        val manifest = readManifest(zip)
        val exportedAt = parseInstant(manifest.exportedAt)
        checkVersions(manifest)
        loadScratch(scratch, manifest, readData(zip, manifest))
        validateSemantics(scratch)
        verifyMedia(zip, manifest, scratch)
        return PreparedImport(manifest, exportedAt, zip, scratch)
    }

    private fun readManifest(zip: ZipFile): BackupManifest {
        val entry = zip.getEntry(BackupFormat.MANIFEST_ENTRY) ?: abort(BackupRejection.NOT_A_BACKUP, "manifest")
        val bytes = zip.getInputStream(entry).readAtMost(limits.manifestBytes)
            ?: abort(BackupRejection.NOT_A_BACKUP, "manifest size")
        val manifest = try {
            json.decodeFromString(BackupManifest.serializer(), bytes.decodeToString())
        } catch (error: IllegalArgumentException) {
            // SerializationException é subclasse de IllegalArgumentException.
            abort(BackupRejection.NOT_A_BACKUP, error.message)
        }
        if (manifest.format != BackupFormat.FORMAT) abort(BackupRejection.NOT_A_BACKUP, "format")
        return manifest
    }

    /** Existe DDL congelado para toda versão suportada, inclusive a atual (verificado em teste). */
    private fun checkVersions(manifest: BackupManifest) {
        val newer = manifest.formatVersion > BackupFormat.FORMAT_VERSION ||
            manifest.schemaVersion > CURRENT_SCHEMA_VERSION
        if (newer) abort(BackupRejection.NEWER_VERSION, "${manifest.formatVersion}/${manifest.schemaVersion}")
        if (manifest.formatVersion < 1) abort(BackupRejection.INVALID_DATA, "formatVersion")
        if (SchemaHistory.snapshot(manifest.schemaVersion) == null) {
            abort(BackupRejection.UNSUPPORTED_VERSION, manifest.schemaVersion.toString())
        }
    }

    private fun readData(zip: ZipFile, manifest: BackupManifest): JsonObject {
        val entry = zip.getEntry(BackupFormat.DATA_ENTRY) ?: abort(BackupRejection.NOT_A_BACKUP, "data")
        val digest = sha256()
        val bytes = DigestInputStream(zip.getInputStream(entry), digest).readAtMost(limits.dataBytes)
            ?: abort(BackupRejection.INVALID_DATA, "data size")
        if (digest.digest().toHex() != manifest.dataSha256) {
            abort(BackupRejection.CHECKSUM_MISMATCH, BackupFormat.DATA_ENTRY)
        }
        val element = try {
            json.parseToJsonElement(bytes.decodeToString())
        } catch (error: IllegalArgumentException) {
            abort(BackupRejection.INVALID_DATA, error.message)
        }
        return element as? JsonObject ?: abort(BackupRejection.INVALID_DATA, "data")
    }

    /**
     * Cria o schema da versão do backup, carrega as linhas com FKs desligadas (a ordem das tabelas não
     * importa), confere todas as referências de uma vez e migra até a versão atual.
     */
    private fun loadScratch(scratch: SqlDriver, manifest: BackupManifest, data: JsonObject) {
        SchemaHistory.create(scratch, manifest.schemaVersion)
        val raw = RawDatabase(scratch)
        val tables = raw.tables() - BackupFormat.INTERNAL
        if (data.keys != tables || manifest.counts.keys != tables) abort(BackupRejection.INVALID_DATA, "tables")
        scratch.execute(null, "PRAGMA foreign_keys = OFF", 0)
        createDatabase(scratch).transaction {
            for (table in tables.sorted()) {
                loadTable(raw, table, data.getValue(table), manifest.counts.getValue(table))
            }
        }
        if (raw.foreignKeyViolations() > 0) abort(BackupRejection.INVALID_DATA, "references")
        ChangesDatabase.Schema.migrate(scratch, manifest.schemaVersion, CURRENT_SCHEMA_VERSION)
    }

    private fun loadTable(raw: RawDatabase, table: String, element: JsonElement, expected: Int) {
        val rows = element as? JsonArray ?: abort(BackupRejection.INVALID_DATA, table)
        if (rows.size != expected) abort(BackupRejection.INVALID_DATA, "$table count")
        val columns = raw.columns(table)
        for (item in rows) {
            val row = (item as? JsonObject)?.let { RowJson.decode(columns, it) }
                ?: abort(BackupRejection.INVALID_DATA, table)
            guarded(table) { raw.insert(table, columns, row) }
        }
    }

    private fun validateSemantics(scratch: SqlDriver) {
        val database = createDatabase(scratch)
        for ((table, check) in SEMANTIC_CHECKS) guarded(table) { check(database) }
    }

    private fun verifyMedia(zip: ZipFile, manifest: BackupManifest, scratch: SqlDriver) {
        val declared = manifest.media.associateBy { it.path }
        if (declared.size != manifest.media.size) abort(BackupRejection.INVALID_DATA, "media")
        for (file in manifest.media) verifyFile(zip, file)
        val missing = manifest.missingMedia.toSet()
        val raw = RawDatabase(scratch)
        raw.forEachRow(ATTACHMENTS, raw.columns(ATTACHMENTS)) { verifyAttachment(it, declared, missing) }
    }
}

/**
 * Cada tabela lida pelos mapeadores do app: se o app não conseguiria exibir a linha (enum desconhecido,
 * UUID inválido, offset fora de ±18h, JSON corrompido, regra de recorrência inválida, caminho de mídia
 * fora da raiz), o backup é rejeitado antes de qualquer escrita.
 */
private val SEMANTIC_CHECKS: List<Pair<String, (ChangesDatabase) -> Unit>> = listOf(
    "profile" to { db ->
        db.profileQueries.selectAllForBackup().executeAsList().forEach { it.toModel() }
    },
    "medication" to { db ->
        db.medicationQueries.selectAllForBackup().executeAsList().forEach { it.toModel() }
    },
    "regimen" to { db ->
        db.regimenQueries.selectAllForBackup().executeAsList().forEach { it.toModel() }
    },
    "dose_log" to { db ->
        db.doseLogQueries.selectAllForBackup().executeAsList().forEach { it.toModel() }
    },
    "body_change_type" to { db ->
        db.bodyChangeQueries.selectAllTypesForBackup().executeAsList().forEach { it.toModel() }
    },
    "body_change_entry" to { db ->
        db.bodyChangeQueries.selectAllEntriesForBackup().executeAsList().forEach { it.toModel() }
    },
    "media_attachment" to { db ->
        db.mediaQueries.selectAllForBackup().executeAsList().forEach(::checkAttachmentRow)
    },
    "measurement" to { db ->
        db.measurementQueries.selectAllForBackup().executeAsList().forEach { it.toModel() }
    },
    "exercise_session" to { db ->
        db.exerciseQueries.selectAllForBackup().executeAsList().forEach { it.toModel() }
    },
    "health_condition" to { db ->
        db.healthConditionQueries.selectAllForBackup().executeAsList().forEach { it.toModel() }
    },
    "lab_analyte" to { db ->
        db.labQueries.selectAllAnalytesForBackup().executeAsList().forEach { it.toModel() }
    },
    "lab_result" to { db ->
        db.labQueries.selectAllResultsForBackup().executeAsList().forEach { it.toModel() }
    },
    "mood_log" to { db ->
        db.moodQueries.selectAllForBackup().executeAsList().forEach { it.toModel() }
    },
    "calendar_event" to { db ->
        db.calendarQueries.selectAllForBackup().executeAsList().forEach { it.toModel() }
    },
)

private fun checkAttachmentRow(row: Media_attachment) {
    require(MediaPaths.isValid(row.relative_path)) { "path" }
    row.toModel()
}

private fun openZip(archive: File): ZipFile = try {
    ZipFile(archive)
} catch (error: IOException) {
    abort(BackupRejection.UNREADABLE, error.message)
}

private fun parseInstant(text: String): Instant = try {
    Instant.parse(text)
} catch (error: IllegalArgumentException) {
    abort(BackupRejection.NOT_A_BACKUP, error.message)
}

private fun verifyFile(zip: ZipFile, file: MediaEntry) {
    if (!MediaPaths.isValid(file.path)) abort(BackupRejection.INVALID_DATA, file.path)
    val entry = zip.getEntry(BackupFormat.MEDIA_PREFIX + file.path)
        ?: abort(BackupRejection.MISSING_MEDIA, file.path)
    val digest = sha256()
    val size = copyHashing(zip.getInputStream(entry), null, digest)
    if (size != file.size || digest.digest().toHex() != file.sha256) {
        abort(BackupRejection.CHECKSUM_MISMATCH, file.path)
    }
}

/** Anexo com arquivo declarado tem o mesmo checksum; anexo vivo sem arquivo precisa estar declarado como ausente. */
private fun verifyAttachment(row: Row, declared: Map<String, MediaEntry>, missing: Set<String>) {
    val path = row.getValue(PATH) as String
    val file = declared[path]
    if (file != null && file.sha256 != row[CHECKSUM]) abort(BackupRejection.CHECKSUM_MISMATCH, path)
    if (file == null && row[DELETED_AT] == null && path !in missing) abort(BackupRejection.MISSING_MEDIA, path)
}
