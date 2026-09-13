// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import br.com.colman.changes.core.db.sql.ChangesDatabase
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.LabAnalyte
import br.com.colman.changes.core.model.LabResult
import br.com.colman.changes.core.model.RecordedTime
import br.com.colman.changes.core.model.ReferenceRange
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.model.TimeZoneProvider
import br.com.colman.changes.core.model.asFailure
import br.com.colman.changes.core.model.asSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Dados de um novo resultado de exame. Tipo próprio (em vez de 7 parâmetros soltos) para não violar
 * o limite de parâmetros do Detekt; `ignoreDataClasses` isenta o construtor da checagem.
 */
public data class NewLabResult(
    val analyteId: Uuid,
    val value: Double,
    val unit: String,
    val collectedAt: Instant,
    val referenceRange: ReferenceRange?,
    val labName: String?,
    val notes: String?,
)

/**
 * Analitos e resultados de exames (Seção 7.6). Critério 7.6.2: nenhum limiar clínico é embutido
 * aqui; a marcação de fora-da-faixa é aritmética pura, feita sobre a faixa que a própria pessoa digita.
 */
public class LabRepository(
    private val database: ChangesDatabase,
    private val io: CoroutineDispatcher,
    private val clock: Clock,
    private val timeZones: TimeZoneProvider,
) {
    // --- Analitos --------------------------------------------------------------------------------

    public fun observeVisibleAnalytes(): Flow<List<LabAnalyte>> =
        database.labQueries.selectVisibleAnalytes().asFlow().mapToList(io).mapRows { it.toModel() }

    public fun observeAllAnalytes(): Flow<List<LabAnalyte>> =
        database.labQueries.selectAllAnalytes().asFlow().mapToList(io).mapRows { it.toModel() }

    public suspend fun createCustomAnalyte(label: String, defaultUnit: String): Result<LabAnalyte> = withContext(io) {
        val error = firstError(blankError("label", label), blankError("defaultUnit", defaultUnit))
        if (error != null) {
            error.asFailure()
        } else {
            val id = Uuid.random()
            val candidate = LabAnalyte(id, "CUSTOM_$id", null, label, defaultUnit, false, false)
            val now = clock.now().toEpochMilliseconds()
            database.labQueries.insertAnalyte(candidate.toRow(now, now))
            candidate.asSuccess()
        }
    }

    public suspend fun updateCustomAnalyte(analyte: LabAnalyte): Result<Unit> = withContext(io) {
        val row = database.labQueries.selectAnalyteById(analyte.id.toString()).executeAsOneOrNull()
        val error = firstError(
            blankError("customLabel", analyte.customLabel.orEmpty()),
            blankError("defaultUnit", analyte.defaultUnit)
        )
        if (row == null) {
            DomainError.NotFound("labAnalyte", analyte.id.toString()).asFailure()
        } else if (row.is_builtin.asBoolean()) {
            DomainError.Invalid("labAnalyte", DomainError.Reason.BUILTIN_IMMUTABLE).asFailure()
        } else if (error != null) {
            error.asFailure()
        } else {
            val now = clock.now().toEpochMilliseconds()
            database.labQueries.updateCustomAnalyte(
                analyte.customLabel,
                analyte.defaultUnit,
                now,
                analyte.id.toString()
            )
            Unit.asSuccess()
        }
    }

    public suspend fun setAnalyteHidden(id: Uuid, hidden: Boolean): Result<Unit> = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        database.labQueries.setAnalyteHidden(hidden.asLong(), now, id.toString())
        Unit.asSuccess()
    }

    public suspend fun deleteAnalyte(id: Uuid): Result<Unit> = withContext(io) {
        val row = database.labQueries.selectAnalyteById(id.toString()).executeAsOneOrNull()
        if (row == null) {
            DomainError.NotFound("labAnalyte", id.toString()).asFailure()
        } else if (row.is_builtin.asBoolean()) {
            DomainError.Invalid("labAnalyte", DomainError.Reason.BUILTIN_IMMUTABLE).asFailure()
        } else {
            val now = clock.now().toEpochMilliseconds()
            database.labQueries.softDeleteAnalyte(now, id.toString())
            Unit.asSuccess()
        }
    }

    public suspend fun restoreAnalyte(id: Uuid): Result<Unit> = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        database.labQueries.restoreAnalyte(now, id.toString())
        Unit.asSuccess()
    }

    // --- Resultados ------------------------------------------------------------------------------

    public fun observeResults(analyteId: Uuid): Flow<List<LabResult>> =
        database.labQueries.selectResultsByAnalyte(analyteId.toString()).asFlow().mapToList(io)
            .mapRows { it.toModel() }

    public fun observeAllResults(): Flow<List<LabResult>> =
        database.labQueries.selectAllResults().asFlow().mapToList(io).mapRows { it.toModel() }

    public suspend fun createResult(newResult: NewLabResult): Result<LabResult> = withContext(io) {
        val analyteExists =
            database.labQueries.selectAnalyteById(newResult.analyteId.toString()).executeAsOneOrNull() != null
        val now = clock.now()
        val error = firstError(
            if (analyteExists) null else DomainError.NotFound("labAnalyte", newResult.analyteId.toString()),
            finiteError("value", newResult.value),
            referenceRangeError(newResult.referenceRange),
            futureError("collectedAt", newResult.collectedAt, now),
        )
        if (error != null) {
            error.asFailure()
        } else {
            val model = LabResult(
                id = Uuid.random(),
                analyteId = newResult.analyteId,
                value = newResult.value,
                unit = newResult.unit,
                collectedAt = RecordedTime.of(newResult.collectedAt, timeZones.current()),
                referenceRange = newResult.referenceRange,
                labName = newResult.labName,
                notes = newResult.notes,
            )
            val nowMillis = now.toEpochMilliseconds()
            database.labQueries.insertResult(model.toRow(nowMillis, nowMillis))
            model.asSuccess()
        }
    }

    public suspend fun updateResult(result: LabResult): Result<LabResult> = withContext(io) {
        val now = clock.now()
        val error = firstError(
            finiteError("value", result.value),
            referenceRangeError(result.referenceRange),
            futureError("collectedAt", result.collectedAt.instant, now),
        )
        if (error != null) {
            error.asFailure()
        } else {
            database.labQueries.updateResult(
                result.analyteId.toString(),
                result.value,
                result.unit,
                result.collectedAt.epochMillis,
                result.collectedAt.offsetSeconds,
                result.referenceRange?.low,
                result.referenceRange?.high,
                result.labName,
                result.notes,
                now.toEpochMilliseconds(),
                result.id.toString(),
            )
            result.asSuccess()
        }
    }

    public suspend fun deleteResult(id: Uuid): Result<Unit> = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        database.labQueries.softDeleteResult(now, id.toString())
        Unit.asSuccess()
    }

    public suspend fun restoreResult(id: Uuid): Result<Unit> = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        database.labQueries.restoreResult(now, id.toString())
        Unit.asSuccess()
    }

    private fun referenceRangeError(range: ReferenceRange?): DomainError? = range?.let {
        val lowError = it.low?.let { low -> finiteError("referenceRange.low", low) }
        val highError = it.high?.let { high -> finiteError("referenceRange.high", high) }
        val orderError = if (it.low != null && it.high != null && it.low > it.high) {
            DomainError.Invalid("referenceRange", DomainError.Reason.END_BEFORE_START)
        } else {
            null
        }
        firstError(lowError, highError, orderError)
    }
}
