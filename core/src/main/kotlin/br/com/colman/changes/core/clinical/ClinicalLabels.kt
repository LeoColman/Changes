// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.clinical

import br.com.colman.changes.core.model.BodyRegion
import kotlinx.serialization.Serializable

/**
 * Textos localizados do dataset clínico (`clinical/labels.json`): rótulos de catálogo, templates de
 * vocabulário corporal, sugestões de condição e citações. Rótulos de anatomia só saem daqui através
 * do [BodyVocabularyResolver].
 */
public interface ClinicalLabels {
    public val supportedLocales: Set<String>

    /** Citação completa de uma fonte (Seção 8.4). */
    public fun reference(key: SourceKey): String

    /** Rótulos do locale mais próximo: igual, depois mesmo idioma, depois pt-BR. */
    public fun forLocale(languageTag: String): LocaleLabels

    public companion object {
        public const val DEFAULT_LOCALE: String = "pt-BR"

        public fun load(): ClinicalLabels = fromJson(ClinicalResources.read("labels.json"))

        public fun fromJson(json: String): ClinicalLabels =
            JsonClinicalLabels(ClinicalResources.json.decodeFromString<LabelsDto>(json))
    }
}

private class JsonClinicalLabels(private val dto: LabelsDto) : ClinicalLabels {
    override val supportedLocales: Set<String> get() = dto.locales.keys

    override fun reference(key: SourceKey): String = dto.references.getValue(key.name)

    override fun forLocale(languageTag: String): LocaleLabels {
        val tag = languageTag.replace('_', '-')
        val language = tag.substringBefore('-')
        val key = dto.locales.keys.firstOrNull { it.equals(tag, ignoreCase = true) }
            ?: dto.locales.keys.firstOrNull { it.substringBefore('-').equals(language, ignoreCase = true) }
            ?: ClinicalLabels.DEFAULT_LOCALE
        return LocaleLabels(key, dto.locales.getValue(key))
    }
}

public class LocaleLabels internal constructor(public val locale: String, private val dto: LocaleLabelsDto) {
    public val conditionSuggestions: List<String> get() = dto.conditionSuggestions

    public fun changeTypeLabel(code: String): String? = dto.changeTypes[code]

    public fun analyteLabel(code: String): String? = dto.analytes[code]

    /** Nome exibido de um medicamento builtin: a marca, ou o nome genérico localizado. */
    public fun medicationName(medication: BuiltinMedication): String =
        medication.brand ?: dto.medicationNames.getValue(medication.key)

    public fun substance(key: String): String? = dto.substances[key]

    internal fun regionTemplate(code: String): RegionTemplateDto? = dto.regionTemplates[code]

    internal fun regionTerm(region: BodyRegion, option: String): String = dto.regionTerms.getValue(
        region.name
    ).getValue(option)

    internal fun optionLabel(region: BodyRegion, option: String): String =
        dto.regionOptionLabels.getValue(region.name).getValue(option)

    internal fun regionName(region: BodyRegion): String = dto.regionNames.getValue(region.name)

    internal val chestMeasurementTemplate: String get() = dto.chestMeasurement

    internal val plainChangeLabels: Map<String, String> get() = dto.changeTypes

    internal val regionTemplates: Map<String, RegionTemplateDto> get() = dto.regionTemplates
}

@Serializable
private data class LabelsDto(val references: Map<String, String>, val locales: Map<String, LocaleLabelsDto>)

@Serializable
internal data class LocaleLabelsDto(
    val changeTypes: Map<String, String>,
    val regionTemplates: Map<String, RegionTemplateDto>,
    val regionTerms: Map<String, Map<String, String>>,
    val regionOptionLabels: Map<String, Map<String, String>>,
    val regionNames: Map<String, String>,
    val chestMeasurement: String,
    val analytes: Map<String, String>,
    val medicationNames: Map<String, String>,
    val substances: Map<String, String>,
    val conditionSuggestions: List<String>,
)

@Serializable
internal data class RegionTemplateDto(val region: String, val options: Map<String, String>, val custom: String)
