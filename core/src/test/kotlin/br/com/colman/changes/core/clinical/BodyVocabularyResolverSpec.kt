// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.clinical

import br.com.colman.changes.core.model.BodyChangeCategory
import br.com.colman.changes.core.model.BodyChangeType
import br.com.colman.changes.core.model.BodyRegion
import br.com.colman.changes.core.model.BodyVocabulary
import br.com.colman.changes.core.model.VocabularyChoice
import br.com.colman.changes.core.testing.testDataset
import br.com.colman.changes.core.testing.testLabels
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotBeBlank
import kotlin.uuid.Uuid

/** Seção 7.10: todo rótulo builtin que cita anatomia passa pelo resolver (varredura do catálogo). */
class BodyVocabularyResolverSpec : FunSpec({
    val pt = BodyVocabularyResolver(testLabels.forLocale("pt-BR"))
    val en = BodyVocabularyResolver(testLabels.forLocale("en"))
    val anatomyTerms = listOf(
        "clitóris", "clitoris", "clitoral", "vagin", "menstrua", "mama", "mamári", "seio", "peito", "tórax", "torax",
        "breast", "chest", "thorax", "pênis", "penis", "falo", "phallus", "genit", "canal frontal", "front hole",
        "sangramento", "bleeding", "útero", "uterus", "dick",
    )
    val marker = "XYZZY"
    val custom = BodyRegion.entries.fold(
        BodyVocabulary()
    ) { v, region -> v.with(region, VocabularyChoice.Custom(marker)) }

    test("defaults are neutral") {
        pt.builtinLabel("CLITORAL_ENLARGEMENT", BodyVocabulary()) shouldBe "Crescimento genital"
        pt.builtinLabel("VAGINAL_ATROPHY", BodyVocabulary()) shouldBe "Atrofia do canal frontal"
        pt.builtinLabel("MENSES_CESSATION", BodyVocabulary()) shouldBe "Cessação do sangramento"
        pt.builtinLabel("CHEST_TENDERNESS", BodyVocabulary()) shouldBe "Sensibilidade no tórax"
        en.builtinLabel("CLITORAL_ENLARGEMENT", BodyVocabulary()) shouldBe "Genital growth"
    }

    test("the chosen preset changes the label") {
        val vocabulary = BodyVocabulary().with(BodyRegion.GENITAL, VocabularyChoice.Preset("DICK"))
        pt.builtinLabel("CLITORAL_ENLARGEMENT", vocabulary) shouldBe "Crescimento do dick"
        pt.regionTerm(BodyRegion.GENITAL, vocabulary) shouldBe "dick"
    }

    test("a free term is used verbatim, trimmed") {
        val vocabulary = BodyVocabulary().with(BodyRegion.GENITAL, VocabularyChoice.Custom("  meu pau "))
        pt.builtinLabel("CLITORAL_ENLARGEMENT", vocabulary) shouldBe "Crescimento: meu pau"
        pt.regionTerm(BodyRegion.GENITAL, vocabulary) shouldBe "meu pau"
    }

    test("catalog scan: anatomy labels always carry the chosen term; other labels never cite anatomy") {
        listOf(pt, en).forEach { resolver ->
            testDataset.bodyChangeTypes.forEach { type ->
                val label = resolver.builtinLabel(type.code, custom)
                label.shouldNotBeBlank()
                label shouldNotBe type.code
                if (resolver.citesAnatomy(type.code)) {
                    label shouldContain marker
                } else {
                    anatomyTerms.filter { label.lowercase().contains(it) }.shouldBeEmpty()
                }
            }
        }
    }

    test("exactly the anatomy-dependent codes go through the vocabulary") {
        testDataset.bodyChangeTypes.map { it.code }.filter { pt.citesAnatomy(it) }.toSet() shouldBe
            setOf("CLITORAL_ENLARGEMENT", "VAGINAL_ATROPHY", "MENSES_CESSATION", "CHEST_TENDERNESS")
        pt.citesAnatomy("LIBIDO").shouldBeFalse()
        en.citesAnatomy("CHEST_TENDERNESS").shouldBeTrue()
    }

    test("every option of every region has a template text, a term and a settings label, in every locale") {
        testLabels.supportedLocales.forEach { locale ->
            val labels = testLabels.forLocale(locale)
            val resolver = BodyVocabularyResolver(labels)
            BodyRegion.entries.forEach { region ->
                resolver.regionName(region).shouldNotBeBlank()
                region.options.forEach { option ->
                    val vocabulary = BodyVocabulary().with(region, VocabularyChoice.Preset(option))
                    resolver.regionTerm(region, vocabulary).shouldNotBeBlank()
                    resolver.optionLabel(region, option).shouldNotBeBlank()
                }
            }
            labels.regionTemplates.forEach { (code, template) ->
                val region = BodyRegion.valueOf(template.region)
                template.options.keys shouldBe region.options.toSet()
                region.options.forEach { option ->
                    resolver.builtinLabel(code, BodyVocabulary().with(region, VocabularyChoice.Preset(option))) shouldBe
                        template.options.getValue(option)
                }
                template.custom shouldContain "{term}"
            }
        }
    }

    test("plain labels are used when there is no template, and the code is the last resort") {
        pt.builtinLabel("LIBIDO", custom) shouldBe "Libido"
        pt.builtinLabel("NOT_IN_CATALOG", custom) shouldBe "NOT_IN_CATALOG"
    }

    test("custom types show the person's own label") {
        val type = BodyChangeType(
            id = Uuid.random(),
            code = "CUSTOM_x",
            labelKey = null,
            customLabel = "Minha mudança",
            category = BodyChangeCategory.OTHER,
            isReversible = null,
            supportsMeasurement = false,
            measurementUnit = null,
            isBuiltin = false,
            isHidden = false,
        )
        pt.label(type, custom) shouldBe "Minha mudança"
        pt.label(type.copy(customLabel = null, code = "LIBIDO", isBuiltin = true), custom) shouldBe "Libido"
    }

    test("chest measurement label uses the chosen chest term") {
        pt.chestMeasurementLabel(BodyVocabulary()) shouldBe "Circunferência: tórax"
        pt.chestMeasurementLabel(BodyVocabulary().with(BodyRegion.CHEST, VocabularyChoice.Preset("CHEST"))) shouldBe "Circunferência: peito"
        en.chestMeasurementLabel(custom) shouldBe "Circumference: $marker"
        pt.optionLabel(BodyRegion.GENITAL, "NEUTRAL") shouldBe "Neutro (crescimento genital)"
        pt.regionName(BodyRegion.GENITAL) shouldBe "Genitália externa"
    }
})
