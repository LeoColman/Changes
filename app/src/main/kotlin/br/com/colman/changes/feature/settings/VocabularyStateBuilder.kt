// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.settings

import br.com.colman.changes.core.clinical.BodyVocabularyResolver
import br.com.colman.changes.core.clinical.LocaleLabels
import br.com.colman.changes.core.model.BodyRegion
import br.com.colman.changes.core.model.BodyVocabulary
import br.com.colman.changes.core.model.VocabularyChoice

/** Tipo builtin representativo de cada região, só para a prévia da tela (Seção 7.10). */
private val PREVIEW_CODE: Map<BodyRegion, String> = mapOf(
    BodyRegion.GENITAL to "CLITORAL_ENLARGEMENT",
    BodyRegion.CHEST to "CHEST_TENDERNESS",
    BodyRegion.MENSTRUATION to "MENSES_CESSATION",
    BodyRegion.FRONT_CANAL to "VAGINAL_ATROPHY",
)

/**
 * Monta o estado de cada região a partir do vocabulário atual, persistido ou rascunho de onboarding.
 * Compartilhado por [VocabularyViewModel] e `OnboardingViewModel` para que os dois usem o mesmo
 * composable de tela (Seção 9, passo 4 do onboarding).
 */
internal fun buildVocabularyRegions(labels: LocaleLabels, vocabulary: BodyVocabulary): List<VocabularyRegionUiState> {
    val resolver = BodyVocabularyResolver(labels)
    return BodyRegion.entries.map { region -> region.toUiState(resolver, vocabulary) }
}

private fun BodyRegion.toUiState(
    resolver: BodyVocabularyResolver,
    vocabulary: BodyVocabulary,
): VocabularyRegionUiState {
    val rawChoice = vocabulary.choices[this] ?: VocabularyChoice.Preset(defaultOption)
    return VocabularyRegionUiState(
        region = this,
        regionName = resolver.regionName(this),
        options = options.map { option -> VocabularyOptionUiState(option, resolver.optionLabel(this, option)) },
        selectedOption = (rawChoice as? VocabularyChoice.Preset)?.option,
        customText = (rawChoice as? VocabularyChoice.Custom)?.text.orEmpty(),
        isCustomSelected = rawChoice is VocabularyChoice.Custom,
        previewLabel = resolver.builtinLabel(PREVIEW_CODE.getValue(this), vocabulary),
    )
}
