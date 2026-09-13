// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.health

import androidx.compose.runtime.Immutable
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.ui.chart.ChartBand
import br.com.colman.changes.ui.chart.ChartPoint
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

/**
 * Estado da tela de Exames (Seção 7.6). Marcação de fora da faixa é puramente aritmética
 * ([br.com.colman.changes.core.model.ReferenceRange.isOutside]); sem faixa informada, sem marcação.
 */
@Immutable
data class LabResultsUiState(
    val isLoading: Boolean = true,
    val analytes: List<LabAnalyteListItemUiState> = emptyList(),
    /** `null` mostra a lista de analitos; preenchido mostra o detalhe de um analito. */
    val detail: LabAnalyteDetailUiState? = null,
    val newAnalyteForm: NewAnalyteFormUiState? = null,
    val resultForm: LabResultFormUiState? = null,
)

@Immutable
data class LabAnalyteListItemUiState(val id: Uuid, val label: String, val latest: LabResultRowUiState?)

@Immutable
data class LabAnalyteDetailUiState(
    val analyteId: Uuid,
    val label: String,
    val defaultUnit: String,
    val chartPoints: List<ChartPoint>,
    /** Faixa do resultado mais recente que tiver faixa informada (não necessariamente o último). */
    val chartBand: ChartBand?,
    /** Ordenados por data, mais antigo primeiro. */
    val results: List<LabResultRowUiState>,
)

@Immutable
data class LabResultRowUiState(
    val id: Uuid,
    val value: Double,
    val unit: String,
    val date: LocalDate,
    val referenceLow: Double?,
    val referenceHigh: Double?,
    /** Critério 7.6.1: só existe quando há faixa informada e o valor cai fora dela. */
    val isOutOfRange: Boolean,
    val labName: String?,
    val notes: String?,
)

/** Formulário de um analito personalizado novo (nome e unidade padrão). */
@Immutable
data class NewAnalyteFormUiState(val label: String = "", val defaultUnit: String = "", val error: DomainError? = null)

/** Formulário de adicionar/editar um resultado. `editingId == null` é um registro novo. */
@Immutable
data class LabResultFormUiState(
    val editingId: Uuid?,
    val analyteId: Uuid,
    val valueText: String = "",
    val unit: String = "",
    val date: LocalDate,
    val referenceLowText: String = "",
    val referenceHighText: String = "",
    val labName: String = "",
    val notes: String = "",
    val error: DomainError? = null,
)

sealed interface LabResultsUiEvent {
    /** Carregado uma vez pelo `Route` (Seção 5): `analyteId` nulo mostra a lista. */
    data class Load(val analyteId: Uuid?) : LabResultsUiEvent

    data class AnalyteOpened(val analyteId: Uuid) : LabResultsUiEvent

    data object BackToListRequested : LabResultsUiEvent

    data object NewAnalyteRequested : LabResultsUiEvent

    data class NewAnalyteChanged(val apply: (NewAnalyteFormUiState) -> NewAnalyteFormUiState) : LabResultsUiEvent

    data object NewAnalyteSaved : LabResultsUiEvent

    data object NewAnalyteDismissed : LabResultsUiEvent

    data object AddResultRequested : LabResultsUiEvent

    data class EditResultRequested(val resultId: Uuid) : LabResultsUiEvent

    data class ResultFormChanged(val apply: (LabResultFormUiState) -> LabResultFormUiState) : LabResultsUiEvent

    data object ResultFormSaved : LabResultsUiEvent

    data object ResultFormDismissed : LabResultsUiEvent

    data class DeleteResultRequested(val resultId: Uuid) : LabResultsUiEvent

    data object UndoDeleteRequested : LabResultsUiEvent
}

/** Efeito de uma vez (Seção 5): snackbar de desfazer depois de excluir um resultado. */
sealed interface LabResultsEffect {
    data object ShowUndoDelete : LabResultsEffect
}
