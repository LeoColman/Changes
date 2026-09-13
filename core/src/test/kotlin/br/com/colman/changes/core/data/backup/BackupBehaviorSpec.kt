// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data.backup

import app.cash.sqldelight.Query
import br.com.colman.changes.core.data.Codecs
import br.com.colman.changes.core.db.CURRENT_SCHEMA_VERSION
import br.com.colman.changes.core.db.sql.Body_change_type
import br.com.colman.changes.core.db.sql.Dose_log
import br.com.colman.changes.core.db.sql.Exercise_session
import br.com.colman.changes.core.db.sql.Mood_log
import br.com.colman.changes.core.model.BackupRejection
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.errorOrNull
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.core.testing.testDataset
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

/** Seção 7.8 e ADR 0009: prévia, merge, mídias, staging e recuperação. */
class BackupBehaviorSpec : BehaviorSpec({
    val state = completeState()

    fun rejectionOf(result: br.com.colman.changes.core.model.Result<*>): BackupRejection? =
        (result.errorOrNull() as? DomainError.BackupRejected)?.reason

    Given("an attachment whose file was already gone on the exporting device") {
        Then("the export declares it missing, and the import accepts everything else") {
            runTest {
                val source = device()
                state.applyTo(source)
                source.media.delete(state.attachments.first { it.deleted_at == null }.relative_path)
                val expectedMissing = state.attachments.count { it.deleted_at == null && !source.media.exists(it.relative_path) }
                val out = ByteArrayOutputStream()
                val summary = source.backup.export(out).getOrNull().shouldNotBeNull()
                summary.missingMedia shouldBe expectedMissing
                summary.mediaFiles shouldBe source.media.listAll().size
                summary.rows shouldBe BackupFormat.EXPORTED.associateWith { source.dump().getValue(it).size }

                val target = device()
                val plan = target.backup.plan(
                    archiveOf(out.toByteArray()),
                    ImportMode.REPLACE
                ).getOrNull().shouldNotBeNull()
                plan.missingMedia shouldBe expectedMissing
                plan.mediaToAdd shouldBe summary.mediaFiles
                plan.schemaVersion shouldBe CURRENT_SCHEMA_VERSION
                plan.appVersion shouldBe "test"
                plan.exportedAt shouldBe source.clock.now()
                plan.mode shouldBe ImportMode.REPLACE
                target.backup.apply(plan).getOrNull().shouldNotBeNull().mediaAdded shouldBe summary.mediaFiles
                target.dump() shouldBe source.dump()
                target.mediaChecksums() shouldBe source.mediaChecksums()
            }
        }
    }

    Given("a device that already has the archive, where each side changed a different row") {
        Then("the preview counts one update and one kept row, and applying does exactly that") {
            runTest {
                val source = device()
                source.database.exerciseQueries.insert(exercise(FIRST, 1_000L))
                source.database.exerciseQueries.insert(exercise(SECOND, 1_000L))
                val target = device()
                target.import(source.exportBytes(), ImportMode.MERGE)
                target.touch("exercise_session", FIRST, "activity", "device", by = 10)
                source.touch("exercise_session", SECOND, "activity", "archive", by = 10)

                val plan = target.backup.plan(
                    archiveOf(source.exportBytes()),
                    ImportMode.MERGE
                ).getOrNull().shouldNotBeNull()
                val exercises = plan.tables.single { it.table == "exercise_session" }
                exercises shouldBe TableCounts("exercise_session", updated = 1, kept = 1)
                plan.tables.filter { it.table != "exercise_session" }.sumOf { it.inserted + it.updated + it.kept } shouldBe 0
                plan.tables.sumOf { it.unchanged } shouldBeGreaterThan 0

                target.backup.apply(plan).getOrNull().shouldNotBeNull().tables shouldBe plan.tables
                val activities = target.dump().getValue("exercise_session").associate { it["id"] to it["activity"] }
                activities shouldBe mapOf(FIRST to "device", SECOND to "archive")
            }
        }
        Then("replacing counts every row of the archive as inserted and every row of the device as removed") {
            runTest {
                val source = device()
                source.database.exerciseQueries.insert(exercise(FIRST, 1_000L))
                val target = device()
                target.database.exerciseQueries.insert(exercise(SECOND, 1_000L))
                target.database.exerciseQueries.insert(exercise(THIRD, 1_000L))
                val plan = target.backup.plan(
                    archiveOf(source.exportBytes()),
                    ImportMode.REPLACE
                ).getOrNull().shouldNotBeNull()
                plan.tables.single {
                    it.table == "exercise_session"
                } shouldBe TableCounts("exercise_session", inserted = 1, removed = 2)
                target.backup.apply(plan).getOrNull().shouldNotBeNull().tables shouldBe plan.tables
                target.dump() shouldBe source.dump()
            }
        }
    }

    Given("two check-ins for the same day with different ids (Seção 7.7)") {
        Then("the newer one survives, whichever side imports") {
            runTest {
                val older = device().apply { database.moodQueries.insert(mood(FIRST, updatedAt = 100L)) }
                val newer = device().apply { database.moodQueries.insert(mood(SECOND, updatedAt = 200L)) }
                val olderArchive = older.exportBytes()
                val newerArchive = newer.exportBytes()

                val keep = newer.backup.plan(archiveOf(olderArchive), ImportMode.MERGE).getOrNull().shouldNotBeNull()
                keep.tables.single { it.table == "mood_log" } shouldBe TableCounts("mood_log", kept = 1)
                newer.backup.apply(keep)
                newer.dump().getValue("mood_log").map { it["id"] } shouldBe listOf(SECOND)

                val replace = older.backup.plan(archiveOf(newerArchive), ImportMode.MERGE).getOrNull().shouldNotBeNull()
                replace.tables.single { it.table == "mood_log" } shouldBe TableCounts("mood_log", updated = 1)
                older.backup.apply(replace)
                older.dump().getValue("mood_log").map { it["id"] } shouldBe listOf(SECOND)
            }
        }
    }

    Given("a custom catalog code that exists on the device under another id") {
        Then("merging is refused, since only a tampered file does that, and replacing is fine") {
            runTest {
                val source = device()
                source.database.bodyChangeQueries.insertType(customType(FIRST, "CUSTOM_shared"))
                val target = device()
                target.database.bodyChangeQueries.insertType(customType(SECOND, "CUSTOM_shared"))
                val archive = source.exportBytes()
                target.rejection(archive, ImportMode.MERGE) shouldBe BackupRejection.INVALID_DATA
                target.import(archive, ImportMode.REPLACE)
                target.dump() shouldBe source.dump()
            }
        }
    }

    Given("a device database that already breaks a reference") {
        Then("merging refuses to commit on top of it, and replacing repairs it") {
            runTest {
                val source = device()
                val target = device()
                target.driver.execute(null, "PRAGMA foreign_keys = OFF", 0)
                target.database.doseLogQueries.insert(danglingDose())
                target.driver.execute(null, "PRAGMA foreign_keys = ON", 0)
                val archive = source.exportBytes()
                val before = target.dump()

                val merge = target.backup.plan(archiveOf(archive), ImportMode.MERGE).getOrNull().shouldNotBeNull()
                rejectionOf(target.backup.apply(merge)) shouldBe BackupRejection.INVALID_DATA
                target.dump() shouldBe before

                target.import(archive, ImportMode.REPLACE)
                target.dump() shouldBe source.dump()
            }
        }
    }

    Given("media already on the importing device") {
        Then("merging adds the archive's files and keeps the device's own") {
            runTest {
                val source = device().also { state.applyTo(it) }
                val target = device().also { StateBuilder(kotlin.random.Random(99)).build().applyTo(it) }
                val expected = target.mediaChecksums() + source.mediaChecksums()
                target.import(source.exportBytes(), ImportMode.MERGE)
                target.mediaChecksums() shouldBe expected
            }
        }
        Then("replacing removes every file no attachment references anymore") {
            runTest {
                val source = device().also { state.applyTo(it) }
                val target = device().also { StateBuilder(kotlin.random.Random(99)).build().applyTo(it) }
                target.media.write("orphan.jpg", "orphan".byteInputStream())
                target.import(source.exportBytes(), ImportMode.REPLACE)
                target.mediaChecksums() shouldBe source.mediaChecksums()
            }
        }
    }

    Given("a staged file whose checksum does not match") {
        Then("nothing is committed and the staging area is cleaned") {
            runTest {
                val source = device().also { state.applyTo(it) }
                val target = device(storage = ::CorruptingStorage)
                val before = target.dump()
                val plan = target.backup.plan(
                    archiveOf(source.exportBytes()),
                    ImportMode.MERGE
                ).getOrNull().shouldNotBeNull()
                rejectionOf(target.backup.apply(plan)) shouldBe BackupRejection.CHECKSUM_MISMATCH
                target.dump() shouldBe before
                target.media.listAll().shouldBeEmpty()
            }
        }
    }

    Given("a storage failure while staging") {
        Then("the import fails as IMPORT_FAILED with nothing committed") {
            runTest {
                val source = device().also { state.applyTo(it) }
                val target = device(storage = ::FailingStorage)
                val before = target.dump()
                val plan = target.backup.plan(
                    archiveOf(source.exportBytes()),
                    ImportMode.MERGE
                ).getOrNull().shouldNotBeNull()
                rejectionOf(target.backup.apply(plan)) shouldBe BackupRejection.IMPORT_FAILED
                target.dump() shouldBe before
                target.media.listAll().shouldBeEmpty()
            }
        }
    }

    Given("an archive that changed between the preview and the import") {
        Then("the import validates again and refuses it") {
            runTest {
                val source = device()
                val target = device()
                val file = archiveOf(source.exportBytes())
                val plan = target.backup.plan(file, ImportMode.MERGE).getOrNull().shouldNotBeNull()
                file.writeBytes("not a zip".toByteArray())
                rejectionOf(target.backup.apply(plan)) shouldBe BackupRejection.UNREADABLE
            }
        }
    }

    Given("an import that stopped after the commit (process killed before the files were moved)") {
        Then("the next start moves referenced staged files into place and drops the rest") {
            runTest {
                val device = device().also { state.applyTo(it) }
                val alive = state.attachments.filter { it.deleted_at == null }.map { it.relative_path }
                val interrupted = alive.first()
                val untouched = alive.last()
                val content = device.mediaChecksums().getValue(interrupted)
                device.media.move(interrupted, ".import-a/$interrupted")
                device.media.write(".import-a/unreferenced.jpg", "x".byteInputStream())
                device.media.write(".import-b/$untouched", "stale copy".byteInputStream())
                val before = device.mediaChecksums().getValue(untouched)

                device.backup.recoverInterruptedImports()

                device.mediaChecksums().getValue(interrupted) shouldBe content
                device.mediaChecksums().getValue(untouched) shouldBe before
                device.media.listAll().filter { it.startsWith(".import-") }.shouldBeEmpty()
            }
        }
    }

    Given("files nobody references") {
        Then("the sweep removes them and keeps referenced files, trashed items' files and staged files") {
            runTest {
                val device = device().also { state.applyTo(it) }
                val referenced = device.media.listAll()
                device.media.write("orphan.jpg", "orphan".byteInputStream())
                device.media.write(".import-z/pending.jpg", "pending".byteInputStream())
                device.backup.sweepOrphanMedia() shouldBe 1
                device.media.listAll() shouldBe (referenced + ".import-z/pending.jpg").sorted()
            }
        }
    }

    Given("a screen observing the database") {
        Then("it is told to reload after an import") {
            runTest {
                val source = device().also { state.applyTo(it) }
                val target = device()
                var notified = 0
                target.database.profileQueries.get().addListener(Query.Listener { notified++ })
                target.import(source.exportBytes(), ImportMode.REPLACE)
                notified shouldBeGreaterThan 0
            }
        }
    }

    Given("an export") {
        Then("it records when it happened") {
            runTest {
                val source = device()
                source.exportBytes()
                source.database.appMetaQueries.get().executeAsOne().last_export_at shouldBe source.clock.now().toEpochMilliseconds()
            }
        }
        Then("a destination that fails is reported as EXPORT_FAILED, and nothing is recorded") {
            runTest {
                val source = device()
                val broken = object : OutputStream() {
                    override fun write(b: Int) = throw IOException("gone")
                }
                rejectionOf(source.backup.export(broken)) shouldBe BackupRejection.EXPORT_FAILED
                source.database.appMetaQueries.get().executeAsOne().last_export_at shouldBe null
            }
        }
        Then("it covers every exported table") {
            runTest {
                val entries = unzip(device().also { state.applyTo(it) }.exportBytes())
                entries.data().keys shouldBe BackupFormat.EXPORTED.toSet()
                entries.keys.first() shouldBe BackupFormat.DATA_ENTRY
                entries.keys.last() shouldBe BackupFormat.MANIFEST_ENTRY
                entries.keys.filter { it.startsWith(BackupFormat.MEDIA_PREFIX) } shouldContainAll
                    state.attachments.filter { it.hasFile() }.map { BackupFormat.MEDIA_PREFIX + it.relative_path }
            }
        }
    }
})

private const val FIRST = "0b1e8f7a-1111-4a2b-8c3d-000000000001"
private const val SECOND = "0b1e8f7a-2222-4a2b-8c3d-000000000002"
private const val THIRD = "0b1e8f7a-3333-4a2b-8c3d-000000000003"

private fun exercise(id: String, updatedAt: Long) = Exercise_session(
    id = id,
    activity = "walk",
    duration_minutes = 30,
    intensity = "LIGHT",
    occurred_at = 1_000L,
    occurred_at_offset_seconds = 0,
    notes = null,
    created_at = 1_000L,
    updated_at = updatedAt,
    deleted_at = null,
)

private fun mood(id: String, updatedAt: Long) = Mood_log(
    id = id,
    entry_date = 20_000L,
    mood = 3,
    energy = 3,
    anxiety = null,
    dysphoria = null,
    sleep_hours = null,
    note = null,
    tags = Codecs.encodeTags(emptyList()),
    created_at = 100L,
    updated_at = updatedAt,
    deleted_at = null,
)

private fun customType(id: String, code: String) = Body_change_type(
    id = id,
    code = code,
    label_key = null,
    custom_label = "custom",
    category = "OTHER",
    is_reversible = null,
    supports_measurement = 0,
    measurement_unit = null,
    is_builtin = 0,
    is_hidden = 0,
    created_at = 1L,
    updated_at = 1L,
    deleted_at = null,
)

private fun danglingDose() = Dose_log(
    id = FIRST,
    regimen_id = null,
    medication_id = "7b0e6f0a-1d2c-4e5f-8a9b-0c1d2e3f4a5b",
    dose_value = 1.0,
    dose_unit = "MG",
    route = "INTRAMUSCULAR",
    injection_site = null,
    taken_at = 1L,
    taken_at_offset_seconds = 0,
    notes = null,
    created_at = 1L,
    updated_at = 1L,
    deleted_at = null,
).also { check(testDataset.medications.none { m -> m.id.toString() == it.medication_id }) }
