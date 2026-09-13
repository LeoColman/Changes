// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.backup

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import br.com.colman.changes.R

/** Nome amigável de cada tabela exportada (Seção 7.8, ADR 0009), pela chave usada no banco. */
private val TABLE_NAME_RES = mapOf(
    "profile" to R.string.backup_table_profile,
    "medication" to R.string.backup_table_medication,
    "regimen" to R.string.backup_table_regimen,
    "dose_log" to R.string.backup_table_dose_log,
    "body_change_type" to R.string.backup_table_body_change_type,
    "body_change_entry" to R.string.backup_table_body_change_entry,
    "media_attachment" to R.string.backup_table_media_attachment,
    "measurement" to R.string.backup_table_measurement,
    "exercise_session" to R.string.backup_table_exercise_session,
    "health_condition" to R.string.backup_table_health_condition,
    "lab_analyte" to R.string.backup_table_lab_analyte,
    "lab_result" to R.string.backup_table_lab_result,
    "mood_log" to R.string.backup_table_mood_log,
    "calendar_event" to R.string.backup_table_calendar_event,
)

/** Uma chave desconhecida (tabela nova ainda sem tradução) cai num rótulo genérico. */
@Composable
fun backupTableName(table: String): String = stringResource(TABLE_NAME_RES[table] ?: R.string.backup_table_generic)
