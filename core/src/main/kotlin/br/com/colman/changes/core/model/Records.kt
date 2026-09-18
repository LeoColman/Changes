// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.model

import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

public data class DoseLog(
    val id: Uuid,
    /** `null` = dose avulsa, ou regime removido definitivamente. */
    val regimenId: Uuid?,
    val medicationId: Uuid,
    val dose: Dose,
    val route: Route,
    val injectionSite: InjectionSite?,
    val takenAt: RecordedTime,
    val notes: String?,
)

public enum class MeasurementType { WEIGHT, WAIST, HIP, CHEST, BICEP, NECK, BODY_FAT_PCT, CUSTOM }

public enum class MeasurementUnit { KG, LB, CM, IN, PERCENT }

public data class Measurement(
    val id: Uuid,
    val type: MeasurementType,
    val customLabel: String?,
    val value: Double,
    val unit: MeasurementUnit,
    val measuredAt: RecordedTime,
    val notes: String?,
)

public enum class ExerciseIntensity { LIGHT, MODERATE, VIGOROUS }

public data class ExerciseSession(
    val id: Uuid,
    val activity: String,
    val durationMinutes: Int,
    val intensity: ExerciseIntensity,
    val occurredAt: RecordedTime,
    val notes: String?,
)

public enum class ConditionSeverity { MILD, MODERATE, SEVERE, UNKNOWN }

public enum class ConditionStatus { ACTIVE, RESOLVED, IN_REMISSION, SUSPECTED }

public data class HealthCondition(
    val id: Uuid,
    val label: String,
    /** CID-10 opcional, entrada livre, sem validação. */
    val code: String?,
    val severity: ConditionSeverity,
    val status: ConditionStatus,
    val diagnosedAt: LocalDate?,
    val resolvedAt: LocalDate?,
    val affectsTreatment: Boolean,
    val notes: String?,
)

public data class LabAnalyte(
    val id: Uuid,
    val code: String,
    val labelKey: String?,
    val customLabel: String?,
    val defaultUnit: String,
    val isBuiltin: Boolean,
    val isHidden: Boolean,
)

public data class LabResult(
    val id: Uuid,
    val analyteId: Uuid,
    val value: Double,
    val unit: String,
    val collectedAt: RecordedTime,
    /** Faixa do laudo da própria pessoa. `null` quando não informada. */
    val referenceRange: ReferenceRange?,
    val labName: String?,
    val notes: String?,
)

/**
 * Check-in do dia. Escalas 1..5: humor e energia obrigatórios, o resto opcional. Alívio e bem-estar
 * ([relief]), irritabilidade e impaciência ([irritability]), intensidade emocional
 * ([emotionalIntensity]) e ansiedade são os sentimentos comuns no início da testosterona (ADR 0012).
 */
public data class MoodLog(
    val id: Uuid,
    val date: LocalDate,
    val mood: Int,
    val energy: Int,
    val anxiety: Int?,
    val dysphoria: Int?,
    val sleepHours: Double?,
    val note: String?,
    val tags: List<String>,
    val relief: Int? = null,
    val irritability: Int? = null,
    val emotionalIntensity: Int? = null,
)
