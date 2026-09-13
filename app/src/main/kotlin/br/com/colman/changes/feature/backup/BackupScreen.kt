// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.colman.changes.R
import br.com.colman.changes.core.data.backup.ExportSummary
import br.com.colman.changes.core.data.backup.ImportMode
import br.com.colman.changes.ui.components.ChangesScreen
import br.com.colman.changes.ui.components.FormColumn
import br.com.colman.changes.ui.components.LoadingState
import br.com.colman.changes.ui.components.SectionHeader

private val IMPORT_MIME_TYPES = arrayOf("application/zip", "application/octet-stream")

/**
 * Tela Backup (Seção 7.8): exportar tudo para um arquivo `.ttbackup.zip` ou escolher um arquivo
 * para importar. Sem estado próprio de negócio: os seletores de documento (SAF) só traduzem o
 * resultado do sistema em [BackupUiEvent].
 */
@Composable
fun BackupScreen(
    state: BackupUiState,
    onEvent: (BackupUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) onEvent(BackupUiEvent.ExportRequested(uri.toString()))
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onEvent(BackupUiEvent.ImportFilePicked(uri.toString()))
    }

    Box(modifier) {
        ChangesScreen(title = stringResource(R.string.backup_title), onBack = onBack) { padding ->
            if (state.isExporting || state.isPlanning) {
                LoadingState(Modifier.padding(padding))
            } else {
                BackupForm(
                    state = state,
                    onEvent = onEvent,
                    onExport = { exportLauncher.launch(state.suggestedFileName) },
                    onImport = { importLauncher.launch(IMPORT_MIME_TYPES) },
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun BackupForm(
    state: BackupUiState,
    onEvent: (BackupUiEvent) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FormColumn(modifier.verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.backup_explanation))

        SectionHeader(stringResource(R.string.backup_export_section))
        Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.backup_export_action))
        }
        state.exportSummary?.let { ExportSummaryText(it) }

        SectionHeader(stringResource(R.string.backup_import_section))
        if (state.choosingImportMode) {
            ImportModeChooser(onEvent)
        } else {
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.backup_import_action))
            }
        }

        state.error?.let { Text(it.backupMessage(), color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun ExportSummaryText(summary: ExportSummary) {
    val totalRows = summary.rows.values.sum()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(pluralStringResource(R.plurals.backup_export_summary_records, totalRows, totalRows))
        Text(pluralStringResource(R.plurals.backup_export_summary_photos, summary.mediaFiles, summary.mediaFiles))
        if (summary.missingMedia > 0) {
            Text(
                pluralStringResource(
                    R.plurals.backup_export_summary_missing_media,
                    summary.missingMedia,
                    summary.missingMedia
                ),
            )
        }
    }
}

@Composable
private fun ImportModeChooser(onEvent: (BackupUiEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.backup_import_mode_prompt))
        ImportModeOption(
            title = stringResource(R.string.backup_import_mode_merge_title),
            description = stringResource(R.string.backup_import_mode_merge_description),
            onClick = { onEvent(BackupUiEvent.ImportModeChosen(ImportMode.MERGE)) },
        )
        ImportModeOption(
            title = stringResource(R.string.backup_import_mode_replace_title),
            description = stringResource(R.string.backup_import_mode_replace_description),
            onClick = { onEvent(BackupUiEvent.ImportModeChosen(ImportMode.REPLACE)) },
        )
        TextButton(onClick = { onEvent(BackupUiEvent.CancelImportPick) }) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

@Composable
private fun ImportModeOption(title: String, description: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column {
            Text(title)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}
