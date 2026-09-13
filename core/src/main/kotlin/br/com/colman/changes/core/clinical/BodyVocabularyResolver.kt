// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.clinical

import br.com.colman.changes.core.model.BodyChangeType
import br.com.colman.changes.core.model.BodyRegion
import br.com.colman.changes.core.model.BodyVocabulary
import br.com.colman.changes.core.model.VocabularyChoice

/**
 * Resolve em runtime o termo que a pessoa escolheu para cada região do corpo (Seção 7.10). Todo
 * rótulo builtin que cita anatomia passa por aqui; nenhum sai de `strings.xml`.
 */
public class BodyVocabularyResolver(private val labels: LocaleLabels) {

    /** Rótulo de um tipo de mudança: o texto da pessoa (customizado) ou o builtin resolvido. */
    public fun label(type: BodyChangeType, vocabulary: BodyVocabulary): String =
        type.customLabel ?: builtinLabel(type.code, vocabulary)

    public fun builtinLabel(code: String, vocabulary: BodyVocabulary): String {
        val template = labels.regionTemplate(code) ?: return labels.changeTypeLabel(code) ?: code
        val choice = vocabulary.choiceFor(BodyRegion.valueOf(template.region))
        return if (choice is VocabularyChoice.Preset) {
            template.options.getValue(choice.option)
        } else {
            template.custom.replace(TERM, (choice as VocabularyChoice.Custom).text.trim())
        }
    }

    /** O termo em si (substantivo), para textos genéricos. */
    public fun regionTerm(region: BodyRegion, vocabulary: BodyVocabulary): String {
        val choice = vocabulary.choiceFor(region)
        return if (choice is VocabularyChoice.Preset) {
            labels.regionTerm(region, choice.option)
        } else {
            (choice as VocabularyChoice.Custom).text.trim()
        }
    }

    /** Rótulo da opção na tela de Ajustes. */
    public fun optionLabel(region: BodyRegion, option: String): String = labels.optionLabel(region, option)

    public fun regionName(region: BodyRegion): String = labels.regionName(region)

    /** Rótulo da medida de circunferência do tórax, com o termo escolhido. */
    public fun chestMeasurementLabel(vocabulary: BodyVocabulary): String =
        labels.chestMeasurementTemplate.replace(TERM, regionTerm(BodyRegion.CHEST, vocabulary))

    /** `true` se o rótulo builtin do [code] depende do vocabulário corporal. */
    public fun citesAnatomy(code: String): Boolean = labels.regionTemplate(code) != null

    private companion object {
        const val TERM = "{term}"
    }
}
