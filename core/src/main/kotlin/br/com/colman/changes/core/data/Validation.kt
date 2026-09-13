// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.isFiniteNumber
import kotlin.time.Instant

// Helpers de validação compartilhados pelos repositórios. Cada um devolve `null` quando o campo é
// válido, ou o `DomainError.Invalid` correspondente. Nada aqui é `inline`: o Pitest mede o pacote
// `data` e uma checagem inline copiada para cada chamador vira mutante impossível de matar.

/** Campo obrigatório, sem espaços em branco. */
internal fun blankError(field: String, value: String): DomainError? =
    if (value.isBlank()) DomainError.Invalid(field, DomainError.Reason.REQUIRED) else null

/** Número finito, sem exigência de sinal. */
internal fun finiteError(field: String, value: Double): DomainError? =
    if (!isFiniteNumber(value)) DomainError.Invalid(field, DomainError.Reason.NOT_FINITE) else null

/**
 * Número finito e maior que zero. A ordem importa: comparar um valor não finito com zero pode dar
 * `false` (é o caso de `NaN`), escondendo o problema real. Finitude é checada primeiro sempre.
 */
internal fun positiveFiniteError(field: String, value: Double): DomainError? = when {
    !isFiniteNumber(value) -> DomainError.Invalid(field, DomainError.Reason.NOT_FINITE)
    value <= 0.0 -> DomainError.Invalid(field, DomainError.Reason.NOT_POSITIVE)
    else -> null
}

/** Inteiro maior que zero. */
internal fun positiveError(field: String, value: Int): DomainError? =
    if (value <= 0) DomainError.Invalid(field, DomainError.Reason.NOT_POSITIVE) else null

/** Inteiro dentro de uma faixa fechada. */
internal fun rangeError(field: String, value: Int, range: IntRange): DomainError? =
    if (value !in range) DomainError.Invalid(field, DomainError.Reason.OUT_OF_RANGE) else null

/** Número (finito) dentro de uma faixa fechada. Não finito também é fora de faixa. */
internal fun rangeError(field: String, value: Double, range: ClosedFloatingPointRange<Double>): DomainError? =
    if (!isFiniteNumber(value) || value !in range) DomainError.Invalid(field, DomainError.Reason.OUT_OF_RANGE) else null

/** Inteiro maior ou igual a zero. */
internal fun notNegativeError(field: String, value: Int): DomainError? =
    if (value < 0) DomainError.Invalid(field, DomainError.Reason.OUT_OF_RANGE) else null

/** Instante que não pode estar no futuro em relação a [now] (inclusive: `now` mesmo é válido). */
internal fun futureError(field: String, instant: Instant, now: Instant): DomainError? =
    if (instant > now) DomainError.Invalid(field, DomainError.Reason.IN_THE_FUTURE) else null

/** Primeiro erro não nulo, na ordem dada; `null` se todos passaram. */
internal fun firstError(vararg errors: DomainError?): DomainError? {
    for (error in errors) {
        if (error != null) return error
    }
    return null
}
