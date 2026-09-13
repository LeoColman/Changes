// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.medication

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import br.com.colman.changes.R
import br.com.colman.changes.core.model.DomainError

/** Texto neutro para um erro de domínio (Seção 7.1.3): nenhuma palavra de julgamento ou alarme. */
@Composable
fun DomainError.medicationMessage(): String = when (this) {
    is DomainError.Invalid -> stringResource(reasonMessageRes(reason))
    // Recusa de backup nunca vem das telas de medicação; o ramo existe porque o tipo é selado.
    is DomainError.NotFound, is DomainError.BackupRejected -> stringResource(R.string.medication_error_not_found)
}

private fun reasonMessageRes(reason: DomainError.Reason): Int = when (reason) {
    DomainError.Reason.REQUIRED -> R.string.medication_error_required
    DomainError.Reason.OUT_OF_RANGE -> R.string.medication_error_out_of_range
    DomainError.Reason.NOT_FINITE, DomainError.Reason.NOT_POSITIVE -> R.string.medication_error_not_positive
    DomainError.Reason.IN_THE_FUTURE -> R.string.medication_error_future_date
    DomainError.Reason.END_BEFORE_START -> R.string.medication_error_end_before_start
    DomainError.Reason.BUILTIN_IMMUTABLE -> R.string.medication_error_builtin_immutable
    DomainError.Reason.DUPLICATE -> R.string.medication_error_duplicate
}
