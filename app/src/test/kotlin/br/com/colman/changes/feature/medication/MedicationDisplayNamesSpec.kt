// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.medication

import br.com.colman.changes.core.clinical.BuiltinAnalyte
import br.com.colman.changes.core.clinical.BuiltinChangeType
import br.com.colman.changes.core.clinical.BuiltinMedication
import br.com.colman.changes.core.clinical.ClinicalDataset
import br.com.colman.changes.core.clinical.ExpectedChange
import br.com.colman.changes.core.model.Medication
import br.com.colman.changes.core.model.Route
import br.com.colman.changes.core.model.TreatmentProtocol
import br.com.colman.changes.core.testing.testLabels
import io.kotest.core.listeners.AfterSpecListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.Locale
import kotlin.uuid.Uuid

private val knownMedication = BuiltinMedication(
    Uuid.random(),
    "known-medication",
    "BrandaZ",
    "TESTOSTERONE_CYPIONATE",
    Route.INTRAMUSCULAR,
    null,
)

private val medicationWithoutLabelledSubstance = BuiltinMedication(
    Uuid.random(),
    "unlabelled-substance",
    "BrandY",
    "SUBSTANCE_WITHOUT_A_LABEL",
    Route.ORAL,
    null,
)

private val fakeDataset = object : ClinicalDataset {
    override val datasetVersion = 1
    override val protocol = TreatmentProtocol.MASCULINIZING
    override val expectedChanges: List<ExpectedChange> = emptyList()
    override val bodyChangeTypes: List<BuiltinChangeType> = emptyList()
    override val medications = listOf(knownMedication, medicationWithoutLabelledSubstance)
    override val labAnalytes: List<BuiltinAnalyte> = emptyList()
}

private fun customMedication(
    id: Uuid = Uuid.random(),
    name: String = "Minha marca",
    substance: String? = "Minha substância",
    isBuiltin: Boolean = false,
) = Medication(id, name, substance, null, null, isBuiltin, false)

/**
 * Fixa pt-BR como locale padrão durante o spec e devolve o anterior no fim: `MedicationDisplayNames`
 * resolve com `Locale.getDefault()`, então o teste não pode depender do locale da máquina.
 */
private class FixedDefaultLocale : BeforeSpecListener, AfterSpecListener {
    private var previous: Locale = Locale.getDefault()

    override suspend fun beforeSpec(spec: Spec) {
        previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("pt-BR"))
    }

    override suspend fun afterSpec(spec: Spec) {
        Locale.setDefault(previous)
    }
}

/** Seção 8: nome e substância builtin vêm sempre do rótulo do locale atual, nunca do que o banco guardou em pt-BR. */
class MedicationDisplayNamesSpec : FunSpec({
    extension(FixedDefaultLocale())
    val names = MedicationDisplayNames(fakeDataset, testLabels)
    val ptBrLabels = testLabels.forLocale("pt-BR")

    test("a builtin medication shows the localized label, not whatever the row happens to store") {
        val medication = customMedication(id = knownMedication.id, name = "nome gravado errado", isBuiltin = true)

        names.name(medication) shouldBe ptBrLabels.medicationName(knownMedication)
        names.name(medication) shouldBe "BrandaZ"
    }

    test("marked custom even with a builtin id, the recorded name wins") {
        val medication = customMedication(id = knownMedication.id, name = "Minha marca custom", isBuiltin = false)

        names.name(medication) shouldBe "Minha marca custom"
    }

    test("builtin flag set but the id matches no catalog entry, falls back to the recorded name") {
        val medication = customMedication(id = Uuid.random(), name = "Medicamento sem catálogo", isBuiltin = true)

        names.name(medication) shouldBe "Medicamento sem catálogo"
    }

    test("a builtin medication with a labelled substance shows the localized substance") {
        val medication = customMedication(
            id = knownMedication.id,
            substance = "substância gravada errada",
            isBuiltin = true,
        )

        names.substance(medication) shouldBe ptBrLabels.substance("TESTOSTERONE_CYPIONATE")
        names.substance(medication) shouldBe "cipionato de testosterona"
    }

    test("a builtin medication whose substance key has no label falls back to the recorded substance") {
        val medication = customMedication(
            id = medicationWithoutLabelledSubstance.id,
            substance = "Minha substância customizada",
            isBuiltin = true,
        )

        names.substance(medication) shouldBe "Minha substância customizada"
    }

    test("a non-builtin medication always shows its own recorded substance") {
        val medication = customMedication(
            id = knownMedication.id,
            substance = "Substância customizada",
            isBuiltin = false,
        )

        names.substance(medication) shouldBe "Substância customizada"
    }
})
