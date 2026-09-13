// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.model

/**
 * Resultado de uma operação de domínio. Erros esperados nunca são exceções.
 *
 * Nada aqui é `inline` (o Pitest não mata mutantes em corpo inline) e nada usa `when` exaustivo
 * sobre o tipo selado (o último `is` vira checagem redundante e gera mutante equivalente).
 * Ver docs/core-guidelines.md.
 */
public sealed interface Result<out T> {
    public data class Success<out T>(val value: T) : Result<T>

    public data class Failure(val error: DomainError) : Result<Nothing>
}

public fun <T> T.asSuccess(): Result<T> = Result.Success(this)

public fun DomainError.asFailure(): Result<Nothing> = Result.Failure(this)

public val Result<*>.isSuccess: Boolean get() = this is Result.Success

public fun <T> Result<T>.getOrNull(): T? = (this as? Result.Success)?.value

public fun <T> Result<T>.errorOrNull(): DomainError? = (this as? Result.Failure)?.error

public fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> =
    if (this is Result.Success) Result.Success(transform(value)) else this as Result.Failure

public fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> =
    if (this is Result.Success) transform(value) else this as Result.Failure

public fun <T> Result<T>.getOrElse(onFailure: (DomainError) -> T): T =
    if (this is Result.Success) value else onFailure((this as Result.Failure).error)
