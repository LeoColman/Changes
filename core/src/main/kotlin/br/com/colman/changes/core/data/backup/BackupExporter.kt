// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data.backup

import app.cash.sqldelight.db.SqlDriver
import br.com.colman.changes.core.db.CURRENT_SCHEMA_VERSION
import br.com.colman.changes.core.media.MediaStorage
import br.com.colman.changes.core.model.MediaPaths
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.security.DigestOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.time.Instant

private const val ATTACHMENTS = "media_attachment"
private const val PATH = "relative_path"
private const val DELETED_AT = "deleted_at"

/**
 * Export em streaming (Seção 7.8, critério 7.8.5): linhas lidas por cursor e escritas uma a uma;
 * mídia copiada em blocos com hash calculado na cópia; manifesto por último (ADR 0009).
 */
internal class BackupExporter(
    private val driver: SqlDriver,
    private val media: MediaStorage,
    private val appInfo: AppInfo,
) {
    private val json = Json

    /** Escreve o backup completo em [output] e fecha o stream. */
    fun export(output: OutputStream, exportedAt: Instant): ExportSummary {
        val raw = RawDatabase(driver)
        val zip = ZipOutputStream(output.buffered())
        try {
            val data = writeData(zip, raw)
            val files = writeMedia(zip, raw)
            writeManifest(zip, manifestOf(data, files, exportedAt))
            return ExportSummary(data.counts, files.entries.size, files.missing.size)
        } finally {
            zip.close()
        }
    }

    private class DataResult(val counts: Map<String, Int>, val sha256: String)

    private class MediaResult(val entries: List<MediaEntry>, val missing: List<String>)

    private fun manifestOf(data: DataResult, files: MediaResult, exportedAt: Instant) = BackupManifest(
        format = BackupFormat.FORMAT,
        formatVersion = BackupFormat.FORMAT_VERSION,
        schemaVersion = CURRENT_SCHEMA_VERSION,
        appVersion = appInfo.versionName,
        exportedAt = exportedAt.toString(),
        counts = data.counts,
        dataSha256 = data.sha256,
        media = files.entries,
        missingMedia = files.missing,
    )

    private fun writeData(zip: ZipOutputStream, raw: RawDatabase): DataResult {
        zip.putNextEntry(ZipEntry(BackupFormat.DATA_ENTRY))
        val digest = sha256()
        // Sem fechar o writer: fecharia o zip. Só flush no fim da entrada.
        val writer = OutputStreamWriter(DigestOutputStream(zip, digest), Charsets.UTF_8).buffered()
        val counts = LinkedHashMap<String, Int>()
        writer.write("{")
        BackupFormat.EXPORTED.forEachIndexed { index, table ->
            if (index > 0) writer.write(",")
            writer.write(json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(table)))
            writer.write(":[")
            val columns = raw.columns(table)
            var count = 0
            raw.forEachRow(table, columns) { row ->
                if (count > 0) writer.write(",")
                writer.write(json.encodeToString(JsonObject.serializer(), RowJson.encode(columns, row)))
                count++
            }
            writer.write("]")
            counts[table] = count
        }
        writer.write("}")
        writer.flush()
        zip.closeEntry()
        return DataResult(counts, digest.digest().toHex())
    }

    /**
     * Todo arquivo referenciado por anexo, inclusive na lixeira, em ordem de caminho. Arquivo que já não
     * existe: de item purgado (tombstone) não faz falta; de item vivo é declarado em `missingMedia`.
     */
    private fun writeMedia(zip: ZipOutputStream, raw: RawDatabase): MediaResult {
        val alive = sortedMapOf<String, Boolean>()
        raw.forEachRow(ATTACHMENTS, raw.columns(ATTACHMENTS)) { row ->
            alive[row.getValue(PATH) as String] = row[DELETED_AT] == null
        }
        val entries = mutableListOf<MediaEntry>()
        val missing = mutableListOf<String>()
        for ((path, isAlive) in alive) {
            val input = if (MediaPaths.isValid(path)) media.openRead(path) else null
            if (input != null) {
                zip.putNextEntry(ZipEntry(BackupFormat.MEDIA_PREFIX + path))
                val digest = sha256()
                val size = copyHashing(input, zip, digest)
                zip.closeEntry()
                entries += MediaEntry(path, digest.digest().toHex(), size)
            } else if (isAlive) {
                missing += path
            }
        }
        return MediaResult(entries, missing)
    }

    private fun writeManifest(zip: ZipOutputStream, manifest: BackupManifest) {
        zip.putNextEntry(ZipEntry(BackupFormat.MANIFEST_ENTRY))
        zip.write(json.encodeToString(BackupManifest.serializer(), manifest).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}
