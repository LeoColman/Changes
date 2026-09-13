// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import br.com.colman.changes.core.db.sql.ChangesDatabase
import br.com.colman.changes.core.model.Concentration
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.Medication
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.model.Route
import br.com.colman.changes.core.model.asFailure
import br.com.colman.changes.core.model.asSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Catálogo de medicações (Seção 6.1). Itens builtin vêm do seed e não podem ser apagados. */
public class MedicationRepository(
    private val database: ChangesDatabase,
    private val io: CoroutineDispatcher,
    private val clock: Clock,
) {
    public fun observeVisible(): Flow<List<Medication>> =
        database.medicationQueries.selectVisible().asFlow().mapToList(io).mapRows { it.toModel() }

    public fun observeAll(): Flow<List<Medication>> =
        database.medicationQueries.selectAllActive().asFlow().mapToList(io).mapRows { it.toModel() }

    public suspend fun get(id: Uuid): Medication? = withContext(io) {
        database.medicationQueries.selectById(id.toString()).executeAsOneOrNull()?.toModel()
    }

    public suspend fun createCustom(
        name: String,
        substance: String?,
        defaultRoute: Route?,
        concentration: Concentration?,
    ): Result<Medication> = withContext(io) {
        val candidate = Medication(Uuid.random(), name, substance, defaultRoute, concentration, false, false)
        val error = medicationError(candidate)
        if (error != null) {
            error.asFailure()
        } else {
            val now = clock.now().toEpochMilliseconds()
            database.medicationQueries.insert(candidate.toRow(now, now))
            candidate.asSuccess()
        }
    }

    public suspend fun updateCustom(medication: Medication): Result<Unit> = withContext(io) {
        val row = database.medicationQueries.selectById(medication.id.toString()).executeAsOneOrNull()
        val error = medicationError(medication)
        if (row == null) {
            DomainError.NotFound("medication", medication.id.toString()).asFailure()
        } else if (row.is_builtin.asBoolean()) {
            DomainError.Invalid("medication", DomainError.Reason.BUILTIN_IMMUTABLE).asFailure()
        } else if (error != null) {
            error.asFailure()
        } else {
            val now = clock.now().toEpochMilliseconds()
            database.medicationQueries.updateCustom(
                medication.name,
                medication.substance,
                medication.defaultRoute?.name,
                medication.concentration?.value,
                medication.concentration?.unit?.name,
                now,
                medication.id.toString(),
            )
            Unit.asSuccess()
        }
    }

    public suspend fun setHidden(id: Uuid, hidden: Boolean): Result<Unit> = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        database.medicationQueries.setHidden(hidden.asLong(), now, id.toString())
        Unit.asSuccess()
    }

    public suspend fun delete(id: Uuid): Result<Unit> = withContext(io) {
        val row = database.medicationQueries.selectById(id.toString()).executeAsOneOrNull()
        if (row == null) {
            DomainError.NotFound("medication", id.toString()).asFailure()
        } else if (row.is_builtin.asBoolean()) {
            DomainError.Invalid("medication", DomainError.Reason.BUILTIN_IMMUTABLE).asFailure()
        } else {
            val now = clock.now().toEpochMilliseconds()
            database.medicationQueries.softDelete(now, id.toString())
            Unit.asSuccess()
        }
    }

    public suspend fun restore(id: Uuid): Result<Unit> = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        database.medicationQueries.restore(now, id.toString())
        Unit.asSuccess()
    }

    private fun medicationError(medication: Medication): DomainError? = firstError(
        blankError("name", medication.name),
        medication.concentration?.let { positiveFiniteError("concentration", it.value) },
    )
}
