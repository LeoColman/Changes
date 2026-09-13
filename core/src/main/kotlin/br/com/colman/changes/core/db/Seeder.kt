// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.db

import br.com.colman.changes.core.clinical.BuiltinAnalyte
import br.com.colman.changes.core.clinical.BuiltinChangeType
import br.com.colman.changes.core.clinical.BuiltinMedication
import br.com.colman.changes.core.clinical.ClinicalDataset
import br.com.colman.changes.core.clinical.ClinicalLabels
import br.com.colman.changes.core.clinical.ExpectedChange
import br.com.colman.changes.core.clinical.Permanence
import br.com.colman.changes.core.db.sql.Body_change_type
import br.com.colman.changes.core.db.sql.ChangesDatabase
import br.com.colman.changes.core.db.sql.Expected_change
import br.com.colman.changes.core.db.sql.Lab_analyte
import br.com.colman.changes.core.db.sql.Medication
import kotlin.time.Clock

/**
 * Seed idempotente, executado a cada abertura do app e depois de todo import (Seção 8):
 * - linhas únicas (perfil, app_meta);
 * - catálogos builtin: insere o que falta e reescreve a definição do builtin, preservando
 *   `is_hidden` e os carimbos (a definição pertence ao app, a visibilidade à pessoa);
 * - `expected_change`: reescrito inteiro quando o dataset do app é mais novo que o do banco.
 *
 * Nunca toca em registro da pessoa.
 */
public class Seeder(
    private val database: ChangesDatabase,
    private val dataset: ClinicalDataset,
    labels: ClinicalLabels,
    private val clock: Clock,
) {
    /** O banco guarda o texto em pt-BR; a UI localiza itens builtin pelo id. */
    private val storedLabels = labels.forLocale(ClinicalLabels.DEFAULT_LOCALE)

    public fun seed() {
        val now = clock.now().toEpochMilliseconds()
        database.transaction {
            database.appMetaQueries.insertDefault(CURRENT_SCHEMA_VERSION)
            database.appMetaQueries.setSchemaVersion(CURRENT_SCHEMA_VERSION)
            database.profileQueries.insertDefault(now)
            dataset.bodyChangeTypes.forEach { seedChangeType(it, now) }
            dataset.medications.forEach { seedMedication(it, now) }
            dataset.labAnalytes.forEach { seedAnalyte(it, now) }
            reseedExpectedChangesIfOutdated()
        }
    }

    private fun seedChangeType(type: BuiltinChangeType, now: Long) {
        val reversible = dataset.expectedChange(type.code)?.permanence?.let(::reversibleFlag)
        val supportsMeasurement = if (type.measurementUnit != null) 1L else 0L
        val unit = type.measurementUnit?.name
        database.bodyChangeQueries.insertBuiltinTypeIfMissing(
            Body_change_type(
                id = type.id.toString(),
                code = type.code,
                label_key = type.code,
                custom_label = null,
                category = type.category.name,
                is_reversible = reversible,
                supports_measurement = supportsMeasurement,
                measurement_unit = unit,
                is_builtin = 1L,
                is_hidden = 0L,
                created_at = now,
                updated_at = now,
                deleted_at = null,
            ),
        )
        database.bodyChangeQueries.updateBuiltinTypeDefinition(
            type.code,
            type.category.name,
            reversible,
            supportsMeasurement,
            unit,
            type.id.toString(),
        )
    }

    private fun seedMedication(medication: BuiltinMedication, now: Long) {
        val name = storedLabels.medicationName(medication)
        val substance = medication.substanceKey?.let { storedLabels.substance(it) }
        val route = medication.route?.name
        val concentrationValue = medication.concentration?.value
        val concentrationUnit = medication.concentration?.unit?.name
        database.medicationQueries.insertBuiltinIfMissing(
            Medication(
                id = medication.id.toString(),
                name = name,
                substance = substance,
                default_route = route,
                concentration_value = concentrationValue,
                concentration_unit = concentrationUnit,
                is_builtin = 1L,
                is_hidden = 0L,
                created_at = now,
                updated_at = now,
                deleted_at = null,
            ),
        )
        database.medicationQueries.updateBuiltinDefinition(
            name,
            substance,
            route,
            concentrationValue,
            concentrationUnit,
            medication.id.toString(),
        )
    }

    private fun seedAnalyte(analyte: BuiltinAnalyte, now: Long) {
        database.labQueries.insertBuiltinAnalyteIfMissing(
            Lab_analyte(
                id = analyte.id.toString(),
                code = analyte.code,
                label_key = analyte.code,
                custom_label = null,
                default_unit = analyte.defaultUnit,
                is_builtin = 1L,
                is_hidden = 0L,
                created_at = now,
                updated_at = now,
                deleted_at = null,
            ),
        )
        database.labQueries.updateBuiltinAnalyteDefinition(analyte.code, analyte.defaultUnit, analyte.id.toString())
    }

    private fun reseedExpectedChangesIfOutdated() {
        val stored = database.appMetaQueries.get().executeAsOne().clinical_dataset_version
        val empty = database.expectedChangeQueries.selectByProtocol(dataset.protocol.code).executeAsList().isEmpty()
        if (dataset.datasetVersion > stored || empty) {
            database.expectedChangeQueries.deleteAll()
            dataset.expectedChanges.forEach { database.expectedChangeQueries.insert(it.toRow()) }
            database.appMetaQueries.setClinicalDatasetVersion(dataset.datasetVersion.toLong())
        }
    }

    private fun ExpectedChange.toRow() = Expected_change(
        id = "${protocol.code}:$changeTypeCode:$source",
        change_type_code = changeTypeCode,
        protocol = protocol.code,
        onset_months_min = onsetMonthsMin,
        onset_months_max = onsetMonthsMax,
        max_effect_months_min = maxEffectMonthsMin,
        max_effect_months_max = maxEffectMonthsMax,
        permanence = permanence.name,
        permanence_source_key = permanenceSource?.name,
        source_key = source.name,
        dataset_version = dataset.datasetVersion.toLong(),
    )

    private companion object {
        /** `is_reversible`: 0 permanente, 1 não permanente, NULL quando a fonte não afirma. */
        fun reversibleFlag(permanence: Permanence): Long? = when (permanence) {
            Permanence.PERMANENT -> 0L
            Permanence.NOT_PERMANENT -> 1L
            else -> null
        }
    }
}
