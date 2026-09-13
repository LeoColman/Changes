// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.settings

import androidx.compose.runtime.Immutable
import br.com.colman.changes.core.model.UnitSystem
import kotlinx.datetime.LocalDate

/** Estado da tela de Perfil (Seção 6.1). Nenhum campo é obrigatório. */
@Immutable
data class ProfileUiState(
    val isLoading: Boolean = true,
    val displayName: String = "",
    val hrtStartDate: LocalDate? = null,
    val heightText: String = "",
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val birthYearText: String = "",
    val showBmi: Boolean = false,
)

/** Ações da tela de Perfil: cada uma grava sozinha, sem botão de salvar. */
sealed interface ProfileUiEvent {
    data class DisplayNameChanged(val text: String) : ProfileUiEvent

    data class HrtStartDateChanged(val date: LocalDate) : ProfileUiEvent

    data class HeightChanged(val text: String) : ProfileUiEvent

    data class UnitSystemChanged(val unitSystem: UnitSystem) : ProfileUiEvent

    data class BirthYearChanged(val text: String) : ProfileUiEvent

    data class ShowBmiChanged(val enabled: Boolean) : ProfileUiEvent
}
