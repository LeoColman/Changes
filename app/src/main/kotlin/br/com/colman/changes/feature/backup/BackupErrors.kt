// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.backup

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import br.com.colman.changes.R
import br.com.colman.changes.core.model.BackupRejection
import br.com.colman.changes.core.model.DomainError

/** As três operações de `BackupRepository` só falham com [DomainError.BackupRejected] (Seção 7.8). */
internal fun DomainError.rejectionOrNull(): BackupRejection? = (this as? DomainError.BackupRejected)?.reason

/**
 * Mensagem neutra por motivo de recusa (Seção 7.8): sempre deixa claro que nada foi alterado. O
 * `when` não tem `else` de propósito: um motivo novo no enum [BackupRejection] fica sem mensagem e
 * quebra a build até ganhar uma.
 */
@Composable
fun BackupRejection.backupMessage(): String = stringResource(
    when (this) {
        BackupRejection.UNREADABLE -> R.string.backup_error_unreadable
        BackupRejection.NOT_A_BACKUP -> R.string.backup_error_not_a_backup
        BackupRejection.NEWER_VERSION -> R.string.backup_error_newer_version
        BackupRejection.UNSUPPORTED_VERSION -> R.string.backup_error_unsupported_version
        BackupRejection.CHECKSUM_MISMATCH -> R.string.backup_error_checksum_mismatch
        BackupRejection.MISSING_MEDIA -> R.string.backup_error_missing_media
        BackupRejection.INVALID_DATA -> R.string.backup_error_invalid_data
        BackupRejection.EXPORT_FAILED -> R.string.backup_error_export_failed
        BackupRejection.IMPORT_FAILED -> R.string.backup_error_import_failed
    },
)
