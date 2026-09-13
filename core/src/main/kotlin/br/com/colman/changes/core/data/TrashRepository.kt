// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import br.com.colman.changes.core.db.sql.ChangesDatabase
import br.com.colman.changes.core.db.sql.SelectTrash
import br.com.colman.changes.core.media.MediaStorage
import br.com.colman.changes.core.model.MediaOwnerType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** O que pode estar na lixeira. Itens builtin de catálogo nunca são excluídos, então nunca aparecem. */
public enum class TrashKind {
    MEDICATION,
    REGIMEN,
    DOSE_LOG,
    BODY_CHANGE_TYPE,
    BODY_CHANGE_ENTRY,
    MEASUREMENT,
    EXERCISE,
    HEALTH_CONDITION,
    LAB_ANALYTE,
    LAB_RESULT,
    MOOD,
    CALENDAR_EVENT,
}

/**
 * Um item da lixeira. [label] é o texto da própria pessoa que ajuda a reconhecer o item (nome, título,
 * nota), vazio quando não há; a tela mostra o tipo e as datas junto. [purgeAt] é quando ele sai sozinho.
 */
public data class TrashItem(
    val kind: TrashKind,
    val id: Uuid,
    val deletedAt: Instant,
    val purgeAt: Instant,
    val label: String,
)

/**
 * Lixeira de 30 dias (Seção 9, ADR 0008). Restaurar desfaz a exclusão (uma entrada volta com as fotos
 * excluídas junto com ela). Purgar apaga o texto livre, deixa um tombstone com `deleted_at = 0` e
 * `updated_at` novo (vence cópias antigas num merge) e apaga os arquivos de mídia depois do commit.
 */
public class TrashRepository(
    private val database: ChangesDatabase,
    private val media: MediaStorage,
    private val clock: Clock,
    private val io: CoroutineDispatcher,
) {
    /** Itens excluídos há no máximo 30 dias, do mais recente para o mais antigo. */
    public fun observe(): Flow<List<TrashItem>> {
        val since = clock.now().minus(RETENTION).toEpochMilliseconds()
        return database.trashQueries.selectTrash(since).asFlow().mapToList(io).mapRows(::itemOf)
    }

    /** Desfaz a exclusão. Fotos excluídas junto com o item (mesmo instante) voltam com ele. */
    public suspend fun restore(item: TrashItem): Unit = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        val id = item.id.toString()
        val owner = OWNERS[item.kind]
        database.transaction {
            restorers.getValue(item.kind)(now, id)
            if (owner != null) {
                database.mediaQueries.restoreByOwnerDeletedAt(now, owner.name, id, item.deletedAt.toEpochMilliseconds())
            }
        }
    }

    /**
     * Exclui definitivamente. Um item restaurado nesse meio tempo (tela desatualizada) não é tocado,
     * nem as fotos dele. Devolve quantos arquivos de mídia saíram.
     */
    public suspend fun purge(item: TrashItem): Int = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        val id = item.id.toString()
        val owner = OWNERS[item.kind]
        val paths = database.transactionWithResult {
            val purged = purgers.getValue(item.kind)(now, id)
            if (purged > 0 && owner != null) {
                val collected = database.mediaQueries.selectPurgeablePathsByOwner(owner.name, id).executeAsList()
                database.mediaQueries.purgeByOwner(now, owner.name, id)
                collected
            } else {
                emptyList()
            }
        }
        deleteFiles(paths)
    }

    /** Esvazia a lixeira inteira. Devolve quantos arquivos de mídia saíram. */
    public suspend fun empty(): Int = withContext(io) {
        purgeBefore(clock.now().toEpochMilliseconds() + 1)
    }

    /** Purga o que foi excluído há mais de 30 dias. Chamado na abertura do app. */
    public suspend fun purgeExpired(): Int = withContext(io) {
        purgeBefore(clock.now().minus(RETENTION).toEpochMilliseconds())
    }

    private suspend fun purgeBefore(cutoff: Long): Int {
        val now = clock.now().toEpochMilliseconds()
        val paths = database.transactionWithResult {
            val collected = database.mediaQueries.selectExpiredPaths(cutoff).executeAsList()
            for (purge in expiredPurgers) purge(now, cutoff)
            database.mediaQueries.purgeExpired(now, cutoff)
            collected
        }
        return deleteFiles(paths)
    }

    private suspend fun deleteFiles(paths: List<String>): Int {
        var removed = 0
        for (path in paths) {
            if (media.delete(path)) removed++
        }
        return removed
    }

    private fun itemOf(row: SelectTrash): TrashItem {
        val deletedAt = Instant.fromEpochMilliseconds(row.deleted_at)
        return TrashItem(TrashKind.valueOf(row.kind), row.id.asUuid(), deletedAt, deletedAt + RETENTION, row.label)
    }

    private val restorers: Map<TrashKind, (Long, String) -> Unit> = mapOf(
        TrashKind.MEDICATION to { now, id -> database.medicationQueries.restore(now, id) },
        TrashKind.REGIMEN to { now, id -> database.regimenQueries.restore(now, id) },
        TrashKind.DOSE_LOG to { now, id -> database.doseLogQueries.restore(now, id) },
        TrashKind.BODY_CHANGE_TYPE to { now, id -> database.bodyChangeQueries.restoreType(now, id) },
        TrashKind.BODY_CHANGE_ENTRY to { now, id -> database.bodyChangeQueries.restoreEntry(now, id) },
        TrashKind.MEASUREMENT to { now, id -> database.measurementQueries.restore(now, id) },
        TrashKind.EXERCISE to { now, id -> database.exerciseQueries.restore(now, id) },
        TrashKind.HEALTH_CONDITION to { now, id -> database.healthConditionQueries.restore(now, id) },
        TrashKind.LAB_ANALYTE to { now, id -> database.labQueries.restoreAnalyte(now, id) },
        TrashKind.LAB_RESULT to { now, id -> database.labQueries.restoreResult(now, id) },
        TrashKind.MOOD to { now, id -> database.moodQueries.restore(now, id) },
        TrashKind.CALENDAR_EVENT to { now, id -> database.calendarQueries.restore(now, id) },
    )

    /** Purga uma linha pelo id; devolve quantas linhas mudaram (0 se ela já não estava na lixeira). */
    private val purgers: Map<TrashKind, (Long, String) -> Long> = mapOf(
        TrashKind.MEDICATION to { now, id -> database.medicationQueries.purgeById(now, id).value },
        TrashKind.REGIMEN to { now, id -> database.regimenQueries.purgeById(now, id).value },
        TrashKind.DOSE_LOG to { now, id -> database.doseLogQueries.purgeById(now, id).value },
        TrashKind.BODY_CHANGE_TYPE to { now, id -> database.bodyChangeQueries.purgeTypeById(now, id).value },
        TrashKind.BODY_CHANGE_ENTRY to { now, id -> database.bodyChangeQueries.purgeEntryById(now, id).value },
        TrashKind.MEASUREMENT to { now, id -> database.measurementQueries.purgeById(now, id).value },
        TrashKind.EXERCISE to { now, id -> database.exerciseQueries.purgeById(now, id).value },
        TrashKind.HEALTH_CONDITION to { now, id -> database.healthConditionQueries.purgeById(now, id).value },
        TrashKind.LAB_ANALYTE to { now, id -> database.labQueries.purgeAnalyteById(now, id).value },
        TrashKind.LAB_RESULT to { now, id -> database.labQueries.purgeResultById(now, id).value },
        TrashKind.MOOD to { now, id -> database.moodQueries.purgeById(now, id).value },
        TrashKind.CALENDAR_EVENT to { now, id -> database.calendarQueries.purgeById(now, id).value },
    )

    private val expiredPurgers: List<(Long, Long) -> Unit> = listOf(
        { now, cutoff -> database.medicationQueries.purgeExpired(now, cutoff) },
        { now, cutoff -> database.regimenQueries.purgeExpired(now, cutoff) },
        { now, cutoff -> database.doseLogQueries.purgeExpired(now, cutoff) },
        { now, cutoff -> database.bodyChangeQueries.purgeExpiredType(now, cutoff) },
        { now, cutoff -> database.bodyChangeQueries.purgeExpiredEntry(now, cutoff) },
        { now, cutoff -> database.measurementQueries.purgeExpired(now, cutoff) },
        { now, cutoff -> database.exerciseQueries.purgeExpired(now, cutoff) },
        { now, cutoff -> database.healthConditionQueries.purgeExpired(now, cutoff) },
        { now, cutoff -> database.labQueries.purgeExpiredAnalyte(now, cutoff) },
        { now, cutoff -> database.labQueries.purgeExpiredResult(now, cutoff) },
        { now, cutoff -> database.moodQueries.purgeExpired(now, cutoff) },
        { now, cutoff -> database.calendarQueries.purgeExpired(now, cutoff) },
    )

    private companion object {
        val RETENTION = 30.days

        /** Tipos que podem ter mídia anexada: a mídia segue o dono na restauração e na purga. */
        val OWNERS: Map<TrashKind, MediaOwnerType> = mapOf(
            TrashKind.BODY_CHANGE_ENTRY to MediaOwnerType.BODY_CHANGE_ENTRY,
            TrashKind.MEASUREMENT to MediaOwnerType.MEASUREMENT,
            TrashKind.LAB_RESULT to MediaOwnerType.LAB_RESULT,
            TrashKind.CALENDAR_EVENT to MediaOwnerType.CALENDAR_EVENT,
        )
    }
}
