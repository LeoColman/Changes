// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.settings

import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDate

/** Índice do último passo (Seção 9: no máximo 4 passos, 0 a 3, todos puláveis). */
internal const val ONBOARDING_LAST_STEP = 3

/**
 * Estado do onboarding: (0) boas-vindas, (1) início da TH, (2) altura, peso atual e "mostrar IMC",
 * (3) vocabulário corporal. Nada é obrigatório; nada é gravado até terminar ou pular.
 */
@Immutable
data class OnboardingUiState(
    val step: Int = 0,
    val hrtStartDate: LocalDate? = null,
    val heightText: String = "",
    val showBmi: Boolean = false,
    val weightText: String = "",
    val weightError: Boolean = false,
    val vocabulary: VocabularyUiState = VocabularyUiState(),
)

/** Ações do onboarding: `Finished` cobre tanto "Pular" quanto "Concluir" no último passo. */
sealed interface OnboardingUiEvent {
    data object NextStep : OnboardingUiEvent

    data object Finished : OnboardingUiEvent

    data class HrtStartDateChanged(val date: LocalDate) : OnboardingUiEvent

    data class HeightChanged(val text: String) : OnboardingUiEvent

    data class ShowBmiChanged(val enabled: Boolean) : OnboardingUiEvent

    data class WeightChanged(val text: String) : OnboardingUiEvent

    data class VocabularyChanged(val event: VocabularyUiEvent) : OnboardingUiEvent
}

/** Efeito de uma vez: terminar (ou pular) fecha o onboarding e chama `onFinished()`. */
sealed interface OnboardingEffect {
    data object Finished : OnboardingEffect
}
