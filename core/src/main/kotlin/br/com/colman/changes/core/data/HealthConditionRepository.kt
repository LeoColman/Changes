// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import br.com.colman.changes.core.db.sql.ChangesDatabase
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.HealthCondition
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.model.asFailure
import br.com.colman.changes.core.model.asSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Condições adversas de saúde (Seção 7.5). Registro livre, sem classificação de risco: o app nunca
 * contraindica nem emite alerta clínico.
 */
public class HealthConditionRepository(
    private val database: ChangesDatabase,
    private val io: CoroutineDispatcher,
    private val clock: Clock,
) {
    /** As com `affectsTreatment = true` vêm primeiro, já garantido pela query. */
    public fun observeAll(): Flow<List<HealthCondition>> =
        database.healthConditionQueries.selectAll().asFlow().mapToList(io).mapRows { it.toModel() }

    /** `draft.id` é ignorado: um id novo é gerado para o registro. */
    public suspend fun create(draft: HealthCondition): Result<HealthCondition> = withContext(io) {
        val error = conditionError(draft.label, draft.diagnosedAt, draft.resolvedAt)
        if (error != null) {
            error.asFailure()
        } else {
            val model = draft.copy(id = Uuid.random())
            val now = clock.now().toEpochMilliseconds()
            database.healthConditionQueries.insert(model.toRow(now, now))
            model.asSuccess()
        }
    }

    public suspend fun update(condition: HealthCondition): Result<HealthCondition> = withContext(io) {
        val error = conditionError(condition.label, condition.diagnosedAt, condition.resolvedAt)
        if (error != null) {
            error.asFailure()
        } else {
            val now = clock.now().toEpochMilliseconds()
            database.healthConditionQueries.update(
                condition.label,
                condition.code,
                condition.severity.name,
                condition.status.name,
                condition.diagnosedAt?.asEpochDay(),
                condition.resolvedAt?.asEpochDay(),
                condition.affectsTreatment.asLong(),
                condition.notes,
                now,
                condition.id.toString(),
            )
            condition.asSuccess()
        }
    }

    public suspend fun delete(id: Uuid): Result<Unit> = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        database.healthConditionQueries.softDelete(now, id.toString())
        Unit.asSuccess()
    }

    public suspend fun restore(id: Uuid): Result<Unit> = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        database.healthConditionQueries.restore(now, id.toString())
        Unit.asSuccess()
    }

    private fun conditionError(label: String, diagnosedAt: LocalDate?, resolvedAt: LocalDate?): DomainError? {
        val dateError = if (diagnosedAt != null && resolvedAt != null && resolvedAt < diagnosedAt) {
            DomainError.Invalid("resolvedAt", DomainError.Reason.END_BEFORE_START)
        } else {
            null
        }
        return firstError(blankError("label", label), dateError)
    }
}
