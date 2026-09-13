// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.model

/**
 * Faixa de referência digitada pela pessoa a partir do laudo (Seção 7.6). A comparação é puramente
 * aritmética: não há interpretação, severidade nem limiar clínico embutido.
 */
public data class ReferenceRange(val low: Double?, val high: Double?) {
    public val isEmpty: Boolean get() = low == null && high == null

    public fun isOutside(value: Double): Boolean = isBelow(value) || isAbove(value)

    private fun isBelow(value: Double): Boolean = low != null && value < low

    private fun isAbove(value: Double): Boolean = high != null && value > high

    public companion object {
        /** `null` quando nenhum limite foi informado: sem faixa, sem marcação (critério 7.6.1). */
        public fun ofNullable(low: Double?, high: Double?): ReferenceRange? =
            if (low == null && high == null) null else ReferenceRange(low, high)
    }
}
