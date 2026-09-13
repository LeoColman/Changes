// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data.backup

import br.com.colman.changes.core.data.Codecs
import br.com.colman.changes.core.db.sql.Body_change_entry
import br.com.colman.changes.core.db.sql.Dose_log
import br.com.colman.changes.core.db.sql.Exercise_session
import br.com.colman.changes.core.db.sql.Measurement
import br.com.colman.changes.core.db.sql.Media_attachment
import br.com.colman.changes.core.db.sql.Mood_log
import br.com.colman.changes.core.model.MediaPaths
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.core.testing.testDataset
import io.kotest.core.Tag
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.uuid.Uuid

/** Roda só na task `stressTest`, com heap limitado (build.gradle.kts). */
object Stress : Tag()

private const val ROWS = 10_000
private const val PHOTOS = 500
private const val PHOTO_BYTES = 256 * 1024

/**
 * Critério 7.8.5: base com 10k registros e 500 fotos exporta sem OOM. As fotos somam 125 MB e a task
 * roda com 128 MB de heap: só passa se nada for materializado em memória.
 */
class BackupStressSpec : FunSpec({
    tags(Stress)

    test("10k rows and 500 photos export and validate in constant memory") {
        runTest(timeout = 10.minutes) {
            val device = device()
            device.fill()
            val file = Files.createTempFile("stress", ".ttbackup.zip").toFile().apply { deleteOnExit() }
            val summary = device.backup.export(file.outputStream()).getOrNull().shouldNotBeNull()
            summary.rows.values.sum() shouldBeGreaterThanOrEqual ROWS
            summary.mediaFiles shouldBe PHOTOS
            device.backup.plan(file, ImportMode.MERGE).getOrNull().shouldNotBeNull().tables.sumOf {
                it.unchanged
            } shouldBeGreaterThanOrEqual ROWS
        }
    }
})

private suspend fun Device.fill() {
    val medication = testDataset.medications.first().id.toString()
    val type = testDataset.bodyChangeTypes.first().id.toString()
    val photo = ByteArray(PHOTO_BYTES)
    val photos = List(PHOTOS) { index ->
        Random(index).nextBytes(photo)
        val path = MediaPaths.forNew(Uuid.random(), "jpg")
        path to media.write(path, photo.inputStream())
    }
    database.transaction {
        repeat(ROWS / 4) { index ->
            val at = 1_700_000_000_000L + index * 60_000L
            database.doseLogQueries.insert(
                Dose_log(id(), null, medication, 1.0, "MG", "INTRAMUSCULAR", null, at, 0, null, at, at, null)
            )
            database.measurementQueries.insert(Measurement(id(), "WEIGHT", null, 70.0, "KG", at, 0, null, at, at, null))
            database.exerciseQueries.insert(Exercise_session(id(), "walk", 30, "LIGHT", at, 0, null, at, at, null))
            database.moodQueries.insert(mood(index.toLong(), at))
        }
        photos.forEach { (path, checksum) ->
            val entry = id()
            database.bodyChangeQueries.insertEntry(
                Body_change_entry(entry, type, 1L, 0, null, null, null, null, 1L, 1L, null)
            )
            database.mediaQueries.insert(
                Media_attachment(id(), "BODY_CHANGE_ENTRY", entry, path, "image/jpeg", null, null, checksum, null, 1L, 1L, null)
            )
        }
    }
}

private fun id(): String = Uuid.random().toString()

private fun mood(day: Long, at: Long) = Mood_log(
    id = id(),
    entry_date = day,
    mood = 3,
    energy = 3,
    anxiety = null,
    dysphoria = null,
    sleep_hours = null,
    note = null,
    tags = Codecs.encodeTags(emptyList()),
    created_at = at,
    updated_at = at,
    deleted_at = null,
)
