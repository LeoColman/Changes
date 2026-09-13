// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.expected

import androidx.compose.runtime.Immutable
import br.com.colman.changes.core.clinical.ExpectedChangeStatus
import br.com.colman.changes.core.clinical.Permanence
import br.com.colman.changes.core.clinical.SourceKey
import kotlinx.datetime.LocalDate

/**
 * Estado da tela de mudanças esperadas (Seção 7.2). Descritivo: nada aqui compara a pessoa a uma
 * meta. Sem [hrtStart], os itens vêm sem estado relativo (critério 7.2.1), mas aparecem do mesmo jeito.
 */
@Immutable
data class ExpectedChangesUiState(
    val isLoading: Boolean = true,
    val hrtStart: LocalDate? = null,
    val monthsOnTreatment: Double? = null,
    val items: List<ExpectedItemUiState> = emptyList(),
    val references: List<ExpectedReferenceUiState> = emptyList(),
)

/** Unidade de exibição de uma faixa de meses: anos quando o mínimo passa de 12 meses. */
enum class ExpectedRangeUnit { MONTHS, YEARS }

/** Faixa já convertida para a unidade de exibição. [min] e [max] estão na unidade de [unit]. */
@Immutable
data class ExpectedRangeUiState(val min: Double, val max: Double, val unit: ExpectedRangeUnit)

/**
 * Um item da linha do tempo de uma mudança do dataset clínico. Sem data de início da TH, [status],
 * [windowStart], [windowEnd] e [firstObservedMonths] vêm `null` (critério 7.2.1).
 */
@Immutable
data class ExpectedItemUiState(
    val changeTypeCode: String,
    val name: String,
    /** Sempre em meses (não convertido): usado pela linha do tempo, cujo eixo é em meses desde a TH. */
    val onsetMonthsMin: Double,
    val onsetMonthsMax: Double,
    val onsetRange: ExpectedRangeUiState,
    /** `null` quando a fonte não informa o efeito máximo. */
    val maxEffectRange: ExpectedRangeUiState?,
    val permanence: Permanence,
    val permanenceCitation: String?,
    val sourceCitation: String,
    val status: ExpectedChangeStatus?,
    val windowStart: LocalDate?,
    val windowEnd: LocalDate?,
    val firstObserved: LocalDate?,
    val firstObservedMonths: Double?,
)

/** Uma citação da Seção 8.4, para a seção final de referências (critério 7.2.3). */
@Immutable
data class ExpectedReferenceUiState(val source: SourceKey, val citation: String)
