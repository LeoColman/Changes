// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Converte cada linha do SQLDelight para o modelo, num único ponto do módulo. `Flow.map` é `inline`
 * do kotlinx-coroutines: chamado direto em cada repositório, sua máquina de estados de corrotina
 * seria copiada e mutada em cada classe (docs/core-guidelines.md, regra 16), inflando o denominador
 * do Pitest com mutantes de código gerado pelo compilador, não de lógica nossa. Centralizar aqui
 * concentra esse custo num único lugar do pacote.
 */
internal fun <T, R> Flow<List<T>>.mapRows(transform: (T) -> R): Flow<List<R>> = map { rows ->
    val result = mutableListOf<R>()
    for (row in rows) result += transform(row)
    result
}

/** Mesma ideia de [mapRows] para uma linha só (ou nenhuma): centraliza o único ponto de `Flow.map`. */
internal fun <T, R> Flow<T?>.mapRow(transform: (T?) -> R): Flow<R> = map(transform)
