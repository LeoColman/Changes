// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.backup

import androidx.compose.runtime.Immutable
import br.com.colman.changes.core.data.backup.ImportMode
import br.com.colman.changes.core.data.backup.ImportSummary
import br.com.colman.changes.core.data.backup.TableCounts
import br.com.colman.changes.core.model.BackupRejection
import kotlinx.datetime.LocalDateTime

/**
 * Estado da tela de prévia do import (Seção 7.8, ADR 0009): mostra o que vai acontecer antes de
 * aplicar. [result] só é preenchido depois que `apply` termina com sucesso; a partir daí a tela
 * mostra o resumo final em vez do plano.
 */
@Immutable
data class ImportPreviewUiState(
    val mode: ImportMode = ImportMode.MERGE,
    val schemaVersion: Long = 0,
    val appVersion: String = "",
    val exportedAt: LocalDateTime? = null,
    val tables: List<TableCounts> = emptyList(),
    val mediaToAdd: Int = 0,
    val missingMedia: Int = 0,
    val showConfirm: Boolean = false,
    val isApplying: Boolean = false,
    val result: ImportSummary? = null,
    val error: BackupRejection? = null,
)

/** Ações da tela de prévia do import. */
sealed interface ImportPreviewUiEvent {
    /** Abre o diálogo de confirmação certo para [ImportPreviewUiState.mode]. */
    data object RequestApply : ImportPreviewUiEvent
    data object DismissConfirm : ImportPreviewUiEvent

    /** Chamado só depois que o diálogo confirma (Mesclar: `ConfirmDialog`; Substituir: `TypedConfirmDialog`). */
    data object ConfirmApply : ImportPreviewUiEvent

    /** Sai da tela sem aplicar: apaga a cópia temporária do arquivo. */
    data object Cancel : ImportPreviewUiEvent

    /** Sai da tela depois do resumo final. */
    data object Done : ImportPreviewUiEvent
}

/** Efeitos de uma vez: navegação depois de cancelar ou concluir. */
sealed interface ImportPreviewEffect {
    data object NavigateBack : ImportPreviewEffect
    data object Finished : ImportPreviewEffect
}
