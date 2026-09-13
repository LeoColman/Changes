// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import br.com.colman.changes.core.db.sql.ChangesDatabase
import br.com.colman.changes.core.media.MediaStorage
import br.com.colman.changes.core.model.MediaAttachment
import br.com.colman.changes.core.model.MediaOwnerType
import br.com.colman.changes.core.model.MediaPaths
import br.com.colman.changes.core.model.RecordedTime
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.model.TimeZoneProvider
import br.com.colman.changes.core.model.asSuccess
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Anexos de mídia (Seção 6.1, 7.3). Blobs nunca entram no banco: só o caminho relativo e o SHA-256. */
public class MediaRepository(
    private val database: ChangesDatabase,
    private val io: CoroutineDispatcher,
    private val clock: Clock,
    private val timeZones: TimeZoneProvider,
    private val storage: MediaStorage,
) {
    public fun observeByOwner(ownerType: MediaOwnerType, ownerId: Uuid): Flow<List<MediaAttachment>> =
        database.mediaQueries.selectByOwner(ownerType.name, ownerId.toString()).asFlow().mapToList(io)
            .mapRows { it.toModel() }

    /** Grava o arquivo antes da linha; se o insert falhar, o arquivo recém-gravado é removido. */
    public suspend fun attach(
        ownerType: MediaOwnerType,
        ownerId: Uuid,
        source: InputStream,
        mimeType: String,
        capturedAt: Instant?,
    ): Result<MediaAttachment> = withContext(io) {
        val id = Uuid.random()
        val relativePath = MediaPaths.forNew(id, MediaPaths.extensionForMimeType(mimeType))
        val checksum = storage.write(relativePath, source)
        val now = clock.now().toEpochMilliseconds()
        val model = MediaAttachment(
            id = id,
            ownerType = ownerType,
            ownerId = ownerId,
            relativePath = relativePath,
            mimeType = mimeType,
            capturedAt = capturedAt?.let { RecordedTime.of(it, timeZones.current()) },
            checksumSha256 = checksum,
            caption = null,
        )
        val outcome = runCatching { database.mediaQueries.insert(model.toRow(now, now)) }
        outcome.onFailure { storage.delete(relativePath) }
        outcome.getOrThrow()
        model.asSuccess()
    }

    public suspend fun updateCaption(id: Uuid, caption: String?): Result<Unit> = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        database.mediaQueries.updateCaption(caption, now, id.toString())
        Unit.asSuccess()
    }

    public suspend fun delete(id: Uuid): Result<Unit> = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        database.mediaQueries.softDelete(now, id.toString())
        Unit.asSuccess()
    }

    public suspend fun restore(id: Uuid): Result<Unit> = withContext(io) {
        val now = clock.now().toEpochMilliseconds()
        database.mediaQueries.restore(now, id.toString())
        Unit.asSuccess()
    }

    public fun openRead(attachment: MediaAttachment): InputStream? = storage.openRead(attachment.relativePath)
}
