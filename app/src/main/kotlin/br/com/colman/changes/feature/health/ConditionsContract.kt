// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.health

import androidx.compose.runtime.Immutable
import br.com.colman.changes.core.model.ConditionSeverity
import br.com.colman.changes.core.model.ConditionStatus
import br.com.colman.changes.core.model.DomainError
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

/**
 * Estado da tela de Condições de saúde (Seção 7.5). Sem classificação de risco: o app só guarda o
 * que a pessoa registrou. As com [ConditionUiState.affectsTreatment] ficam em [pinned].
 */
@Immutable
data class ConditionsUiState(
    val isLoading: Boolean = true,
    val pinned: List<ConditionUiState> = emptyList(),
    val others: List<ConditionUiState> = emptyList(),
    /** Sugestões de rótulo do dataset clínico do locale atual (autocompletar, não questionário). */
    val suggestions: List<String> = emptyList(),
    val form: ConditionFormUiState? = null,
)

@Immutable
data class ConditionUiState(
    val id: Uuid,
    val label: String,
    val code: String?,
    val severity: ConditionSeverity,
    val status: ConditionStatus,
    val diagnosedAt: LocalDate?,
    val resolvedAt: LocalDate?,
    val affectsTreatment: Boolean,
    val notes: String?,
)

/** Formulário de adicionar/editar uma condição. `editingId == null` é um registro novo. */
@Immutable
data class ConditionFormUiState(
    val editingId: Uuid?,
    val label: String = "",
    val code: String = "",
    val severity: ConditionSeverity = ConditionSeverity.UNKNOWN,
    val status: ConditionStatus = ConditionStatus.ACTIVE,
    val diagnosedAt: LocalDate? = null,
    val resolvedAt: LocalDate? = null,
    val affectsTreatment: Boolean = false,
    val notes: String = "",
    val error: DomainError? = null,
)

sealed interface ConditionsUiEvent {
    data object AddRequested : ConditionsUiEvent

    data class EditRequested(val conditionId: Uuid) : ConditionsUiEvent

    /** Qualquer campo simples do formulário (texto, seleção, data, interruptor). */
    data class FormChanged(val apply: (ConditionFormUiState) -> ConditionFormUiState) : ConditionsUiEvent

    data object FormSaved : ConditionsUiEvent

    data object FormDismissed : ConditionsUiEvent

    data class DeleteRequested(val conditionId: Uuid) : ConditionsUiEvent

    data object UndoDeleteRequested : ConditionsUiEvent
}

/** Efeito de uma vez (Seção 5): snackbar de desfazer depois de excluir. */
sealed interface ConditionsEffect {
    data object ShowUndoDelete : ConditionsEffect
}
