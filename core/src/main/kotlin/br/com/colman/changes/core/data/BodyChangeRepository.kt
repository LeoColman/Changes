// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import br.com.colman.changes.core.db.sql.ChangesDatabase
import br.com.colman.changes.core.model.BodyChangeCategory
import br.com.colman.changes.core.model.BodyChangeEntry
import br.com.colman.changes.core.model.BodyChangeType
import br.com.colman.changes.core.model.BodyMeasurementUnit
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.Intensity
import br.com.colman.changes.core.model.MediaOwnerType
import br.com.colman.changes.core.model.RecordedTime
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.model.TimeZoneProvider
import br.com.colman.changes.core.model.asFailure
import br.com.colman.changes.core.model.asSuccess
import br.com.colman.changes.core.model.isFiniteNumber
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Catálogo e registros de mudanças corporais (Seção 6.1, 7.3). */
public class BodyChangeRepository(
    private val database: ChangesDatabase,
    private val io: CoroutineDispatcher,
    private val clock: Clock,
    private val timeZones: TimeZoneProvider,
) {
    // --- Tipos ---------------------------------------------------------------------------------

    public fun observeVisibleTypes(): Flow<List<BodyChangeType>> =
        database.bodyChangeQueries.selectVisibleTypes().asFlow().mapToList(io).mapRows { it.toModel() }

    public fun observeAllTypes(): Flow<List<BodyChangeType>> =
        database.bodyChangeQueries.selectAllTypes().asFlow().mapToList(io).mapRows { it.toModel() }

    public suspend fun getType(id: Uuid): BodyChangeType? = withContext(io) {
        database.bodyChangeQueries.selectTypeById(id.toString()).executeAsOneOrNull()?.toModel()
    }

    public suspend fun createCustomType(
        label: String,
        category: BodyChangeCategory,
        measurementUnit: BodyMeasurementUnit?,
    ): Result<BodyChangeType> = withContext(io) {
        val error = blankError("label", label)
        if (error != null) {
            error.asFailure()
        } else {
            val id = Uuid.random()
            val candidate = BodyChangeType(
                id = id,
                code = "CUSTOM_$id",
                labelKey = null,
                customLabel = label,
                category = category,
                isReversible = null,
                supportsMeasurement = measurementUnit != null,
                measurementUnit = measurementUnit,
                isBuiltin = false,
                isHidden = false,
            )
            val now = clock.now().toEpochMilliseconds()
            database.bodyChangeQueries.insertType(candidate.toRow(now, now))
            candidate.asSuccess()
        }
    }

    public suspend fun updateCustomType(type: BodyChangeType): Result<Unit> = withContext(io) {
        val row = database.bodyChangeQueries.selectTypeById(type.id.toString()).executeAsOneOrNull()
        val error = blankError("customLabel", type.customLabel.orEmpty())
        if (row == null) {
            DomainError.NotFound("bodyChangeType", type.id.toString()).asFailure()
        } else if (row.is_builtin.asBoolean()) {
            DomainError.Invalid("bodyChangeType", DomainError.Reason.BUILTIN_IMMUTABLE).asFailure()
        } else if (error != null) {
            error.asFailure()
        } else {
            val now = clock.now().toEpochMilliseconds()
            database.bodyChangeQueries.updateCustomType(
                type.customLabel,
                type.category.name,
                (type.measurementUnit != null).asLong(),
                type.measurementUnit?.name,
                now,
                type.id.toString(),
            )
            Unit.asSuccess()
        }
    }

    public suspend fun setTypeHidden(id: Uuid, hidden: Boolean): Result<Unit> = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        database.bodyChangeQueries.setTypeHidden(hidden.asLong(), now, id.toString())
        Unit.asSuccess()
    }

    public suspend fun deleteType(id: Uuid): Result<Unit> = withContext(io) {
        val row = database.bodyChangeQueries.selectTypeById(id.toString()).executeAsOneOrNull()
        if (row == null) {
            DomainError.NotFound("bodyChangeType", id.toString()).asFailure()
        } else if (row.is_builtin.asBoolean()) {
            DomainError.Invalid("bodyChangeType", DomainError.Reason.BUILTIN_IMMUTABLE).asFailure()
        } else {
            val now = clock.now().toEpochMilliseconds()
            database.bodyChangeQueries.softDeleteType(now, id.toString())
            Unit.asSuccess()
        }
    }

    public suspend fun restoreType(id: Uuid): Result<Unit> = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        database.bodyChangeQueries.restoreType(now, id.toString())
        Unit.asSuccess()
    }

    // --- Entradas --------------------------------------------------------------------------------

    public fun observeEntriesByType(typeId: Uuid): Flow<List<BodyChangeEntry>> =
        database.bodyChangeQueries.selectEntriesByType(typeId.toString()).asFlow().mapToList(io)
            .mapRows { it.toModel() }

    public fun observeAllEntries(): Flow<List<BodyChangeEntry>> =
        database.bodyChangeQueries.selectAllEntries().asFlow().mapToList(io).mapRows { it.toModel() }

    public suspend fun getEntry(id: Uuid): BodyChangeEntry? = withContext(io) {
        database.bodyChangeQueries.selectEntryById(id.toString()).executeAsOneOrNull()?.toModel()
    }

    public suspend fun createEntry(
        typeId: Uuid,
        observedAt: Instant,
        intensity: Intensity?,
        measurementValue: Double?,
        notes: String?,
    ): Result<BodyChangeEntry> = withContext(io) {
        val type = database.bodyChangeQueries.selectTypeById(typeId.toString()).executeAsOneOrNull()?.toModel()
        if (type == null) {
            DomainError.NotFound("bodyChangeType", typeId.toString()).asFailure()
        } else {
            val error = firstError(
                futureError("observedAt", observedAt, clock.now()),
                measurementValue?.let { measurementError(type, it) },
            )
            if (error != null) {
                error.asFailure()
            } else {
                val now = clock.now()
                val unit = if (measurementValue != null) type.measurementUnit else null
                val model = BodyChangeEntry(
                    id = Uuid.random(),
                    changeTypeId = typeId,
                    observedAt = RecordedTime.of(observedAt, timeZones.current()),
                    intensity = intensity,
                    measurementValue = measurementValue,
                    measurementUnit = unit,
                    notes = notes,
                )
                val nowMillis = now.toEpochMilliseconds()
                database.bodyChangeQueries.insertEntry(model.toRow(nowMillis, nowMillis))
                model.asSuccess()
            }
        }
    }

    public suspend fun updateEntry(entry: BodyChangeEntry): Result<BodyChangeEntry> = withContext(io) {
        val type = database.bodyChangeQueries.selectTypeById(
            entry.changeTypeId.toString()
        ).executeAsOneOrNull()?.toModel()
        if (type == null) {
            DomainError.NotFound("bodyChangeType", entry.changeTypeId.toString()).asFailure()
        } else {
            val error = firstError(
                futureError("observedAt", entry.observedAt.instant, clock.now()),
                entry.measurementValue?.let { measurementError(type, it) },
            )
            if (error != null) {
                error.asFailure()
            } else {
                val unit = if (entry.measurementValue != null) type.measurementUnit else null
                val normalized = entry.copy(measurementUnit = unit)
                val now = clock.now().toEpochMilliseconds()
                database.bodyChangeQueries.updateEntry(
                    normalized.changeTypeId.toString(),
                    normalized.observedAt.epochMillis,
                    normalized.observedAt.offsetSeconds,
                    normalized.intensity?.level?.toLong(),
                    normalized.measurementValue,
                    normalized.measurementUnit?.name,
                    normalized.notes,
                    now,
                    normalized.id.toString(),
                )
                normalized.asSuccess()
            }
        }
    }

    /** Critério 7.3.2 (lido com ADR 0008): marca as mídias da entrada com o mesmo `deleted_at`. */
    public suspend fun deleteEntry(id: Uuid): Result<Unit> = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        database.transaction {
            database.bodyChangeQueries.softDeleteEntry(now, id.toString())
            database.mediaQueries.softDeleteByOwner(now, MediaOwnerType.BODY_CHANGE_ENTRY.name, id.toString())
        }
        Unit.asSuccess()
    }

    public suspend fun restoreEntry(id: Uuid): Result<Unit> = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        val deletedAt = database.bodyChangeQueries.selectEntryById(id.toString()).executeAsOneOrNull()?.deleted_at
        database.transaction {
            database.bodyChangeQueries.restoreEntry(now, id.toString())
            if (deletedAt != null) {
                database.mediaQueries.restoreByOwnerDeletedAt(
                    now,
                    MediaOwnerType.BODY_CHANGE_ENTRY.name,
                    id.toString(),
                    deletedAt
                )
            }
        }
        Unit.asSuccess()
    }

    /** Código do tipo -> data local (fuso atual) da primeira observação não apagada. */
    public suspend fun firstObservations(): Map<String, LocalDate> = withContext(io) {
        val zone = timeZones.current()
        val result = mutableMapOf<String, LocalDate>()
        for (row in database.bodyChangeQueries.selectFirstObservations().executeAsList()) {
            val code = database.bodyChangeQueries.selectTypeById(row.change_type_id).executeAsOneOrNull()?.code
            val firstObservedAt = row.first_observed_at
            if (code != null && firstObservedAt != null) {
                val observed = RecordedTime.of(Instant.fromEpochMilliseconds(firstObservedAt), zone)
                result[code] = observed.localDate
            }
        }
        result
    }

    private fun measurementError(type: BodyChangeType, value: Double): DomainError? = when {
        !type.supportsMeasurement -> DomainError.Invalid("measurementValue", DomainError.Reason.OUT_OF_RANGE)
        !isFiniteNumber(value) -> DomainError.Invalid("measurementValue", DomainError.Reason.NOT_FINITE)
        else -> null
    }
}
