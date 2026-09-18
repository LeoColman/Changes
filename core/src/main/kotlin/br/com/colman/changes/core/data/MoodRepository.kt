// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import br.com.colman.changes.core.db.sql.ChangesDatabase
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.MoodLog
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.model.TimeZoneProvider
import br.com.colman.changes.core.model.asFailure
import br.com.colman.changes.core.model.asSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.Uuid

private const val SCALE_MIN = 1
private const val SCALE_MAX = 5
private const val SLEEP_HOURS_MIN = 0.0
private const val SLEEP_HOURS_MAX = 24.0

/**
 * Check-in diário de saúde mental (Seção 7.7). A nota livre nunca é lida por heurística, classificador
 * ou regra que altere a UI (critério 7.7.2): este repositório só grava e devolve o texto.
 */
public class MoodRepository(
    private val database: ChangesDatabase,
    private val io: CoroutineDispatcher,
    private val clock: Clock,
    private val timeZones: TimeZoneProvider,
) {
    public fun observeBetween(from: LocalDate, to: LocalDate): Flow<List<MoodLog>> =
        database.moodQueries.selectBetween(from.asEpochDay(), to.asEpochDay()).asFlow().mapToList(io)
            .mapRows { it.toModel() }

    public fun observeAll(): Flow<List<MoodLog>> =
        database.moodQueries.selectAll().asFlow().mapToList(io).mapRows { it.toModel() }

    public fun observe(date: LocalDate): Flow<MoodLog?> =
        database.moodQueries.selectByDateIncludingDeleted(date.asEpochDay()).asFlow().mapToOneOrNull(io)
            .mapRow { row -> if (row != null && row.deleted_at == null) row.toModel() else null }

    /**
     * Critério 7.7.1: reaproveita a mesma linha (mesmo id) se já existir uma entrada para a data,
     * mesmo apagada. `draft.id` é ignorado: a linha é escolhida pela data, não pelo id.
     */
    public suspend fun upsert(draft: MoodLog): Result<MoodLog> = withContext(io) {
        val cleanedTags = cleanTags(draft.tags)
        val error = moodError(draft)
        if (error != null) {
            error.asFailure()
        } else {
            val now = clock.now().toEpochMilliseconds()
            val existing = database.moodQueries.selectByDateIncludingDeleted(
                draft.date.asEpochDay()
            ).executeAsOneOrNull()
            val id = existing?.id?.asUuid() ?: Uuid.random()
            val model = draft.copy(id = id, tags = cleanedTags)
            database.transaction {
                if (existing == null) {
                    database.moodQueries.insert(model.toRow(now, now))
                } else {
                    database.moodQueries.overwrite(
                        mood = model.mood.toLong(),
                        energy = model.energy.toLong(),
                        anxiety = model.anxiety?.toLong(),
                        dysphoria = model.dysphoria?.toLong(),
                        relief = model.relief?.toLong(),
                        irritability = model.irritability?.toLong(),
                        emotionalIntensity = model.emotionalIntensity?.toLong(),
                        sleepHours = model.sleepHours,
                        note = model.note,
                        tags = Codecs.encodeTags(model.tags),
                        updatedAt = now,
                        id = id.toString(),
                    )
                }
            }
            model.asSuccess()
        }
    }

    public suspend fun delete(id: Uuid): Result<Unit> = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        database.moodQueries.softDelete(now, id.toString())
        Unit.asSuccess()
    }

    public suspend fun restore(id: Uuid): Result<Unit> = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        database.moodQueries.restore(now, id.toString())
        Unit.asSuccess()
    }

    private fun moodError(draft: MoodLog): DomainError? {
        val today = clock.now().toLocalDateTime(timeZones.current()).date
        val dateError = if (draft.date > today) DomainError.Invalid("date", DomainError.Reason.IN_THE_FUTURE) else null
        return firstError(
            rangeError("mood", draft.mood, SCALE_MIN..SCALE_MAX),
            rangeError("energy", draft.energy, SCALE_MIN..SCALE_MAX),
            draft.anxiety?.let { rangeError("anxiety", it, SCALE_MIN..SCALE_MAX) },
            draft.dysphoria?.let { rangeError("dysphoria", it, SCALE_MIN..SCALE_MAX) },
            draft.relief?.let { rangeError("relief", it, SCALE_MIN..SCALE_MAX) },
            draft.irritability?.let { rangeError("irritability", it, SCALE_MIN..SCALE_MAX) },
            draft.emotionalIntensity?.let { rangeError("emotionalIntensity", it, SCALE_MIN..SCALE_MAX) },
            draft.sleepHours?.let { rangeError("sleepHours", it, SLEEP_HOURS_MIN..SLEEP_HOURS_MAX) },
            dateError,
        )
    }

    /** Corta espaços, descarta vazias e remove repetidas, preservando a ordem da primeira ocorrência. */
    private fun cleanTags(tags: List<String>): List<String> {
        val result = mutableListOf<String>()
        for (raw in tags) {
            val trimmed = raw.trim()
            if (trimmed.isNotEmpty() && trimmed !in result) result += trimmed
        }
        return result
    }
}
