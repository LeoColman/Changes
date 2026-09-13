// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.clinical

import br.com.colman.changes.core.model.BodyChangeCategory
import br.com.colman.changes.core.model.BodyMeasurementUnit
import br.com.colman.changes.core.model.Concentration
import br.com.colman.changes.core.model.ConcentrationUnit
import br.com.colman.changes.core.model.Route
import br.com.colman.changes.core.model.TreatmentProtocol
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.Uuid

/** Fontes citáveis (Seção 8.4). Todo item clínico exibido aponta para uma delas. */
public enum class SourceKey { ENDO_2017, WPATH_SOC8, UCSF_2016 }

/** O que as fontes afirmam sobre a mudança regredir com a suspensão da testosterona (ADR 0002). */
public enum class Permanence { PERMANENT, PARTIALLY_PERMANENT, NOT_PERMANENT, NOT_STATED }

/** Faixa descrita na literatura. Limite `null` = a fonte não dá limite. */
public data class ExpectedChange(
    val changeTypeCode: String,
    val protocol: TreatmentProtocol,
    val onsetMonthsMin: Double,
    val onsetMonthsMax: Double?,
    val maxEffectMonthsMin: Double?,
    val maxEffectMonthsMax: Double?,
    val permanence: Permanence,
    val permanenceSource: SourceKey?,
    val source: SourceKey,
)

public data class BuiltinChangeType(
    val id: Uuid,
    val code: String,
    val category: BodyChangeCategory,
    val measurementUnit: BodyMeasurementUnit?,
)

public data class BuiltinMedication(
    val id: Uuid,
    val key: String,
    val brand: String?,
    val substanceKey: String?,
    val route: Route?,
    val concentration: Concentration?,
)

public data class BuiltinAnalyte(val id: Uuid, val code: String, val defaultUnit: String)

/** Dataset clínico versionado, lido de `src/main/resources/clinical/` (Seção 8). */
public interface ClinicalDataset {
    public val datasetVersion: Int
    public val protocol: TreatmentProtocol
    public val expectedChanges: List<ExpectedChange>
    public val bodyChangeTypes: List<BuiltinChangeType>
    public val medications: List<BuiltinMedication>
    public val labAnalytes: List<BuiltinAnalyte>

    public fun expectedChange(code: String): ExpectedChange? = expectedChanges.firstOrNull { it.changeTypeCode == code }

    public companion object {
        public fun load(): ClinicalDataset {
            val expected = ClinicalResources.json.decodeFromString<ExpectedChangesDto>(
                ClinicalResources.read("expected_changes.json"),
            )
            val catalog = ClinicalResources.json.decodeFromString<CatalogDto>(ClinicalResources.read("catalog.json"))
            val protocol = TreatmentProtocol(expected.protocol)
            return LoadedDataset(
                datasetVersion = expected.datasetVersion,
                protocol = protocol,
                expectedChanges = expected.rows.map { it.toModel(protocol) },
                bodyChangeTypes = catalog.bodyChangeTypes.map { it.toModel() },
                medications = catalog.medications.map { it.toModel() },
                labAnalytes = catalog.labAnalytes.map { BuiltinAnalyte(Uuid.parse(it.id), it.code, it.defaultUnit) },
            )
        }
    }
}

/** Implementação lida dos recursos. Interface + classe privada: o Koin não tenta injetar o construtor. */
private class LoadedDataset(
    override val datasetVersion: Int,
    override val protocol: TreatmentProtocol,
    override val expectedChanges: List<ExpectedChange>,
    override val bodyChangeTypes: List<BuiltinChangeType>,
    override val medications: List<BuiltinMedication>,
    override val labAnalytes: List<BuiltinAnalyte>,
) : ClinicalDataset

internal object ClinicalResources {
    val json: Json = Json { ignoreUnknownKeys = false }

    fun read(name: String): String =
        ClinicalResources::class.java.getResource("/clinical/$name")?.readText(Charsets.UTF_8)
            ?: error("Missing clinical resource $name")
}

// DTOs só carregam dados. A conversão fica em funções de extensão (fora das classes) para que os
// getters sejam de fato chamados e cobertos pelos testes.

@Serializable
private data class ExpectedChangesDto(
    val datasetVersion: Int,
    val protocol: String,
    val rows: List<ExpectedChangeDto>,
)

@Serializable
private data class ExpectedChangeDto(
    val code: String,
    val onsetMonthsMin: Double,
    val onsetMonthsMax: Double?,
    val maxEffectMonthsMin: Double?,
    val maxEffectMonthsMax: Double?,
    val permanence: String,
    val permanenceSource: String?,
    val source: String,
)

private fun ExpectedChangeDto.toModel(protocol: TreatmentProtocol) = ExpectedChange(
    changeTypeCode = code,
    protocol = protocol,
    onsetMonthsMin = onsetMonthsMin,
    onsetMonthsMax = onsetMonthsMax,
    maxEffectMonthsMin = maxEffectMonthsMin,
    maxEffectMonthsMax = maxEffectMonthsMax,
    permanence = Permanence.valueOf(permanence),
    permanenceSource = permanenceSource?.let { SourceKey.valueOf(it) },
    source = SourceKey.valueOf(source),
)

@Serializable
private data class CatalogDto(
    val bodyChangeTypes: List<ChangeTypeDto>,
    val medications: List<MedicationDto>,
    val labAnalytes: List<AnalyteDto>,
)

@Serializable
private data class ChangeTypeDto(val id: String, val code: String, val category: String, val measurementUnit: String?)

private fun ChangeTypeDto.toModel() = BuiltinChangeType(
    id = Uuid.parse(id),
    code = code,
    category = BodyChangeCategory.valueOf(category),
    measurementUnit = measurementUnit?.let { BodyMeasurementUnit.valueOf(it) },
)

@Serializable
private data class MedicationDto(
    val id: String,
    val key: String,
    val brand: String?,
    val substance: String?,
    val route: String?,
    val concentrationValue: Double?,
    val concentrationUnit: String?,
)

private fun MedicationDto.toModel() = BuiltinMedication(
    id = Uuid.parse(id),
    key = key,
    brand = brand,
    substanceKey = substance,
    route = route?.let { Route.valueOf(it) },
    concentration = concentrationValue?.let { value ->
        Concentration(value, ConcentrationUnit.valueOf(checkNotNull(concentrationUnit)))
    },
)

@Serializable
private data class AnalyteDto(val id: String, val code: String, val defaultUnit: String)
