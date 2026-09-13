// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.clinical

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith

class ClinicalResourcesSpec : FunSpec({
    test("reads bundled resources as UTF-8") {
        ClinicalResources.read("labels.json") shouldContain "Cessação"
        ClinicalResources.read("expected_changes.json").trimStart() shouldStartWith "{"
    }

    test("a missing resource fails loudly with its name") {
        shouldThrow<IllegalStateException> { ClinicalResources.read("missing.json") }.message shouldBe
            "Missing clinical resource missing.json"
    }

    test("labels can be built from any JSON with the same shape") {
        val labels = ClinicalLabels.fromJson(ClinicalResources.read("labels.json"))
        labels.supportedLocales shouldBe setOf("pt-BR", "en")
        labels.reference(SourceKey.UCSF_2016) shouldContain "UCSF"
    }

    test("the loaded dataset finds expected changes by code") {
        val dataset = ClinicalDataset.load()
        dataset.expectedChange("MENSES_CESSATION")?.permanence shouldBe Permanence.NOT_PERMANENT
        dataset.expectedChange("NOPE") shouldBe null
        dataset.medications.size shouldBe 10
        dataset.labAnalytes.size shouldBe 23
        dataset.bodyChangeTypes.size shouldBe 15
    }
})
