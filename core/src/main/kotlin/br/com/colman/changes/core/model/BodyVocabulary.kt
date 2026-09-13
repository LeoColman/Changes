// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.model

/** Regiões do corpo cujo nome a pessoa escolhe (Seção 7.10). As opções são códigos; o texto vem do resolver. */
public enum class BodyRegion(public val defaultOption: String, public val options: List<String>) {
    GENITAL("NEUTRAL", listOf("NEUTRAL", "CLITORIS", "DICK", "PHALLUS", "MY_GENITALS")),
    CHEST("THORAX", listOf("THORAX", "CHEST", "MY_CHEST_AREA")),
    MENSTRUATION("BLEEDING", listOf("BLEEDING", "MENSTRUATION", "CYCLE")),
    FRONT_CANAL("FRONT_CANAL", listOf("FRONT_CANAL", "VAGINA")),
}

/** Escolha para uma região: uma opção pré-definida ou um termo livre. */
public sealed interface VocabularyChoice {
    public data class Preset(val option: String) : VocabularyChoice

    public data class Custom(val text: String) : VocabularyChoice
}

public data class BodyVocabulary(val choices: Map<BodyRegion, VocabularyChoice> = emptyMap()) {
    /** A escolha salva, se válida; senão o default neutro da região. */
    public fun choiceFor(region: BodyRegion): VocabularyChoice {
        val choice = choices[region]
        return if (choice != null && isValid(region, choice)) choice else VocabularyChoice.Preset(region.defaultOption)
    }

    public fun with(region: BodyRegion, choice: VocabularyChoice): BodyVocabulary = copy(
        choices = choices + (region to choice)
    )

    private fun isValid(region: BodyRegion, choice: VocabularyChoice): Boolean =
        if (choice is VocabularyChoice.Preset) {
            choice.option in region.options
        } else {
            (choice as VocabularyChoice.Custom).text.isNotBlank()
        }
}
