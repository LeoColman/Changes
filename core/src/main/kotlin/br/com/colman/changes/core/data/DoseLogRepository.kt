// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import br.com.colman.changes.core.db.sql.ChangesDatabase
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.Dose
import br.com.colman.changes.core.model.DoseLog
import br.com.colman.changes.core.model.DoseSchedule
import br.com.colman.changes.core.model.InjectionSite
import br.com.colman.changes.core.model.InjectionSiteRotation
import br.com.colman.changes.core.model.RecordedTime
import br.com.colman.changes.core.model.Regimen
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.model.Route
import br.com.colman.changes.core.model.TimeZoneProvider
import br.com.colman.changes.core.model.asFailure
import br.com.colman.changes.core.model.asSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Fato neutro de adesão: quantas doses previstas foram registradas na janela (Seção 7.1). */
public data class Adherence(public val registered: Int, public val expected: Int, public val windowDays: Int)

/** Dados de uma nova dose avulsa. Tipo próprio para não violar o limite de parâmetros do Detekt. */
public data class NewDoseLog(
    val regimenId: Uuid?,
    val medicationId: Uuid,
    val dose: Dose,
    val route: Route,
    val injectionSite: InjectionSite?,
    val takenAt: Instant,
    val notes: String?,
)

private const val DEFAULT_ADHERENCE_WINDOW_DAYS = 90

/**
 * Histórico de doses (Seção 7.1). Fala com o banco de regimes e medicações diretamente: nunca
 * depende de [RegimenRepository] ou [MedicationRepository].
 */
public class DoseLogRepository(
    private val database: ChangesDatabase,
    private val io: CoroutineDispatcher,
    private val clock: Clock,
    private val timeZones: TimeZoneProvider,
) {
    /** Um registro pelo id, inclusive se estiver na lixeira (para edição e restauração). */
    public suspend fun get(id: Uuid): DoseLog? = withContext(io) {
        database.doseLogQueries.selectById(id.toString()).executeAsOneOrNull()?.toModel()
    }

    public fun observeHistory(medicationId: Uuid?, from: Instant, to: Instant): Flow<List<DoseLog>> =
        database.doseLogQueries.selectHistory(
            medicationId?.toString(),
            from.toEpochMilliseconds(),
            to.toEpochMilliseconds()
        )
            .asFlow().mapToList(io).mapRows { it.toModel() }

    public fun observeBetween(from: Instant, to: Instant): Flow<List<DoseLog>> =
        database.doseLogQueries.selectBetween(from.toEpochMilliseconds(), to.toEpochMilliseconds())
            .asFlow().mapToList(io).mapRows { it.toModel() }

    /** Critério 7.1.3: dose com `takenAt` depois de `clock.now()` é rejeitada. */
    public suspend fun log(newDoseLog: NewDoseLog): Result<DoseLog> = withContext(io) { persistNew(newDoseLog) }

    /** Fluxo de um toque (Seção 7.1): copia medicação, dose e via do regime. */
    public suspend fun logFromRegimen(
        regimenId: Uuid,
        takenAt: Instant,
        injectionSite: InjectionSite?,
    ): Result<DoseLog> = withContext(io) {
        val regimen = database.regimenQueries.selectById(regimenId.toString()).executeAsOneOrNull()?.toModel()
        if (regimen == null) {
            DomainError.NotFound("regimen", regimenId.toString()).asFailure()
        } else {
            persistNew(
                NewDoseLog(regimenId, regimen.medicationId, regimen.dose, regimen.route, injectionSite, takenAt, null)
            )
        }
    }

    public suspend fun update(log: DoseLog): Result<DoseLog> = withContext(io) {
        val error = firstError(
            positiveFiniteError("dose", log.dose.value),
            futureError("takenAt", log.takenAt.instant, clock.now())
        )
        if (error != null) {
            error.asFailure()
        } else {
            val now = clock.now().toEpochMilliseconds()
            database.doseLogQueries.update(
                log.dose.value,
                log.dose.unit.name,
                log.route.name,
                log.injectionSite?.name,
                log.takenAt.epochMillis,
                log.takenAt.offsetSeconds,
                log.notes,
                now,
                log.id.toString(),
            )
            log.asSuccess()
        }
    }

    public suspend fun delete(id: Uuid): Result<Unit> = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        database.doseLogQueries.softDelete(now, id.toString())
        Unit.asSuccess()
    }

    public suspend fun restore(id: Uuid): Result<Unit> = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        database.doseLogQueries.restore(now, id.toString())
        Unit.asSuccess()
    }

    /** Local menos usado nos últimos registros (Seção 7.1). Sugestão mecânica, não clínica. */
    public suspend fun suggestInjectionSite(): InjectionSite? = withContext(io) {
        val recent = database.doseLogQueries.selectRecentInjectionSites(InjectionSiteRotation.WINDOW.toLong())
            .executeAsList()
            .filterNotNull()
            .map { InjectionSite.valueOf(it) }
        InjectionSiteRotation.suggest(recent)
    }

    /** Fato neutro: doses previstas x doses registradas em `[today - (days-1), today]`. */
    public suspend fun adherence(
        regimen: Regimen,
        today: LocalDate,
        days: Int = DEFAULT_ADHERENCE_WINDOW_DAYS,
    ): Adherence = withContext(io) {
        val zone = timeZones.current()
        val windowStart = today.minus(days - 1, DateTimeUnit.DAY)
        val expected = DoseSchedule.occurrences(regimen, windowStart, today).size
        val fromMillis = LocalDateTime(windowStart, LocalTime(0, 0)).toInstant(zone).toEpochMilliseconds()
        val toMillis = LocalDateTime(
            today.plus(1, DateTimeUnit.DAY),
            LocalTime(0, 0)
        ).toInstant(zone).toEpochMilliseconds()
        val registered = database.doseLogQueries.countForRegimenBetween(
            regimen.id.toString(),
            fromMillis,
            toMillis
        ).executeAsOne()
        Adherence(registered.toInt(), expected, days)
    }

    private fun persistNew(newDoseLog: NewDoseLog): Result<DoseLog> {
        val now = clock.now()
        val error = newLogError(newDoseLog.regimenId, newDoseLog.medicationId, newDoseLog.dose, newDoseLog.takenAt, now)
        return if (error != null) {
            error.asFailure()
        } else {
            val recordedTakenAt = RecordedTime.of(newDoseLog.takenAt, timeZones.current())
            val model = DoseLog(
                id = Uuid.random(),
                regimenId = newDoseLog.regimenId,
                medicationId = newDoseLog.medicationId,
                dose = newDoseLog.dose,
                route = newDoseLog.route,
                injectionSite = newDoseLog.injectionSite,
                takenAt = recordedTakenAt,
                notes = newDoseLog.notes,
            )
            val nowMillis = now.toEpochMilliseconds()
            database.doseLogQueries.insert(model.toRow(nowMillis, nowMillis))
            model.asSuccess()
        }
    }

    private fun newLogError(
        regimenId: Uuid?,
        medicationId: Uuid,
        dose: Dose,
        takenAt: Instant,
        now: Instant,
    ): DomainError? {
        val regimenError = regimenId?.let { id ->
            val exists = database.regimenQueries.selectById(id.toString()).executeAsOneOrNull() != null
            if (exists) null else DomainError.NotFound("regimen", id.toString())
        }
        val medicationExists =
            database.medicationQueries.selectById(medicationId.toString()).executeAsOneOrNull() != null
        val medicationError = if (medicationExists) {
            null
        } else {
            DomainError.NotFound(
                "medication",
                medicationId.toString()
            )
        }
        return firstError(
            positiveFiniteError("dose", dose.value),
            futureError("takenAt", takenAt, now),
            regimenError,
            medicationError
        )
    }
}
