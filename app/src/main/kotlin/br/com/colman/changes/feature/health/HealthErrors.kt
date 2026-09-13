// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.health

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import br.com.colman.changes.R
import br.com.colman.changes.core.model.DomainError

/**
 * Texto neutro para um erro de domínio das telas de condições e exames (Seção 9): nenhuma palavra
 * de julgamento, alarme ou severidade.
 */
@Composable
fun DomainError.healthMessage(): String = when (this) {
    is DomainError.Invalid -> stringResource(reasonMessageRes(reason))
    // Condições e exames nunca recusam por causa de backup: o ramo existe porque o tipo é selado.
    is DomainError.NotFound, is DomainError.BackupRejected -> stringResource(R.string.health_error_not_found)
}

private fun reasonMessageRes(reason: DomainError.Reason): Int = when (reason) {
    DomainError.Reason.REQUIRED -> R.string.health_error_required
    DomainError.Reason.OUT_OF_RANGE -> R.string.health_error_out_of_range
    DomainError.Reason.NOT_FINITE -> R.string.health_error_not_finite
    DomainError.Reason.NOT_POSITIVE -> R.string.health_error_not_positive
    DomainError.Reason.IN_THE_FUTURE -> R.string.health_error_future_date
    DomainError.Reason.END_BEFORE_START -> R.string.health_error_end_before_start
    DomainError.Reason.BUILTIN_IMMUTABLE -> R.string.health_error_builtin_immutable
    DomainError.Reason.DUPLICATE -> R.string.health_error_duplicate
}
