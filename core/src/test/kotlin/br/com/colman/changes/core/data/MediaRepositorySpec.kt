// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import app.cash.turbine.test
import br.com.colman.changes.core.db.createDatabase
import br.com.colman.changes.core.model.MediaOwnerType
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.core.model.isSuccess
import br.com.colman.changes.core.testing.FileMediaStorage
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.FixedTimeZoneProvider
import br.com.colman.changes.core.testing.TestZones
import br.com.colman.changes.core.testing.inMemoryDriver
import br.com.colman.changes.core.testing.testDatabase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import java.io.ByteArrayInputStream
import kotlin.io.path.createTempDirectory
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

class MediaRepositorySpec : FunSpec({
    val io = UnconfinedTestDispatcher()
    fun bytes(text: String) = ByteArrayInputStream(text.toByteArray())

    test("attach writes the file, computes its checksum and inserts the row with the recorded capture time") {
        val clock = FixedClock()
        val zones = FixedTimeZoneProvider(TestZones.SAO_PAULO)
        val database = testDatabase(clock)
        val storage = FileMediaStorage(createTempDirectory().toFile())
        val repository = MediaRepository(database, io, clock, zones, storage)
        val ownerId = Uuid.random()

        val result = repository.attach(
            MediaOwnerType.BODY_CHANGE_ENTRY,
            ownerId,
            bytes("conteudo"),
            "image/png",
            clock.now
        )
        result.isSuccess shouldBe true
        val created = result.getOrNull().shouldNotBeNull()
        created.relativePath shouldBe "${created.id}.png"
        created.capturedAt?.epochMillis shouldBe clock.now.toEpochMilliseconds()
        storage.exists(created.relativePath) shouldBe true

        val row = database.mediaQueries.selectById(created.id.toString()).executeAsOne()
        row.owner_type shouldBe "BODY_CHANGE_ENTRY"
        row.owner_id shouldBe ownerId.toString()
        row.mime_type shouldBe "image/png"
        row.checksum_sha256 shouldBe created.checksumSha256
        row.created_at shouldBe clock.now.toEpochMilliseconds()
    }

    test("attach without a captured time stores capturedAt as null") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val storage = FileMediaStorage(createTempDirectory().toFile())
        val repository = MediaRepository(database, io, clock, FixedTimeZoneProvider(), storage)

        val created = repository.attach(
            MediaOwnerType.NOTE,
            Uuid.random(),
            bytes("x"),
            "image/jpeg",
            null
        ).getOrNull().shouldNotBeNull()

        created.capturedAt.shouldBeNull()
        created.relativePath shouldBe "${created.id}.jpg"
    }

    test("attach falls back to jpg for an unknown mime type") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val storage = FileMediaStorage(createTempDirectory().toFile())
        val repository = MediaRepository(database, io, clock, FixedTimeZoneProvider(), storage)

        val created = repository.attach(
            MediaOwnerType.NOTE,
            Uuid.random(),
            bytes("x"),
            "application/octet-stream",
            null
        )
            .getOrNull().shouldNotBeNull()

        created.relativePath shouldBe "${created.id}.jpg"
    }

    test("attach removes the file it just wrote if the database insert fails") {
        val clock = FixedClock()
        val driver = inMemoryDriver()
        val database = createDatabase(driver)
        val storage = FileMediaStorage(createTempDirectory().toFile())
        val repository = MediaRepository(database, io, clock, FixedTimeZoneProvider(), storage)
        driver.execute(null, "DROP TABLE media_attachment", 0)

        runCatching { repository.attach(MediaOwnerType.NOTE, Uuid.random(), bytes("x"), "image/png", null) }

        storage.listAll().shouldBeEmpty()
    }

    test("observeByOwner lists only the given owner's non-deleted attachments") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val storage = FileMediaStorage(createTempDirectory().toFile())
        val repository = MediaRepository(database, io, clock, FixedTimeZoneProvider(), storage)
        val ownerId = Uuid.random()
        val mine = repository.attach(
            MediaOwnerType.NOTE,
            ownerId,
            bytes("a"),
            "image/png",
            null
        ).getOrNull().shouldNotBeNull()
        repository.attach(MediaOwnerType.NOTE, Uuid.random(), bytes("b"), "image/png", null)

        repository.observeByOwner(MediaOwnerType.NOTE, ownerId).test {
            awaitItem().map { it.id } shouldBe listOf(mine.id)
        }
    }

    test("updateCaption changes only the caption and updated_at") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val storage = FileMediaStorage(createTempDirectory().toFile())
        val repository = MediaRepository(database, io, clock, FixedTimeZoneProvider(), storage)
        val created = repository.attach(
            MediaOwnerType.NOTE,
            Uuid.random(),
            bytes("a"),
            "image/png",
            null
        ).getOrNull().shouldNotBeNull()
        clock.advance(1.minutes)

        repository.updateCaption(created.id, "legenda").isSuccess shouldBe true

        val row = database.mediaQueries.selectById(created.id.toString()).executeAsOne()
        row.caption shouldBe "legenda"
        row.updated_at shouldBe clock.now.toEpochMilliseconds()
    }

    test("delete then restore an attachment") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val storage = FileMediaStorage(createTempDirectory().toFile())
        val repository = MediaRepository(database, io, clock, FixedTimeZoneProvider(), storage)
        val created = repository.attach(
            MediaOwnerType.NOTE,
            Uuid.random(),
            bytes("a"),
            "image/png",
            null
        ).getOrNull().shouldNotBeNull()

        repository.delete(created.id).isSuccess shouldBe true
        database.mediaQueries.selectById(created.id.toString()).executeAsOne().deleted_at.shouldNotBeNull()

        repository.restore(created.id).isSuccess shouldBe true
        database.mediaQueries.selectById(created.id.toString()).executeAsOne().deleted_at.shouldBeNull()
    }

    test("openRead reads back exactly what was written, and null for a missing file") {
        val clock = FixedClock()
        val database = testDatabase(clock)
        val storage = FileMediaStorage(createTempDirectory().toFile())
        val repository = MediaRepository(database, io, clock, FixedTimeZoneProvider(), storage)
        val created = repository.attach(MediaOwnerType.NOTE, Uuid.random(), bytes("conteudo"), "image/png", null)
            .getOrNull().shouldNotBeNull()

        repository.openRead(created).use { it?.readBytes()?.decodeToString() } shouldBe "conteudo"
        repository.openRead(created.copy(relativePath = "${Uuid.random()}.png")).shouldBeNull()
    }
})
