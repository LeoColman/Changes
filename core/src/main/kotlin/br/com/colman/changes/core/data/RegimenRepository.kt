// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import br.com.colman.changes.core.db.sql.ChangesDatabase
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.Dose
import br.com.colman.changes.core.model.DoseSchedule
import br.com.colman.changes.core.model.Regimen
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.model.Route
import br.com.colman.changes.core.model.Schedule
import br.com.colman.changes.core.model.asFailure
import br.com.colman.changes.core.model.asSuccess
import br.com.colman.changes.core.model.errorOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Dados de um novo regime. Tipo próprio (em vez de 8 parâmetros soltos) para não violar o limite de
 * parâmetros do Detekt; `ignoreDataClasses` isenta o construtor da checagem.
 */
public data class NewRegimen(
    val medicationId: Uuid,
    val dose: Dose,
    val route: Route,
    val schedule: Schedule,
    val timeOfDay: LocalTime?,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val notes: String?,
)

/**
 * Prescrições vigentes (Seção 7.1). Não depende de [DoseLogRepository]: cada repositório fala com o
 * banco diretamente, nunca com o outro.
 */
public class RegimenRepository(
    private val database: ChangesDatabase,
    private val io: CoroutineDispatcher,
    private val clock: Clock,
) {
    public fun observeActive(): Flow<List<Regimen>> =
        database.regimenQueries.selectActive().asFlow().mapToList(io).mapRows { it.toModel() }

    public fun observeAll(): Flow<List<Regimen>> =
        database.regimenQueries.selectAllNotDeleted().asFlow().mapToList(io).mapRows { it.toModel() }

    public suspend fun get(id: Uuid): Regimen? = withContext(io) {
        database.regimenQueries.selectById(id.toString()).executeAsOneOrNull()?.toModel()
    }

    public suspend fun create(newRegimen: NewRegimen): Result<Regimen> = withContext(io) {
        val candidate = Regimen(
            id = Uuid.random(),
            medicationId = newRegimen.medicationId,
            dose = newRegimen.dose,
            route = newRegimen.route,
            schedule = newRegimen.schedule,
            timeOfDay = newRegimen.timeOfDay,
            startDate = newRegimen.startDate,
            endDate = newRegimen.endDate,
            isActive = true,
            notes = newRegimen.notes,
        )
        val error = regimenError(candidate)
        if (error != null) {
            error.asFailure()
        } else {
            val now = clock.now().toEpochMilliseconds()
            database.regimenQueries.insert(candidate.toRow(now, now))
            candidate.asSuccess()
        }
    }

    /** Critério 7.1.2: nunca reescreve `dose_log`, mesmo mudando dose, via ou agenda. */
    public suspend fun update(regimen: Regimen): Result<Regimen> = withContext(io) { persist(regimen) }

    public suspend fun end(id: Uuid, endDate: LocalDate): Result<Regimen> = withContext(io) {
        val current = database.regimenQueries.selectById(id.toString()).executeAsOneOrNull()?.toModel()
        if (current == null) {
            DomainError.NotFound("regimen", id.toString()).asFailure()
        } else {
            persist(current.copy(endDate = endDate))
        }
    }

    /** Critério 7.1.4: excluir mantém as doses e os dados denormalizados delas (a FK só é limpa na purga). */
    public suspend fun delete(id: Uuid): Result<Unit> = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        database.regimenQueries.softDelete(now, id.toString())
        Unit.asSuccess()
    }

    public suspend fun restore(id: Uuid): Result<Unit> = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        database.regimenQueries.restore(now, id.toString())
        Unit.asSuccess()
    }

    private fun persist(regimen: Regimen): Result<Regimen> {
        val error = regimenError(regimen)
        return if (error != null) {
            error.asFailure()
        } else {
            val now = clock.now().toEpochMilliseconds()
            database.regimenQueries.update(
                regimen.medicationId.toString(),
                regimen.dose.value,
                regimen.dose.unit.name,
                regimen.route.name,
                regimen.schedule.type.name,
                Codecs.encodeSchedule(regimen.schedule),
                regimen.timeOfDay?.asMinutes(),
                regimen.startDate.asEpochDay(),
                regimen.endDate?.asEpochDay(),
                regimen.isActive.asLong(),
                regimen.notes,
                now,
                regimen.id.toString(),
            )
            regimen.asSuccess()
        }
    }

    private fun regimenError(regimen: Regimen): DomainError? {
        val medicationExists =
            database.medicationQueries.selectById(regimen.medicationId.toString()).executeAsOneOrNull() != null
        return firstError(
            positiveFiniteError("dose", regimen.dose.value),
            DoseSchedule.validate(regimen.schedule).errorOrNull(),
            endBeforeStartError(regimen),
            if (medicationExists) null else DomainError.NotFound("medication", regimen.medicationId.toString()),
        )
    }

    private fun endBeforeStartError(regimen: Regimen): DomainError? {
        val end = regimen.endDate
        return if (end != null && end < regimen.startDate) {
            DomainError.Invalid("endDate", DomainError.Reason.END_BEFORE_START)
        } else {
            null
        }
    }
}
