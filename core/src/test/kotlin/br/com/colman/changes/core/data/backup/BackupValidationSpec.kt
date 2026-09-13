// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data.backup

import br.com.colman.changes.core.db.CURRENT_SCHEMA_VERSION
import br.com.colman.changes.core.model.BackupRejection
import br.com.colman.changes.core.model.BackupRejection.CHECKSUM_MISMATCH
import br.com.colman.changes.core.model.BackupRejection.INVALID_DATA
import br.com.colman.changes.core.model.BackupRejection.MISSING_MEDIA
import br.com.colman.changes.core.model.BackupRejection.NEWER_VERSION
import br.com.colman.changes.core.model.BackupRejection.NOT_A_BACKUP
import br.com.colman.changes.core.model.BackupRejection.UNREADABLE
import br.com.colman.changes.core.model.BackupRejection.UNSUPPORTED_VERSION
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/** Seção 7.8, critérios 3 e 4, e ADR 0009: tudo validado antes de qualquer escrita. */
class BackupValidationSpec : BehaviorSpec({
    val state = completeState()

    suspend fun TestScope.sourceEntries(): Entries {
        val source = device()
        state.applyTo(source)
        return unzip(source.exportBytes())
    }

    /** Adultera um export válido e confere o motivo da recusa e o aparelho intacto. */
    fun rejects(expected: BackupRejection, tamper: (Entries) -> ByteArray) = runTest {
        val archive = tamper(sourceEntries())
        val target = device()
        val before = target.dump()
        target.rejection(archive) shouldBe expected
        target.dump() shouldBe before
        target.mediaChecksums().shouldBeEmpty()
    }

    fun accepts(tamper: (Entries) -> ByteArray) = runTest {
        device().rejection(tamper(sourceEntries())) shouldBe null
    }

    fun manifest(edit: (JsonObject) -> JsonObject): (Entries) -> ByteArray = { it.tampered { editManifest(edit) } }

    fun data(edit: (JsonObject) -> JsonObject): (Entries) -> ByteArray = { it.tampered { editData(edit) } }

    fun row(table: String, column: String, value: JsonElement, where: (JsonObject) -> Boolean = { true }) =
        data { d -> d.editRow(table, where) { it + (column to value) } }

    Given("a damaged or foreign file") {
        Then("bytes that are not a zip are unreadable") { rejects(UNREADABLE) { "not a zip".toByteArray() } }
        Then(
            "a zip whose data.json cannot be inflated is unreadable"
        ) { rejects(UNREADABLE) { corruptFirstEntry(zipOf(it)) } }
        Then("a zip without a manifest is not a backup") {
            rejects(NOT_A_BACKUP) { it.tampered { remove(BackupFormat.MANIFEST_ENTRY) } }
        }
        Then("a manifest that is not JSON is not a backup") {
            rejects(NOT_A_BACKUP) { it.tampered { this[BackupFormat.MANIFEST_ENTRY] = "{".toByteArray() } }
        }
        Then(
            "a manifest missing a field is not a backup"
        ) { rejects(NOT_A_BACKUP, manifest { m -> JsonObject(m - "counts") }) }
        Then(
            "another format is not a backup"
        ) { rejects(NOT_A_BACKUP, manifest { it + ("format" to JsonPrimitive("other")) }) }
        Then("an unreadable export date is not a backup") {
            rejects(NOT_A_BACKUP, manifest { it + ("exportedAt" to JsonPrimitive("yesterday")) })
        }
        Then("a zip without data.json is not a backup") {
            rejects(NOT_A_BACKUP) { it.tampered { remove(BackupFormat.DATA_ENTRY) } }
        }
    }

    Given("a backup from another version (criterion 7.8.4)") {
        Then("a newer schema is refused as newer") {
            rejects(NEWER_VERSION, manifest { it + ("schemaVersion" to JsonPrimitive(CURRENT_SCHEMA_VERSION + 1)) })
        }
        Then("a newer format with fields this version does not know is refused as newer, not as foreign") {
            rejects(
                NEWER_VERSION,
                manifest { it + ("formatVersion" to JsonPrimitive(2)) + ("future" to JsonPrimitive(true)) }
            )
        }
        Then("format version zero is invalid") {
            rejects(INVALID_DATA, manifest { it + ("formatVersion" to JsonPrimitive(0)) })
        }
        Then("a schema version without frozen DDL is unsupported") {
            rejects(UNSUPPORTED_VERSION, manifest { it + ("schemaVersion" to JsonPrimitive(0)) })
        }
        Then("unknown manifest fields are ignored when the versions match") {
            accepts(manifest { it + ("future" to JsonPrimitive(1)) })
        }
    }

    Given("data.json that does not match the manifest or the schema (criterion 7.8.3)") {
        Then("one extra byte fails the checksum") {
            rejects(CHECKSUM_MISMATCH) {
                it.tampered { this[BackupFormat.DATA_ENTRY] = getValue(BackupFormat.DATA_ENTRY) + " ".toByteArray() }
            }
        }
        Then("data that is not JSON is invalid") {
            rejects(INVALID_DATA) { it.tampered { replaceData("{".toByteArray(), recount = false) } }
        }
        Then("data that is not an object is invalid") {
            rejects(INVALID_DATA) { it.tampered { replaceData("[]".toByteArray(), recount = false) } }
        }
        Then(
            "an unknown table is invalid"
        ) { rejects(INVALID_DATA, data { it + ("unknown_table" to it.getValue("profile")) }) }
        Then("a missing table is invalid") { rejects(INVALID_DATA, data { JsonObject(it - "calendar_event") }) }
        Then("counts that do not list every table are invalid") {
            rejects(
                INVALID_DATA,
                manifest { m -> m + ("counts" to JsonObject(m.getValue("counts").jsonObject - "profile")) }
            )
        }
        Then("a count that does not match the rows is invalid") {
            rejects(
                INVALID_DATA,
                manifest { m -> m + ("counts" to (m.getValue("counts").jsonObject + ("profile" to JsonPrimitive(2)))) }
            )
        }
        Then("a table that is not an array is invalid") {
            rejects(INVALID_DATA, data { it + ("profile" to JsonObject(emptyMap())) })
        }
        Then("a row that is not an object is invalid") {
            rejects(INVALID_DATA, data { it + ("profile" to JsonArray(listOf(JsonPrimitive(1)))) })
        }
        Then(
            "a row with an extra column is invalid"
        ) { rejects(INVALID_DATA, row("profile", "surprise", JsonPrimitive(1))) }
        Then("a number in quotes is invalid") { rejects(INVALID_DATA, row("profile", "show_bmi", JsonPrimitive("1"))) }
        Then("a row that breaks a CHECK constraint is invalid") {
            rejects(INVALID_DATA, row("mood_log", "mood", JsonPrimitive(9)))
        }
        Then("a reference to a row that does not exist is invalid") {
            rejects(
                INVALID_DATA,
                row("dose_log", "medication_id", JsonPrimitive("7b0e6f0a-1d2c-4e5f-8a9b-0c1d2e3f4a5b"))
            )
        }
    }

    Given("rows the app could not read (ADR 0009: the same mappers as the app)") {
        val unreadable = listOf(
            Triple("profile", "unit_system", JsonPrimitive("CUBITS")),
            Triple("medication", "default_route", JsonPrimitive("TELEPATHY")),
            Triple("regimen", "route", JsonPrimitive("TELEPATHY")),
            Triple("dose_log", "route", JsonPrimitive("TELEPATHY")),
            Triple("dose_log", "taken_at_offset_seconds", JsonPrimitive(18 * 3600 + 1)),
            Triple("body_change_type", "category", JsonPrimitive("NOPE")),
            Triple("body_change_entry", "measurement_unit", JsonPrimitive("NOPE")),
            Triple("media_attachment", "owner_type", JsonPrimitive("NOPE")),
            Triple("media_attachment", "relative_path", JsonPrimitive("../escape.jpg")),
            Triple("measurement", "unit", JsonPrimitive("NOPE")),
            Triple("exercise_session", "intensity", JsonPrimitive("NOPE")),
            Triple("health_condition", "status", JsonPrimitive("NOPE")),
            Triple("lab_result", "id", JsonPrimitive("not-a-uuid")),
            Triple("mood_log", "tags", JsonPrimitive("{")),
            Triple("calendar_event", "category", JsonPrimitive("NOPE")),
            Triple("calendar_event", "recurrence_rule", JsonPrimitive("FREQ=NOPE")),
        )
        for ((table, column, value) in unreadable) {
            Then("$table.$column = $value is refused") { rejects(INVALID_DATA, row(table, column, value)) }
        }
        Then("lab_analyte with an id that is not a UUID is refused") {
            rejects(INVALID_DATA) { entries ->
                val referenced = entries.data().getValue(
                    "lab_result"
                ).jsonArray.map { it.jsonObject.string("analyte_id") }.toSet()
                row("lab_analyte", "id", JsonPrimitive("not-a-uuid")) { it.string("id") !in referenced }(entries)
            }
        }
    }

    Given("media that does not match the archive (criterion 7.8.3)") {
        Then("a declared file missing from the zip is missing media") {
            rejects(MISSING_MEDIA) { it.tampered { remove(firstMediaEntry()) } }
        }
        Then("a file with other bytes fails the checksum") {
            rejects(CHECKSUM_MISMATCH) { it.tampered { this[firstMediaEntry()] = byteArrayOf(1, 2, 3) } }
        }
        Then("a file with another size fails the checksum") {
            rejects(
                CHECKSUM_MISMATCH,
                manifest { m -> m.editMedia { list -> listOf(list.first().withSize(1)) + list.drop(1) } }
            )
        }
        Then("a file declared twice is invalid") {
            rejects(INVALID_DATA, manifest { m -> m.editMedia { list -> list + list.first() } })
        }
        Then("a declared path outside the media root is invalid") {
            val escape = JsonObject(
                mapOf("path" to JsonPrimitive("../x.jpg"), "sha256" to JsonPrimitive("00"), "size" to JsonPrimitive(1))
            )
            rejects(INVALID_DATA, manifest { m -> m.editMedia { list -> list + escape } })
        }
        Then("a live attachment whose file is neither in the zip nor declared missing is missing media") {
            rejects(MISSING_MEDIA) { entries ->
                val path = entries.liveMediaPath()
                entries.tampered {
                    remove(BackupFormat.MEDIA_PREFIX + path)
                    editManifest { m -> m.editMedia { list -> list.filter { it.jsonObject.string("path") != path } } }
                }
            }
        }
        Then("an attachment whose checksum differs from its file fails the checksum") {
            rejects(CHECKSUM_MISMATCH) { entries ->
                val path = entries.liveMediaPath()
                row("media_attachment", "checksum_sha256", JsonPrimitive("0".repeat(64))) {
                    it.string("relative_path") == path
                }(entries)
            }
        }
    }

    Given("size limits for hostile files") {
        Then("the manifest and data.json may be exactly at the limit, and not one byte over") {
            runTest {
                val entries = sourceEntries()
                val file = archiveOf(zipOf(entries))
                val manifestSize = entries.getValue(BackupFormat.MANIFEST_ENTRY).size.toLong()
                val dataSize = entries.getValue(BackupFormat.DATA_ENTRY).size.toLong()
                fun prepare(limits: BackupLimits): BackupRejection? =
                    runCatching {
                        BackupValidator(jdbcScratch(), limits).prepare(file).close()
                    }.exceptionOrNull()?.let { (it as BackupAbort).reason }
                prepare(BackupLimits(manifestSize, dataSize)) shouldBe null
                prepare(BackupLimits(manifestSize - 1, dataSize)) shouldBe NOT_A_BACKUP
                prepare(BackupLimits(manifestSize, dataSize - 1)) shouldBe INVALID_DATA
            }
        }
    }

    Given("any file, accepted or refused") {
        Then("the temporary database is closed afterwards") {
            runTest {
                val entries = sourceEntries()
                val scratch = TrackingScratch()
                val target = device(scratch = scratch)
                target.rejection(manifest { it + ("format" to JsonPrimitive("x")) }(entries)) shouldBe NOT_A_BACKUP
                target.rejection(zipOf(entries)) shouldBe null
                scratch.created shouldHaveSize 2
                scratch.created.map { it.isClosed() } shouldBe listOf(true, true)
            }
        }
    }
})

private fun JsonObject.string(key: String): String? = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun JsonObject.editMedia(edit: (List<JsonElement>) -> List<JsonElement>): JsonObject =
    this + ("media" to JsonArray(edit(getValue("media").jsonArray)))

private fun JsonElement.withSize(delta: Long): JsonObject =
    jsonObject + ("size" to JsonPrimitive(jsonObject.getValue("size").jsonPrimitive.long + delta))

private fun Entries.firstMediaEntry(): String = keys.first { it.startsWith(BackupFormat.MEDIA_PREFIX) }

/** Caminho de um anexo vivo cujo arquivo está no zip. */
private fun Entries.liveMediaPath(): String {
    val declared = manifest().getValue("media").jsonArray.mapNotNull { it.jsonObject.string("path") }.toSet()
    return data().getValue("media_attachment").jsonArray.map { it.jsonObject }
        .first { it["deleted_at"] is kotlinx.serialization.json.JsonNull && it.string("relative_path") in declared }
        .string("relative_path")!!
}
