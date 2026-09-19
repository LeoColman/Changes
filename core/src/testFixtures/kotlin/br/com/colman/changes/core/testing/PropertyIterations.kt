// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.testing

/** Iterações de um property test numa rodada de mutação (ADR 0005). */
private const val PITEST_ITERATIONS = 40

/**
 * Quantas iterações um property test roda. Numa rodada normal, [full]. Numa rodada de mutação
 * (`-Dchanges.pitest=true`), no máximo [PITEST_ITERATIONS]: o Pitest roda o teste uma vez por mutante
 * coberto, então mil iterações por mutante viram horas de espera sem matar mutante nenhum a mais.
 */
public fun propertyIterations(full: Int): Int =
    if (System.getProperty("changes.pitest") == "true") minOf(full, PITEST_ITERATIONS) else full
