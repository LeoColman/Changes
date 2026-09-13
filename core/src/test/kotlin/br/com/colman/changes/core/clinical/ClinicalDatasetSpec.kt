// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.clinical

import br.com.colman.changes.core.clinical.Permanence.NOT_PERMANENT
import br.com.colman.changes.core.clinical.Permanence.NOT_STATED
import br.com.colman.changes.core.clinical.Permanence.PARTIALLY_PERMANENT
import br.com.colman.changes.core.clinical.Permanence.PERMANENT
import br.com.colman.changes.core.clinical.SourceKey.ENDO_2017
import br.com.colman.changes.core.clinical.SourceKey.WPATH_SOC8
import br.com.colman.changes.core.model.Concentration
import br.com.colman.changes.core.model.ConcentrationUnit
import br.com.colman.changes.core.model.Route
import br.com.colman.changes.core.model.TreatmentProtocol
import br.com.colman.changes.core.testing.testDataset
import br.com.colman.changes.core.testing.testLabels
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank

/**
 * O dataset seedado bate, linha a linha, com a Tabela 12 de Hembree 2017 e com as afirmações de
 * permanência do WPATH SOC8 (ADR 0002). A duplicação aqui é proposital: é a conferência contra a fonte.
 */
class ClinicalDatasetSpec : FunSpec({
    data class Row(
        val onsetMin: Double,
        val onsetMax: Double?,
        val maxMin: Double?,
        val maxMax: Double?,
        val permanence: Permanence,
        val permanenceSource: SourceKey?,
    )

    val table = mapOf(
        "SKIN_OILINESS_ACNE" to Row(1.0, 6.0, 12.0, 24.0, NOT_STATED, null),
        "FACIAL_BODY_HAIR" to Row(6.0, 12.0, 48.0, 60.0, PARTIALLY_PERMANENT, WPATH_SOC8),
        "SCALP_HAIR_LOSS" to Row(6.0, 12.0, null, null, PERMANENT, WPATH_SOC8),
        "MUSCLE_MASS_STRENGTH" to Row(6.0, 12.0, 24.0, 60.0, NOT_STATED, null),
        "FAT_REDISTRIBUTION" to Row(1.0, 6.0, 24.0, 60.0, NOT_STATED, null),
        "MENSES_CESSATION" to Row(1.0, 6.0, null, null, NOT_PERMANENT, WPATH_SOC8),
        "CLITORAL_ENLARGEMENT" to Row(1.0, 6.0, 12.0, 24.0, PERMANENT, WPATH_SOC8),
        "VAGINAL_ATROPHY" to Row(1.0, 6.0, 12.0, 24.0, NOT_STATED, null),
        "VOICE_DEEPENING" to Row(6.0, 12.0, 12.0, 24.0, PERMANENT, WPATH_SOC8),
    )

    test("dataset version and protocol") {
        testDataset.datasetVersion shouldBe 1
        testDataset.protocol shouldBe TreatmentProtocol.MASCULINIZING
    }

    test("expected changes match the verified source table exactly, and nothing else is seeded") {
        testDataset.expectedChanges.map { it.changeTypeCode }.toSet() shouldBe table.keys
        testDataset.expectedChanges shouldHaveSize table.size
        testDataset.expectedChanges.forEach { change ->
            val row = table.getValue(change.changeTypeCode)
            Row(
                change.onsetMonthsMin,
                change.onsetMonthsMax,
                change.maxEffectMonthsMin,
                change.maxEffectMonthsMax,
                change.permanence,
                change.permanenceSource,
            ) shouldBe row
            change.source shouldBe ENDO_2017
            change.protocol shouldBe TreatmentProtocol.MASCULINIZING
        }
    }

    test("criterion 7.2.3: every source key resolves to a citation") {
        SourceKey.entries.forEach { testLabels.reference(it).shouldNotBeBlank() }
        testDataset.expectedChanges.flatMap { listOfNotNull(it.source, it.permanenceSource) }.forEach {
            testLabels.reference(it).shouldNotBeBlank()
        }
    }

    test("every expected change has a registrable body change type") {
        testDataset.bodyChangeTypes.map { it.code } shouldContainAll table.keys
        testDataset.expectedChange("VOICE_DEEPENING")?.changeTypeCode shouldBe "VOICE_DEEPENING"
        testDataset.expectedChange("LIBIDO").shouldBeNull()
    }

    test("builtin catalogs have unique ids and codes") {
        val ids = testDataset.bodyChangeTypes.map {
            it.id
        } + testDataset.medications.map { it.id } + testDataset.labAnalytes.map { it.id }
        ids.toSet() shouldHaveSize ids.size
        testDataset.bodyChangeTypes.map { it.code }.toSet() shouldHaveSize testDataset.bodyChangeTypes.size
        testDataset.labAnalytes.map { it.code }.toSet() shouldHaveSize testDataset.labAnalytes.size
        testDataset.medications.map { it.key }.toSet() shouldHaveSize testDataset.medications.size
    }

    test("the body change catalog holds the 9 literature changes plus the 6 registrable ones (8.1)") {
        testDataset.bodyChangeTypes.map { it.code }.toSet() shouldBe table.keys + setOf(
            "LIBIDO",
            "SWEAT_ODOR",
            "EMOTIONAL_SENSITIVITY",
            "PELVIC_PAIN_CRAMPS",
            "CHEST_TENDERNESS",
            "INJECTION_SITE_PAIN",
        )
        testDataset.bodyChangeTypes.filter {
            it.measurementUnit != null
        }.associate { it.code to it.measurementUnit?.name } shouldBe
            mapOf("CLITORAL_ENLARGEMENT" to "CM", "VOICE_DEEPENING" to "HZ")
    }

    test("the lab analyte catalog matches 8.3") {
        testDataset.labAnalytes.map { it.code } shouldBe listOf(
            "TESTOSTERONE_TOTAL", "TESTOSTERONE_FREE", "ESTRADIOL", "SHBG", "LH", "FSH", "HEMATOCRIT", "HEMOGLOBIN",
            "RED_BLOOD_CELLS", "FERRITIN", "ALT", "AST", "GGT", "CREATININE", "CHOLESTEROL_TOTAL", "HDL", "LDL",
            "TRIGLYCERIDES", "FASTING_GLUCOSE", "HBA1C", "TSH", "VITAMIN_D", "PROLACTIN",
        )
    }

    test("criterion 7.6.2: no clinical threshold or reference range is embedded in the catalog") {
        val catalogText = ClinicalResources.read("catalog.json").lowercase()
        listOf("reference", "low", "high", "threshold", "normal", "min\"", "max\"").filter {
            catalogText.contains(it)
        }.shouldBeEmpty()
    }

    test("no dose is seeded (8.2) and concentrations match the product labels (ADR 0003)") {
        ClinicalResources.read("catalog.json").lowercase().contains("dose").shouldBe(false)
        val byKey = testDataset.medications.associateBy { it.key }
        byKey.getValue("DEPOSTERON").concentration shouldBe Concentration(100.0, ConcentrationUnit.MG_PER_ML)
        byKey.getValue("DURATESTON").concentration shouldBe Concentration(250.0, ConcentrationUnit.MG_PER_ML)
        byKey.getValue("NEBIDO").concentration shouldBe Concentration(250.0, ConcentrationUnit.MG_PER_ML)
        byKey.getValue("ANDROGEL").concentration shouldBe Concentration(10.0, ConcentrationUnit.MG_PER_G)
        byKey.getValue("AXERON").concentration shouldBe Concentration(20.0, ConcentrationUnit.MG_PER_ML)
        byKey.getValue("HORMUS").concentration.shouldBeNull()
        byKey.getValue("OTHER_MEDICATION").route.shouldBeNull()
        byKey.getValue("FINASTERIDE").route shouldBe Route.ORAL
        byKey.getValue("MINOXIDIL").route shouldBe Route.TOPICAL
    }

    test("every catalog item has a label in every locale") {
        testLabels.supportedLocales shouldBe setOf("pt-BR", "en")
        testLabels.supportedLocales.forEach { locale ->
            val labels = testLabels.forLocale(locale)
            testDataset.labAnalytes.forEach { labels.analyteLabel(it.code)!!.shouldNotBeBlank() }
            testDataset.medications.forEach { labels.medicationName(it).shouldNotBeBlank() }
            testDataset.medications.mapNotNull { it.substanceKey }.forEach { labels.substance(it)!!.shouldNotBeBlank() }
            labels.conditionSuggestions shouldHaveSize 15
        }
    }

    test("medication names: brands are kept, generics are localized") {
        val byKey = testDataset.medications.associateBy { it.key }
        testLabels.forLocale("pt-BR").medicationName(byKey.getValue("DEPOSTERON")) shouldBe "Deposteron"
        testLabels.forLocale("pt-BR").medicationName(byKey.getValue("OTHER_MEDICATION")) shouldBe "Outro medicamento"
        testLabels.forLocale("en").medicationName(byKey.getValue("OTHER_MEDICATION")) shouldBe "Other medication"
        testLabels.forLocale("pt-BR").substance("TESTOSTERONE_CYPIONATE") shouldBe "cipionato de testosterona"
        testLabels.forLocale("pt-BR").substance("UNKNOWN").shouldBeNull()
        testLabels.forLocale("pt-BR").analyteLabel("UNKNOWN").shouldBeNull()
        testLabels.forLocale("pt-BR").changeTypeLabel("LIBIDO") shouldBe "Libido"
    }

    test("locale fallback: exact, then same language, then pt-BR") {
        testLabels.forLocale("en").locale shouldBe "en"
        testLabels.forLocale("en-US").locale shouldBe "en"
        testLabels.forLocale("EN_gb").locale shouldBe "en"
        testLabels.forLocale("pt_BR").locale shouldBe "pt-BR"
        testLabels.forLocale("pt-PT").locale shouldBe "pt-BR"
        testLabels.forLocale("fr-FR").locale shouldBe "pt-BR"
        testLabels.forLocale("").locale shouldBe "pt-BR"
    }
})
