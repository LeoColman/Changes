// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.backup

import androidx.compose.runtime.Immutable
import br.com.colman.changes.core.data.backup.ExportSummary
import br.com.colman.changes.core.data.backup.ImportMode
import br.com.colman.changes.core.model.BackupRejection

/**
 * Estado da tela Backup (Seção 7.8, ADR 0009): exportar tudo para um arquivo `.ttbackup.zip` ou
 * escolher um arquivo para importar. [choosingImportMode] fica `true` depois que o arquivo é
 * copiado para o cache e antes da pessoa escolher Mesclar ou Substituir.
 */
@Immutable
data class BackupUiState(
    val suggestedFileName: String = "",
    val isExporting: Boolean = false,
    val isPlanning: Boolean = false,
    val exportSummary: ExportSummary? = null,
    val choosingImportMode: Boolean = false,
    val error: BackupRejection? = null,
)

/** Ações da tela Backup. */
sealed interface BackupUiEvent {
    /** `uri` do documento criado pela pessoa (`ActivityResultContracts.CreateDocument`). */
    data class ExportRequested(val uri: String) : BackupUiEvent

    /** `uri` do documento escolhido pela pessoa (`ActivityResultContracts.OpenDocument`). */
    data class ImportFilePicked(val uri: String) : BackupUiEvent

    /** Mesclar ou Substituir, escolhido depois que o arquivo já está no cache privado. */
    data class ImportModeChosen(val mode: ImportMode) : BackupUiEvent
    data object CancelImportPick : BackupUiEvent
    data object DismissError : BackupUiEvent
}

/** Efeito de uma vez: abrir a prévia depois que o plano de import está pronto. */
sealed interface BackupEffect {
    data object OpenImportPreview : BackupEffect
}
