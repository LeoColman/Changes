// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import br.com.colman.changes.core.db.sql.ChangesDatabase
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.ExerciseIntensity
import br.com.colman.changes.core.model.ExerciseSession
import br.com.colman.changes.core.model.RecordedTime
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.model.TimeZoneProvider
import br.com.colman.changes.core.model.asFailure
import br.com.colman.changes.core.model.asSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

private const val WEEK_DAYS = 7

/** Uma semana ISO (segunda-feira) e o total de minutos de exercício nela. */
public data class WeekMinutes(public val weekStart: LocalDate, public val minutes: Int)

/** Sessões de exercício (Seção 7.4). */
public class ExerciseRepository(
    private val database: ChangesDatabase,
    private val io: CoroutineDispatcher,
    private val clock: Clock,
    private val timeZones: TimeZoneProvider,
) {
    public fun observeAll(): Flow<List<ExerciseSession>> =
        database.exerciseQueries.selectAll().asFlow().mapToList(io).mapRows { it.toModel() }

    public fun observeBetween(from: Instant, to: Instant): Flow<List<ExerciseSession>> =
        database.exerciseQueries.selectBetween(from.toEpochMilliseconds(), to.toEpochMilliseconds()).asFlow()
            .mapToList(io).mapRows { it.toModel() }

    public suspend fun create(
        activity: String,
        durationMinutes: Int,
        intensity: ExerciseIntensity,
        occurredAt: Instant,
        notes: String?,
    ): Result<ExerciseSession> = withContext(io) {
        val now = clock.now()
        val error = exerciseError(activity, durationMinutes, occurredAt, now)
        if (error != null) {
            error.asFailure()
        } else {
            val model = ExerciseSession(
                id = Uuid.random(),
                activity = activity,
                durationMinutes = durationMinutes,
                intensity = intensity,
                occurredAt = RecordedTime.of(occurredAt, timeZones.current()),
                notes = notes,
            )
            val nowMillis = now.toEpochMilliseconds()
            database.exerciseQueries.insert(model.toRow(nowMillis, nowMillis))
            model.asSuccess()
        }
    }

    public suspend fun update(session: ExerciseSession): Result<ExerciseSession> = withContext(io) {
        val now = clock.now()
        val error = exerciseError(session.activity, session.durationMinutes, session.occurredAt.instant, now)
        if (error != null) {
            error.asFailure()
        } else {
            database.exerciseQueries.update(
                session.activity,
                session.durationMinutes.toLong(),
                session.intensity.name,
                session.occurredAt.epochMillis,
                session.occurredAt.offsetSeconds,
                session.notes,
                now.toEpochMilliseconds(),
                session.id.toString(),
            )
            session.asSuccess()
        }
    }

    public suspend fun delete(id: Uuid): Result<Unit> = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        database.exerciseQueries.softDelete(now, id.toString())
        Unit.asSuccess()
    }

    public suspend fun restore(id: Uuid): Result<Unit> = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        database.exerciseQueries.restore(now, id.toString())
        Unit.asSuccess()
    }

    /** Resumo semanal (Seção 7.4): semana ISO pela data local de cada sessão, semanas vazias somam 0. */
    public suspend fun weeklyMinutes(today: LocalDate, weeks: Int): List<WeekMinutes> = withContext(io) {
        val currentWeekStart = mondayOf(today)
        val firstWeekStart = currentWeekStart.minus(WEEK_DAYS.toLong() * (weeks - 1), DateTimeUnit.DAY)
        val minutesByWeek = mutableMapOf<LocalDate, Int>()
        for (row in database.exerciseQueries.selectAll().executeAsList()) {
            val session = row.toModel()
            val weekStart = mondayOf(session.occurredAt.localDate)
            if (weekStart in firstWeekStart..currentWeekStart) {
                minutesByWeek[weekStart] = minutesByWeek.getOrDefault(weekStart, 0) + session.durationMinutes
            }
        }
        val result = mutableListOf<WeekMinutes>()
        var week = firstWeekStart
        while (week <= currentWeekStart) {
            result += WeekMinutes(week, minutesByWeek.getOrDefault(week, 0))
            week = week.plus(WEEK_DAYS, DateTimeUnit.DAY)
        }
        result
    }

    private fun exerciseError(activity: String, durationMinutes: Int, occurredAt: Instant, now: Instant): DomainError? =
        firstError(
            blankError("activity", activity),
            positiveError("durationMinutes", durationMinutes),
            futureError("occurredAt", occurredAt, now),
        )

    private fun mondayOf(date: LocalDate): LocalDate = date.minus(date.dayOfWeek.isoDayNumber - 1, DateTimeUnit.DAY)
}
