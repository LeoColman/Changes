// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.medication

import androidx.compose.runtime.Immutable
import br.com.colman.changes.core.model.Regimen
import kotlinx.datetime.LocalDate

/** Item de regime na lista (Seção 7.1): medicação, dose, via e agenda vêm de [regimen]. */
@Immutable
data class RegimenListItem(val regimen: Regimen, val medicationName: String, val nextDose: LocalDate?)

@Immutable
data class RegimenListUiState(
    val isLoading: Boolean = true,
    val activeRegimens: List<RegimenListItem> = emptyList(),
    val endedRegimens: List<RegimenListItem> = emptyList(),
)

sealed interface RegimenListUiEvent {
    data class OpenRegimen(val id: String) : RegimenListUiEvent
    data object CreateRegimen : RegimenListUiEvent
    data object OpenHistory : RegimenListUiEvent
    data object Back : RegimenListUiEvent
}

sealed interface RegimenListEffect {
    data class NavigateToRegimen(val id: String) : RegimenListEffect
    data object NavigateToCreate : RegimenListEffect
    data object NavigateToHistory : RegimenListEffect
    data object NavigateBack : RegimenListEffect
}
