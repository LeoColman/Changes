// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.medication

import androidx.compose.runtime.Immutable
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.DoseUnit
import br.com.colman.changes.core.model.InjectionSite
import br.com.colman.changes.core.model.Route
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Estado da tela de registrar dose (Seção 7.1). Quando [regimenId] não é nulo, a medicação vem do
 * regime e não é editável aqui; dose, via, local, data/hora e notas continuam editáveis.
 */
@Immutable
data class LogDoseUiState(
    val isLoading: Boolean = true,
    val isEditing: Boolean = false,
    val regimenId: String? = null,
    val availableMedications: List<MedicationOption> = emptyList(),
    val selectedMedicationId: String? = null,
    val doseValue: String = "",
    val doseUnit: DoseUnit = DoseUnit.MG,
    val route: Route = Route.OTHER,
    val injectionSite: InjectionSite? = null,
    val date: LocalDate,
    val time: LocalTime,
    val notes: String = "",
    val error: DomainError? = null,
    val showDeleteConfirm: Boolean = false,
    /** Seção 9: `true` quando o formulário difere do que foi carregado, ou do inicial numa entrada nova. */
    val hasUnsavedChanges: Boolean = false,
) {
    val medicationEditable: Boolean get() = regimenId == null
}

sealed interface LogDoseUiEvent {
    /** Carregado uma vez pelo `Route` (Seção 5): a partir de um regime, de um registro existente, ou avulso. */
    data class Load(val regimenId: String?, val plannedEpochDay: Long?, val doseLogId: String?) : LogDoseUiEvent

    /** Qualquer campo simples de formulário (texto, seleção, data, hora). */
    data class FieldChanged(val apply: (LogDoseUiState) -> LogDoseUiState) : LogDoseUiEvent
    data object Save : LogDoseUiEvent
    data object RequestDelete : LogDoseUiEvent
    data object ConfirmDelete : LogDoseUiEvent
    data object CancelDelete : LogDoseUiEvent
    data object Back : LogDoseUiEvent
}

sealed interface LogDoseEffect {
    data object NavigateBack : LogDoseEffect
}
