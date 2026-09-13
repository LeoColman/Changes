// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data.backup

import kotlinx.serialization.Serializable
import java.io.File
import kotlin.time.Instant

public enum class ImportMode { MERGE, REPLACE }

/** Efeito do import sobre uma tabela. `kept` = a versão do aparelho venceu; `removed` só no modo Substituir. */
public data class TableCounts(
    val table: String,
    val inserted: Int = 0,
    val updated: Int = 0,
    val unchanged: Int = 0,
    val kept: Int = 0,
    val removed: Int = 0,
)

public data class ExportSummary(val rows: Map<String, Int>, val mediaFiles: Int, val missingMedia: Int)

/** O que o import vai fazer, calculado sem escrever nada (tela de prévia, Seção 7.8). */
public data class ImportPlan(
    val archive: File,
    val mode: ImportMode,
    val schemaVersion: Long,
    val appVersion: String,
    val exportedAt: Instant,
    val tables: List<TableCounts>,
    val mediaToAdd: Int,
    val missingMedia: Int,
)

public data class ImportSummary(val tables: List<TableCounts>, val mediaAdded: Int)

/** Versão do app que gera o backup (vai no manifesto). */
public data class AppInfo(val versionName: String)

internal object BackupFormat {
    const val FORMAT: String = "changes-backup"
    const val FORMAT_VERSION: Int = 1
    const val DATA_ENTRY: String = "data.json"
    const val MANIFEST_ENTRY: String = "manifest.json"
    const val MEDIA_PREFIX: String = "media/"
    const val STAGING_PREFIX: String = ".import-"

    /** Tabelas exportadas, em ordem compatível com as FKs (pais antes de filhos). */
    val EXPORTED: List<String> = listOf(
        "profile",
        "medication",
        "regimen",
        "dose_log",
        "body_change_type",
        "body_change_entry",
        "media_attachment",
        "measurement",
        "exercise_session",
        "health_condition",
        "lab_analyte",
        "lab_result",
        "mood_log",
        "calendar_event",
    )

    /** Dados do app, não da pessoa: reseedados, nunca exportados. */
    val INTERNAL: Set<String> = setOf("app_meta", "expected_change")

    /** Chave natural além do id: outra linha na mesma data é a mesma entrada (Seção 7.7). */
    val NATURAL_KEYS: Map<String, String> = mapOf("mood_log" to "entry_date")

    /** Colunas UNIQUE de catálogo: colisão com id diferente só acontece com arquivo adulterado. */
    val UNIQUE_CODES: Map<String, String> = mapOf("body_change_type" to "code", "lab_analyte" to "code")
}

@Serializable
internal data class BackupManifest(
    val format: String,
    val formatVersion: Int,
    val schemaVersion: Long,
    val appVersion: String,
    val exportedAt: String,
    val counts: Map<String, Int>,
    val dataSha256: String,
    val media: List<MediaEntry>,
    /** Anexos cujo arquivo já não existia no aparelho de origem. Declarados para não parecer adulteração. */
    val missingMedia: List<String> = emptyList(),
)

@Serializable
internal data class MediaEntry(val path: String, val sha256: String, val size: Long)
