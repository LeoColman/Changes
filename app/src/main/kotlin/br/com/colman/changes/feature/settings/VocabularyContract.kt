// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.settings

import androidx.compose.runtime.Immutable
import br.com.colman.changes.core.model.BodyRegion
import br.com.colman.changes.core.model.VocabularyChoice

/**
 * Estado da tela de Vocabulário corporal (Seção 7.10): uma entrada por [BodyRegion]. Reaproveitado
 * pelo onboarding (passo 4), que constrói o mesmo estado a partir de um rascunho não gravado.
 */
@Immutable
data class VocabularyUiState(val regions: List<VocabularyRegionUiState> = emptyList())

@Immutable
data class VocabularyRegionUiState(
    val region: BodyRegion,
    val regionName: String,
    val options: List<VocabularyOptionUiState>,
    val selectedOption: String?,
    val customText: String,
    val isCustomSelected: Boolean,
    val previewLabel: String,
)

@Immutable
data class VocabularyOptionUiState(val code: String, val label: String)

/** Ações da tela de Vocabulário: escolher um preset, escolher "outro termo" ou digitar o termo livre. */
sealed interface VocabularyUiEvent {
    val region: BodyRegion

    data class OptionSelected(override val region: BodyRegion, val option: String) : VocabularyUiEvent

    data class CustomSelected(override val region: BodyRegion) : VocabularyUiEvent

    data class CustomTextChanged(override val region: BodyRegion, val text: String) : VocabularyUiEvent
}

/** Converte o evento na escolha de domínio correspondente (Seção 7.10). */
internal fun VocabularyUiEvent.toChoice(): VocabularyChoice = when (this) {
    is VocabularyUiEvent.OptionSelected -> VocabularyChoice.Preset(option)
    is VocabularyUiEvent.CustomSelected -> VocabularyChoice.Custom("")
    is VocabularyUiEvent.CustomTextChanged -> VocabularyChoice.Custom(text)
}
