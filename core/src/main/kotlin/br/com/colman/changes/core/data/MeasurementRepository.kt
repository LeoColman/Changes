// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import br.com.colman.changes.core.db.sql.ChangesDatabase
import br.com.colman.changes.core.model.Bmi
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.Measurement
import br.com.colman.changes.core.model.MeasurementType
import br.com.colman.changes.core.model.MeasurementUnit
import br.com.colman.changes.core.model.RecordedTime
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.model.TimeZoneProvider
import br.com.colman.changes.core.model.asFailure
import br.com.colman.changes.core.model.asSuccess
import br.com.colman.changes.core.model.getOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

private const val PERCENT_MIN = 0.0
private const val PERCENT_MAX = 100.0

/** Peso e outras medidas corporais (Seção 6.1, 7.4). IMC é sempre calculado, nunca persistido. */
public class MeasurementRepository(
    private val database: ChangesDatabase,
    private val io: CoroutineDispatcher,
    private val clock: Clock,
    private val timeZones: TimeZoneProvider,
    private val profiles: ProfileRepository,
) {
    public fun observeByType(type: MeasurementType): Flow<List<Measurement>> =
        database.measurementQueries.selectByType(
            type.name
        ).asFlow().mapToList(io).mapRows { it.toModel() }

    public fun observeAll(): Flow<List<Measurement>> =
        database.measurementQueries.selectAll().asFlow().mapToList(io).mapRows { it.toModel() }

    public suspend fun create(
        type: MeasurementType,
        customLabel: String?,
        value: Double,
        unit: MeasurementUnit,
        measuredAt: Instant,
        notes: String?,
    ): Result<Measurement> = withContext(io) {
        val now = clock.now()
        val error = measurementError(type, customLabel, value, unit, measuredAt, now)
        if (error != null) {
            error.asFailure()
        } else {
            val model = Measurement(
                id = Uuid.random(),
                type = type,
                customLabel = customLabel,
                value = value,
                unit = unit,
                measuredAt = RecordedTime.of(measuredAt, timeZones.current()),
                notes = notes,
            )
            val nowMillis = now.toEpochMilliseconds()
            database.measurementQueries.insert(model.toRow(nowMillis, nowMillis))
            model.asSuccess()
        }
    }

    public suspend fun update(measurement: Measurement): Result<Measurement> = withContext(io) {
        val now = clock.now()
        val error = measurementError(
            measurement.type,
            measurement.customLabel,
            measurement.value,
            measurement.unit,
            measurement.measuredAt.instant,
            now,
        )
        if (error != null) {
            error.asFailure()
        } else {
            database.measurementQueries.update(
                measurement.type.name,
                measurement.customLabel,
                measurement.value,
                measurement.unit.name,
                measurement.measuredAt.epochMillis,
                measurement.measuredAt.offsetSeconds,
                measurement.notes,
                now.toEpochMilliseconds(),
                measurement.id.toString(),
            )
            measurement.asSuccess()
        }
    }

    public suspend fun delete(id: Uuid): Result<Unit> = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        database.measurementQueries.softDelete(now, id.toString())
        Unit.asSuccess()
    }

    public suspend fun restore(id: Uuid): Result<Unit> = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        database.measurementQueries.restore(now, id.toString())
        Unit.asSuccess()
    }

    /** Critérios 7.4.1 e 7.4.2: `null` sem altura, sem peso ou com `showBmi = false`. Nunca estima altura. */
    public fun observeBmi(): Flow<Double?> {
        val weightFlow = database.measurementQueries.selectLatestByType(
            MeasurementType.WEIGHT.name
        ).asFlow().mapToOneOrNull(io)
        return combine(profiles.observe(), weightFlow) { profile, weightRow ->
            val weightKg = weightRow?.toModel()?.let { Bmi.weightInKg(it).getOrNull() }
            if (!profile.showBmi || profile.heightCm == null || weightKg == null) {
                null
            } else {
                Bmi.calculate(weightKg, profile.heightCm).getOrNull()
            }
        }
    }

    private fun measurementError(
        type: MeasurementType,
        customLabel: String?,
        value: Double,
        unit: MeasurementUnit,
        measuredAt: Instant,
        now: Instant,
    ): DomainError? {
        val labelError = if (type == MeasurementType.CUSTOM) blankError("customLabel", customLabel.orEmpty()) else null
        val unitError = if (compatibleUnit(
                type,
                unit
            )
        ) {
            null
        } else {
            DomainError.Invalid("unit", DomainError.Reason.OUT_OF_RANGE)
        }
        val percentError = if (type == MeasurementType.BODY_FAT_PCT) {
            rangeError(
                "value",
                value,
                PERCENT_MIN..PERCENT_MAX
            )
        } else {
            null
        }
        return firstError(
            finiteError("value", value),
            labelError,
            unitError,
            percentError,
            futureError("measuredAt", measuredAt, now)
        )
    }

    private fun compatibleUnit(type: MeasurementType, unit: MeasurementUnit): Boolean = when (type) {
        MeasurementType.WEIGHT -> unit == MeasurementUnit.KG || unit == MeasurementUnit.LB
        MeasurementType.WAIST,
        MeasurementType.HIP,
        MeasurementType.CHEST,
        MeasurementType.BICEP,
        MeasurementType.NECK,
        -> unit == MeasurementUnit.CM || unit == MeasurementUnit.IN
        MeasurementType.BODY_FAT_PCT -> unit == MeasurementUnit.PERCENT
        MeasurementType.CUSTOM -> true
    }
}
