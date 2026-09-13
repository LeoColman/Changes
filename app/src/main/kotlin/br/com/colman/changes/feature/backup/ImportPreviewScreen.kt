// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.backup

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import br.com.colman.changes.R
import br.com.colman.changes.core.data.backup.ImportMode
import br.com.colman.changes.core.data.backup.ImportSummary
import br.com.colman.changes.core.data.backup.TableCounts
import br.com.colman.changes.ui.components.ChangesScreen
import br.com.colman.changes.ui.components.ConfirmDialog
import br.com.colman.changes.ui.components.FormColumn
import br.com.colman.changes.ui.components.LoadingState
import br.com.colman.changes.ui.components.SectionHeader
import br.com.colman.changes.ui.components.TypedConfirmDialog
import br.com.colman.changes.ui.format.Formatters

/**
 * Prévia do import (Seção 7.8, ADR 0009): mostra o que vai acontecer antes de aplicar, e o resumo
 * final depois. A confirmação usa `ConfirmDialog` no Mesclar e `TypedConfirmDialog` no Substituir
 * (a pessoa precisa digitar a palavra exigida para o botão habilitar).
 */
@Composable
fun ImportPreviewScreen(
    state: ImportPreviewUiState,
    onEvent: (ImportPreviewUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        ChangesScreen(title = stringResource(R.string.backup_preview_title), onBack = onBack) { padding ->
            val result = state.result
            if (result != null) {
                ImportResultContent(result, onEvent, Modifier.padding(padding))
            } else {
                ImportPlanContent(state, onEvent, Modifier.padding(padding))
            }
        }
        if (state.showConfirm) {
            ImportConfirmDialog(state.mode, onEvent)
        }
    }
}

@Composable
private fun ImportPlanContent(
    state: ImportPreviewUiState,
    onEvent: (ImportPreviewUiEvent) -> Unit,
    modifier: Modifier
) {
    FormColumn(modifier.verticalScroll(rememberScrollState())) {
        ImportPlanHeader(state)

        SectionHeader(stringResource(R.string.backup_preview_tables_title))
        state.tables.forEach { TableCountsRow(it) }
        if (state.mediaToAdd > 0) {
            Text(pluralStringResource(R.plurals.backup_stat_media_to_add, state.mediaToAdd, state.mediaToAdd))
        }
        if (state.missingMedia > 0) {
            Text(pluralStringResource(R.plurals.backup_stat_missing_media, state.missingMedia, state.missingMedia))
        }
        state.error?.let { Text(it.backupMessage(), color = MaterialTheme.colorScheme.error) }

        if (state.isApplying) {
            LoadingState()
        } else {
            Button(onClick = { onEvent(ImportPreviewUiEvent.RequestApply) }, modifier = Modifier.fillMaxWidth()) {
                Text(applyActionLabel(state.mode))
            }
        }
    }
}

@Composable
private fun ImportPlanHeader(state: ImportPreviewUiState) {
    val exportedAt = state.exportedAt
    Text(
        stringResource(
            R.string.backup_preview_info,
            exportedAt?.let { Formatters.dateTime(it) }.orEmpty(),
            state.appVersion,
        ),
    )
    Text(
        if (state.mode == ImportMode.MERGE) {
            stringResource(R.string.backup_import_mode_merge_description)
        } else {
            stringResource(R.string.backup_import_mode_replace_description)
        },
    )
}

@Composable
private fun applyActionLabel(mode: ImportMode): String = if (mode == ImportMode.MERGE) {
    stringResource(R.string.backup_action_merge)
} else {
    stringResource(R.string.backup_action_replace)
}

@Composable
private fun ImportResultContent(result: ImportSummary, onEvent: (ImportPreviewUiEvent) -> Unit, modifier: Modifier) {
    FormColumn(modifier.verticalScroll(rememberScrollState())) {
        SectionHeader(stringResource(R.string.backup_result_title))
        result.tables.forEach { TableCountsRow(it) }
        Text(pluralStringResource(R.plurals.backup_stat_media_added, result.mediaAdded, result.mediaAdded))
        Button(onClick = { onEvent(ImportPreviewUiEvent.Done) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.backup_action_done))
        }
    }
}

private val STAT_PLURALS = listOf(
    TableCounts::inserted to R.plurals.backup_stat_inserted,
    TableCounts::updated to R.plurals.backup_stat_updated,
    TableCounts::unchanged to R.plurals.backup_stat_unchanged,
    TableCounts::kept to R.plurals.backup_stat_kept,
    TableCounts::removed to R.plurals.backup_stat_removed,
)

@Composable
private fun TableCountsRow(counts: TableCounts) {
    Text(backupTableName(counts.table), style = MaterialTheme.typography.titleSmall)
    val stats = STAT_PLURALS.mapNotNull { (field, res) -> field.get(counts).takeIf { it > 0 }?.let { it to res } }
        .map { (value, res) -> pluralStringResource(res, value, value) }
    val summary = if (stats.isEmpty()) stringResource(R.string.backup_stat_none) else stats.joinToString(", ")
    Text(summary, style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun ImportConfirmDialog(mode: ImportMode, onEvent: (ImportPreviewUiEvent) -> Unit) {
    if (mode == ImportMode.MERGE) {
        ConfirmDialog(
            title = stringResource(R.string.backup_confirm_merge_title),
            text = stringResource(R.string.backup_confirm_merge_body),
            confirmLabel = stringResource(R.string.backup_action_merge),
            onConfirm = { onEvent(ImportPreviewUiEvent.ConfirmApply) },
            onDismiss = { onEvent(ImportPreviewUiEvent.DismissConfirm) },
        )
    } else {
        TypedConfirmDialog(
            title = stringResource(R.string.backup_confirm_replace_title),
            text = stringResource(R.string.backup_confirm_replace_body),
            requiredText = stringResource(R.string.backup_replace_confirm_word),
            confirmLabel = stringResource(R.string.backup_action_replace),
            onConfirm = { onEvent(ImportPreviewUiEvent.ConfirmApply) },
            onDismiss = { onEvent(ImportPreviewUiEvent.DismissConfirm) },
        )
    }
}
