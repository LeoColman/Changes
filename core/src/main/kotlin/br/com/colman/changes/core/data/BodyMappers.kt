// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import br.com.colman.changes.core.db.sql.Body_change_entry
import br.com.colman.changes.core.db.sql.Body_change_type
import br.com.colman.changes.core.db.sql.Media_attachment
import br.com.colman.changes.core.model.BodyChangeCategory
import br.com.colman.changes.core.model.BodyChangeEntry
import br.com.colman.changes.core.model.BodyChangeType
import br.com.colman.changes.core.model.BodyMeasurementUnit
import br.com.colman.changes.core.model.Intensity
import br.com.colman.changes.core.model.MediaAttachment
import br.com.colman.changes.core.model.MediaOwnerType
import br.com.colman.changes.core.model.RecordedTime

internal fun Body_change_type.toModel(): BodyChangeType = BodyChangeType(
    id = id.asUuid(),
    code = code,
    labelKey = label_key,
    customLabel = custom_label,
    category = BodyChangeCategory.valueOf(category),
    isReversible = is_reversible?.asBoolean(),
    supportsMeasurement = supports_measurement.asBoolean(),
    measurementUnit = measurement_unit?.let { BodyMeasurementUnit.valueOf(it) },
    isBuiltin = is_builtin.asBoolean(),
    isHidden = is_hidden.asBoolean(),
)

internal fun BodyChangeType.toRow(createdAt: Long, updatedAt: Long): Body_change_type = Body_change_type(
    id = id.toString(),
    code = code,
    label_key = labelKey,
    custom_label = customLabel,
    category = category.name,
    is_reversible = isReversible?.asLong(),
    supports_measurement = supportsMeasurement.asLong(),
    measurement_unit = measurementUnit?.name,
    is_builtin = isBuiltin.asLong(),
    is_hidden = isHidden.asLong(),
    created_at = createdAt,
    updated_at = updatedAt,
    deleted_at = null,
)

internal fun Body_change_entry.toModel(): BodyChangeEntry = BodyChangeEntry(
    id = id.asUuid(),
    changeTypeId = change_type_id.asUuid(),
    observedAt = RecordedTime.fromDb(observed_at, observed_at_offset_seconds),
    intensity = intensity?.let { Intensity.fromLevel(it.toInt()) },
    measurementValue = measurement_value,
    measurementUnit = measurement_unit?.let { BodyMeasurementUnit.valueOf(it) },
    notes = notes,
)

internal fun BodyChangeEntry.toRow(createdAt: Long, updatedAt: Long): Body_change_entry = Body_change_entry(
    id = id.toString(),
    change_type_id = changeTypeId.toString(),
    observed_at = observedAt.epochMillis,
    observed_at_offset_seconds = observedAt.offsetSeconds,
    intensity = intensity?.level?.toLong(),
    measurement_value = measurementValue,
    measurement_unit = measurementUnit?.name,
    notes = notes,
    created_at = createdAt,
    updated_at = updatedAt,
    deleted_at = null,
)

internal fun Media_attachment.toModel(): MediaAttachment = MediaAttachment(
    id = id.asUuid(),
    ownerType = MediaOwnerType.valueOf(owner_type),
    ownerId = owner_id.asUuid(),
    relativePath = relative_path,
    mimeType = mime_type,
    capturedAt = recordedOrNull(captured_at, captured_at_offset_seconds),
    checksumSha256 = checksum_sha256,
    caption = caption,
)

internal fun MediaAttachment.toRow(createdAt: Long, updatedAt: Long): Media_attachment = Media_attachment(
    id = id.toString(),
    owner_type = ownerType.name,
    owner_id = ownerId.toString(),
    relative_path = relativePath,
    mime_type = mimeType,
    captured_at = capturedAt?.epochMillis,
    captured_at_offset_seconds = capturedAt?.offsetSeconds,
    checksum_sha256 = checksumSha256,
    caption = caption,
    created_at = createdAt,
    updated_at = updatedAt,
    deleted_at = null,
)
