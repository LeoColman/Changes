// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.medication

import androidx.compose.runtime.Immutable
import br.com.colman.changes.core.model.DoseLog

/** Período do filtro de histórico (Seção 7.1). `days` nulo = tudo. */
enum class HistoryPeriod(val days: Int?) {
    DAYS_30(30),
    DAYS_90(90),
    DAYS_365(365),
    ALL(null),
}

/** Fato neutro de adesão de um regime ativo (Seção 7.1): sem cor de alarme, sem streak. */
@Immutable
data class AdherenceSummary(
    val regimenId: String,
    val medicationName: String,
    val registered: Int,
    val expected: Int,
    val windowDays: Int,
)

@Immutable
data class HistoryEntry(val log: DoseLog, val medicationName: String)

/** Registros de um mês, mais recente primeiro (Seção 7.1). [key] no formato `yyyy-MM`. */
@Immutable
data class MonthGroup(val key: String, val entries: List<HistoryEntry>)

@Immutable
data class DoseHistoryUiState(
    val isLoading: Boolean = true,
    val medicationFilter: String? = null,
    val availableMedications: List<MedicationOption> = emptyList(),
    val periodFilter: HistoryPeriod = HistoryPeriod.DAYS_90,
    val adherenceSentences: List<AdherenceSummary> = emptyList(),
    val monthGroups: List<MonthGroup> = emptyList(),
)

sealed interface DoseHistoryUiEvent {
    data class FilterByMedication(val medicationId: String?) : DoseHistoryUiEvent
    data class FilterByPeriod(val period: HistoryPeriod) : DoseHistoryUiEvent
    data class RequestDelete(val doseLogId: String) : DoseHistoryUiEvent
    data class Undo(val doseLogId: String) : DoseHistoryUiEvent
    data class EditEntry(val doseLogId: String) : DoseHistoryUiEvent
    data object Back : DoseHistoryUiEvent
}

sealed interface DoseHistoryEffect {
    data class ShowUndoSnackbar(val doseLogId: String) : DoseHistoryEffect
    data class NavigateToEdit(val doseLogId: String) : DoseHistoryEffect
    data object NavigateBack : DoseHistoryEffect
}
