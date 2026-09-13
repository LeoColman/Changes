// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.medication

import br.com.colman.changes.core.clinical.ClinicalDataset
import br.com.colman.changes.core.clinical.ClinicalLabels
import br.com.colman.changes.core.model.Medication
import br.com.colman.changes.core.model.Route
import java.util.Locale

/** Opção de medicação exibida em listas e seletores. */
data class MedicationOption(val id: String, val name: String, val defaultRoute: Route?)

/**
 * Nome e substância exibidos de uma medicação. Para medicação builtin, vêm do [ClinicalLabels] do
 * locale atual pelo [br.com.colman.changes.core.clinical.BuiltinMedication] de mesmo id em
 * [ClinicalDataset]: o banco só guarda a versão em pt-BR (seed), então mostrar o campo persistido
 * direto quebraria o app em outro idioma. Para medicação customizada, vem do cadastro da pessoa.
 */
class MedicationDisplayNames(private val dataset: ClinicalDataset, private val labels: ClinicalLabels) {
    private val builtinById = dataset.medications.associateBy { it.id }

    fun name(medication: Medication): String {
        val builtin = builtinById[medication.id]
        return if (medication.isBuiltin && builtin != null) {
            currentLabels().medicationName(builtin)
        } else {
            medication.name
        }
    }

    fun substance(medication: Medication): String? {
        val builtin = builtinById[medication.id]
        val substanceKey = builtin?.substanceKey
        return if (medication.isBuiltin && substanceKey != null) {
            currentLabels().substance(substanceKey) ?: medication.substance
        } else {
            medication.substance
        }
    }

    private fun currentLabels() = labels.forLocale(Locale.getDefault().toLanguageTag())
}
