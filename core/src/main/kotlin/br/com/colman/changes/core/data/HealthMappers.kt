// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import br.com.colman.changes.core.db.sql.Exercise_session
import br.com.colman.changes.core.db.sql.Health_condition
import br.com.colman.changes.core.db.sql.Lab_analyte
import br.com.colman.changes.core.db.sql.Lab_result
import br.com.colman.changes.core.model.ConditionSeverity
import br.com.colman.changes.core.model.ConditionStatus
import br.com.colman.changes.core.model.ExerciseIntensity
import br.com.colman.changes.core.model.ExerciseSession
import br.com.colman.changes.core.model.HealthCondition
import br.com.colman.changes.core.model.LabAnalyte
import br.com.colman.changes.core.model.LabResult
import br.com.colman.changes.core.model.MeasurementType
import br.com.colman.changes.core.model.MeasurementUnit
import br.com.colman.changes.core.model.RecordedTime
import br.com.colman.changes.core.model.ReferenceRange
import br.com.colman.changes.core.db.sql.Measurement as MeasurementRow
import br.com.colman.changes.core.model.Measurement as MeasurementModel

internal fun MeasurementRow.toModel(): MeasurementModel = MeasurementModel(
    id = id.asUuid(),
    type = MeasurementType.valueOf(type),
    customLabel = custom_label,
    value = value_,
    unit = MeasurementUnit.valueOf(unit),
    measuredAt = RecordedTime.fromDb(measured_at, measured_at_offset_seconds),
    notes = notes,
)

internal fun MeasurementModel.toRow(createdAt: Long, updatedAt: Long): MeasurementRow = MeasurementRow(
    id = id.toString(),
    type = type.name,
    custom_label = customLabel,
    value_ = value,
    unit = unit.name,
    measured_at = measuredAt.epochMillis,
    measured_at_offset_seconds = measuredAt.offsetSeconds,
    notes = notes,
    created_at = createdAt,
    updated_at = updatedAt,
    deleted_at = null,
)

internal fun Exercise_session.toModel(): ExerciseSession = ExerciseSession(
    id = id.asUuid(),
    activity = activity,
    durationMinutes = duration_minutes.toInt(),
    intensity = ExerciseIntensity.valueOf(intensity),
    occurredAt = RecordedTime.fromDb(occurred_at, occurred_at_offset_seconds),
    notes = notes,
)

internal fun ExerciseSession.toRow(createdAt: Long, updatedAt: Long): Exercise_session = Exercise_session(
    id = id.toString(),
    activity = activity,
    duration_minutes = durationMinutes.toLong(),
    intensity = intensity.name,
    occurred_at = occurredAt.epochMillis,
    occurred_at_offset_seconds = occurredAt.offsetSeconds,
    notes = notes,
    created_at = createdAt,
    updated_at = updatedAt,
    deleted_at = null,
)

internal fun Health_condition.toModel(): HealthCondition = HealthCondition(
    id = id.asUuid(),
    label = label,
    code = code,
    severity = ConditionSeverity.valueOf(severity),
    status = ConditionStatus.valueOf(status),
    diagnosedAt = diagnosed_at?.asLocalDate(),
    resolvedAt = resolved_at?.asLocalDate(),
    affectsTreatment = affects_treatment.asBoolean(),
    notes = notes,
)

internal fun HealthCondition.toRow(createdAt: Long, updatedAt: Long): Health_condition = Health_condition(
    id = id.toString(),
    label = label,
    code = code,
    severity = severity.name,
    status = status.name,
    diagnosed_at = diagnosedAt?.asEpochDay(),
    resolved_at = resolvedAt?.asEpochDay(),
    affects_treatment = affectsTreatment.asLong(),
    notes = notes,
    created_at = createdAt,
    updated_at = updatedAt,
    deleted_at = null,
)

internal fun Lab_analyte.toModel(): LabAnalyte = LabAnalyte(
    id = id.asUuid(),
    code = code,
    labelKey = label_key,
    customLabel = custom_label,
    defaultUnit = default_unit,
    isBuiltin = is_builtin.asBoolean(),
    isHidden = is_hidden.asBoolean(),
)

internal fun LabAnalyte.toRow(createdAt: Long, updatedAt: Long): Lab_analyte = Lab_analyte(
    id = id.toString(),
    code = code,
    label_key = labelKey,
    custom_label = customLabel,
    default_unit = defaultUnit,
    is_builtin = isBuiltin.asLong(),
    is_hidden = isHidden.asLong(),
    created_at = createdAt,
    updated_at = updatedAt,
    deleted_at = null,
)

internal fun Lab_result.toModel(): LabResult = LabResult(
    id = id.asUuid(),
    analyteId = analyte_id.asUuid(),
    value = value_,
    unit = unit,
    collectedAt = RecordedTime.fromDb(collected_at, collected_at_offset_seconds),
    referenceRange = ReferenceRange.ofNullable(reference_low, reference_high),
    labName = lab_name,
    notes = notes,
)

internal fun LabResult.toRow(createdAt: Long, updatedAt: Long): Lab_result = Lab_result(
    id = id.toString(),
    analyte_id = analyteId.toString(),
    value_ = value,
    unit = unit,
    collected_at = collectedAt.epochMillis,
    collected_at_offset_seconds = collectedAt.offsetSeconds,
    reference_low = referenceRange?.low,
    reference_high = referenceRange?.high,
    lab_name = labName,
    notes = notes,
    created_at = createdAt,
    updated_at = updatedAt,
    deleted_at = null,
)
