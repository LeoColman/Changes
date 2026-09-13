// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import br.com.colman.changes.core.model.DomainError

/** Erros de domínio traduzidos para uma causa que a UI de `feature.body` sabe exibir. */
enum class BodyErrorMessage {
    REQUIRED_FIELD,
    FUTURE_DATE,
    MEASUREMENT_UNSUPPORTED,
    INVALID_MEASUREMENT,
    BUILTIN_IMMUTABLE,
    NOT_FOUND,
    UNKNOWN,
}

private const val MEASUREMENT_FIELD = "measurementValue"

/** Traduz um [DomainError] de repositório para a causa exibida na tela (o core não carrega texto de UI). */
fun DomainError.toBodyErrorMessage(): BodyErrorMessage = when (this) {
    is DomainError.NotFound -> BodyErrorMessage.NOT_FOUND
    // Recusa de backup nunca vem das telas do corpo; o ramo existe porque o tipo é selado.
    is DomainError.BackupRejected -> BodyErrorMessage.UNKNOWN
    is DomainError.Invalid -> when (reason) {
        DomainError.Reason.REQUIRED -> BodyErrorMessage.REQUIRED_FIELD
        DomainError.Reason.IN_THE_FUTURE -> BodyErrorMessage.FUTURE_DATE
        DomainError.Reason.BUILTIN_IMMUTABLE -> BodyErrorMessage.BUILTIN_IMMUTABLE
        DomainError.Reason.OUT_OF_RANGE -> outOfRangeMessage(field)
        DomainError.Reason.NOT_FINITE -> BodyErrorMessage.INVALID_MEASUREMENT
        else -> BodyErrorMessage.UNKNOWN
    }
}

private fun outOfRangeMessage(field: String): BodyErrorMessage =
    if (field == MEASUREMENT_FIELD) BodyErrorMessage.MEASUREMENT_UNSUPPORTED else BodyErrorMessage.INVALID_MEASUREMENT
