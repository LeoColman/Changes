// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import app.cash.sqldelight.db.QueryResult
import br.com.colman.changes.core.data.backup.BackupFormat
import br.com.colman.changes.core.data.backup.RawDatabase
import br.com.colman.changes.core.data.backup.Row
import br.com.colman.changes.core.data.backup.applyTo
import br.com.colman.changes.core.data.backup.completeState
import br.com.colman.changes.core.data.backup.device
import br.com.colman.changes.core.data.backup.hasFile
import br.com.colman.changes.core.db.sql.Media_attachment
import br.com.colman.changes.core.model.MediaPaths
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Seção 9 e ADR 0008: lixeira de 30 dias, restauração e purga com as fotos. */
class TrashRepositorySpec : FunSpec({
    test("the trash lists what was deleted in the last 30 days, newest first, with the person's own text") {
        runTest {
            val env = TrashEnvironment(this).apply { populate() }
            val now = env.clock.now()
            fun at(kind: TrashKind) = (now - 1.days - kind.ordinal.minutes).toEpochMilliseconds()
            val ids = env.trashOnePerKind(::at)

            val items = env.repository.observe().first()

            items.map { it.kind } shouldBe TrashKind.entries
            items.map { it.id.toString() } shouldBe TrashKind.entries.map { ids.getValue(it) }
            items.forEach { it.deletedAt shouldBe Instant.fromEpochMilliseconds(at(it.kind)) }
            items.forEach { it.purgeAt shouldBe it.deletedAt + 30.days }
            items.single { it.kind == TrashKind.MEDICATION }.label shouldBe env.row(TrashKind.MEDICATION, ids)["name"]
            items.single { it.kind == TrashKind.CALENDAR_EVENT }.label shouldBe env.row(TrashKind.CALENDAR_EVENT, ids)["title"]
            items.single { it.kind == TrashKind.EXERCISE }.label shouldBe env.row(TrashKind.EXERCISE, ids)["activity"]
        }
    }

    test("exactly 30 days ago is still in the trash, one millisecond more is not, and a purged row never is") {
        runTest {
            val env = TrashEnvironment(this).apply { populate() }
            val edge = (env.clock.now() - 30.days).toEpochMilliseconds()
            val kept = env.trashOne(TrashKind.MOOD, edge)
            env.trashOne(TrashKind.EXERCISE, edge - 1)
            env.trashOne(TrashKind.HEALTH_CONDITION, 0)

            env.repository.observe().first().map { it.id.toString() } shouldBe listOf(kept)
        }
    }

    test("restoring brings every kind back, and a body entry comes back only with the photos deleted with it") {
        runTest {
            val env = TrashEnvironment(this).apply { populate() }
            val at = (env.clock.now() - 1.days).toEpochMilliseconds()
            val ids = env.trashOnePerKind { at }
            val separate = env.addPhoto(env.photoEntry, deletedAt = at - 5_000)

            env.repository.observe().first().forEach { env.repository.restore(it) }

            env.repository.observe().first().shouldBeEmpty()
            ids.keys.forEach { kind -> env.row(kind, ids)["deleted_at"] shouldBe null }
            val photos = env.photosOf(env.photoEntry)
            photos.filter { it["id"] != separate }.map { it["deleted_at"] }.toSet() shouldBe setOf(null)
            photos.single { it["id"] == separate }["deleted_at"] shouldBe at - 5_000
        }
    }

    test("deleting permanently blanks the person's text, leaves a newer tombstone and removes the photos") {
        runTest {
            val env = TrashEnvironment(this).apply { populate() }
            val at = (env.clock.now() - 1.days).toEpochMilliseconds()
            val ids = env.trashOnePerKind { at }
            val files = env.filesOf(env.photoEntry).shouldNotBeEmpty()
            val otherFiles = env.device.media.listAll() - files.toSet()
            env.clock.advance(1.hours)

            val removed = env.repository.observe().first().sumOf { env.repository.purge(it) }

            val now = env.clock.now().toEpochMilliseconds()
            env.repository.observe().first().shouldBeEmpty()
            ids.keys.forEach { kind ->
                env.row(kind, ids)["deleted_at"] shouldBe 0L
                env.row(kind, ids)["updated_at"] shouldBe now
            }
            env.row(TrashKind.MEDICATION, ids)["name"] shouldBe ""
            env.row(TrashKind.CALENDAR_EVENT, ids)["title"] shouldBe ""
            env.row(TrashKind.CALENDAR_EVENT, ids)["description"] shouldBe null
            env.row(TrashKind.MOOD, ids)["note"] shouldBe null
            env.row(TrashKind.MOOD, ids)["tags"] shouldBe "[]"
            env.row(TrashKind.BODY_CHANGE_ENTRY, ids)["notes"] shouldBe null
            env.row(TrashKind.HEALTH_CONDITION, ids)["label"] shouldBe ""
            removed shouldBe files.size
            files.forEach { env.device.media.exists(it) shouldBe false }
            env.photosOf(env.photoEntry).forEach { photo ->
                photo["deleted_at"] shouldBe 0L
                photo["caption"] shouldBe null
            }
            otherFiles.forEach { env.device.media.exists(it) shouldBe true }
        }
    }

    test("an item restored in the meantime is not purged by a stale screen, and neither are its photos") {
        runTest {
            val env = TrashEnvironment(this).apply { populate() }
            val at = (env.clock.now() - 1.days).toEpochMilliseconds()
            val entry = env.trashOne(TrashKind.BODY_CHANGE_ENTRY, at)
            val separate = env.addPhoto(entry, deletedAt = at - 5_000)
            val item = env.repository.observe().first().single()
            val notes = env.row(TrashKind.BODY_CHANGE_ENTRY, mapOf(TrashKind.BODY_CHANGE_ENTRY to entry))["notes"]

            env.repository.restore(item)
            env.repository.purge(item) shouldBe 0

            val row = env.row(TrashKind.BODY_CHANGE_ENTRY, mapOf(TrashKind.BODY_CHANGE_ENTRY to entry))
            row["deleted_at"] shouldBe null
            row["notes"] shouldBe notes
            env.photosOf(entry).single { it["id"] == separate }["deleted_at"] shouldBe at - 5_000
        }
    }

    test("at start, what passed 30 days is purged with its photos, the rest stays, and running again changes nothing") {
        runTest {
            val env = TrashEnvironment(this).apply { populate() }
            val edge = (env.clock.now() - 30.days).toEpochMilliseconds()
            val old = env.trashOne(TrashKind.BODY_CHANGE_ENTRY, edge - 1)
            val kept = env.trashOne(TrashKind.MOOD, edge)
            val files = env.filesOf(old)
            val firstRun = env.clock.now().toEpochMilliseconds()

            env.repository.purgeExpired() shouldBe files.size

            val oldRow = mapOf(TrashKind.BODY_CHANGE_ENTRY to old)
            env.row(TrashKind.BODY_CHANGE_ENTRY, oldRow)["deleted_at"] shouldBe 0L
            env.row(TrashKind.MOOD, mapOf(TrashKind.MOOD to kept))["deleted_at"] shouldBe edge
            files.forEach { env.device.media.exists(it) shouldBe false }

            env.clock.advance(1.hours)
            env.repository.purgeExpired() shouldBe 0
            env.row(TrashKind.BODY_CHANGE_ENTRY, oldRow)["updated_at"] shouldBe firstRun
        }
    }

    test("emptying the trash purges everything in it, including what was deleted this instant, and nothing alive") {
        runTest {
            val env = TrashEnvironment(this).apply { populate() }
            val now = env.clock.now().toEpochMilliseconds()
            val ids = env.trashOnePerKind { now }
            val aliveBefore = env.aliveRows()

            env.repository.empty()

            env.repository.observe().first().shouldBeEmpty()
            ids.keys.forEach { kind -> env.row(kind, ids)["deleted_at"] shouldBe 0L }
            env.aliveRows() shouldBe aliveBefore
        }
    }
})

private val TABLES: Map<TrashKind, String> = mapOf(
    TrashKind.MEDICATION to "medication",
    TrashKind.REGIMEN to "regimen",
    TrashKind.DOSE_LOG to "dose_log",
    TrashKind.BODY_CHANGE_TYPE to "body_change_type",
    TrashKind.BODY_CHANGE_ENTRY to "body_change_entry",
    TrashKind.MEASUREMENT to "measurement",
    TrashKind.EXERCISE to "exercise_session",
    TrashKind.HEALTH_CONDITION to "health_condition",
    TrashKind.LAB_ANALYTE to "lab_analyte",
    TrashKind.LAB_RESULT to "lab_result",
    TrashKind.MOOD to "mood_log",
    TrashKind.CALENDAR_EVENT to "calendar_event",
)

/** Só itens da pessoa: catálogo builtin nunca vai para a lixeira. */
private val OWN_ROWS: Map<TrashKind, String> = mapOf(
    TrashKind.MEDICATION to "is_builtin = 0",
    TrashKind.BODY_CHANGE_TYPE to "is_builtin = 0",
    TrashKind.LAB_ANALYTE to "is_builtin = 0",
)

/** Um aparelho com uma linha de cada tabela, nenhuma excluída, e a lixeira sobre ele. */
private class TrashEnvironment(scope: TestScope) {
    val device = scope.device()
    val clock = device.clock
    val repository = TrashRepository(device.database, device.media, clock, StandardTestDispatcher(scope.testScheduler))
    private val state = completeState()

    /** Entrada do corpo com ao menos uma foto gravada em disco. */
    val photoEntry: String = state.attachments.first { it.hasFile() }.owner_id

    suspend fun populate() {
        state.applyTo(device)
        for (table in BackupFormat.EXPORTED) sql("UPDATE $table SET deleted_at = NULL")
    }

    fun trashOnePerKind(at: (TrashKind) -> Long): Map<TrashKind, String> =
        TrashKind.entries.associateWith { kind -> trashOne(kind, at(kind)) }

    /** Exclui uma linha do tipo no instante [at]; a entrada do corpo leva as fotos junto, como no app. */
    fun trashOne(kind: TrashKind, at: Long): String {
        val table = TABLES.getValue(kind)
        val id = if (kind == TrashKind.BODY_CHANGE_ENTRY) {
            photoEntry
        } else {
            firstId("SELECT id FROM $table WHERE ${OWN_ROWS[kind] ?: "1 = 1"} ORDER BY id LIMIT 1")
        }
        sql("UPDATE $table SET deleted_at = $at, updated_at = $at WHERE id = '$id'")
        if (kind == TrashKind.BODY_CHANGE_ENTRY) {
            sql("UPDATE media_attachment SET deleted_at = $at, updated_at = $at WHERE owner_id = '$id'")
        }
        return id
    }

    fun addPhoto(owner: String, deletedAt: Long): String {
        val id = Uuid.random()
        device.database.mediaQueries.insert(
            Media_attachment(
                id = id.toString(),
                owner_type = "BODY_CHANGE_ENTRY",
                owner_id = owner,
                relative_path = MediaPaths.forNew(id, "jpg"),
                mime_type = "image/jpeg",
                captured_at = null,
                captured_at_offset_seconds = null,
                checksum_sha256 = "0".repeat(64),
                caption = "legenda",
                created_at = deletedAt,
                updated_at = deletedAt,
                deleted_at = deletedAt,
            ),
        )
        return id.toString()
    }

    fun row(kind: TrashKind, ids: Map<TrashKind, String>): Row {
        val raw = RawDatabase(device.driver)
        val table = TABLES.getValue(kind)
        return raw.rowWhere(table, raw.columns(table), RawDatabase.ID, ids.getValue(kind))!!
    }

    fun photosOf(owner: String): List<Row> {
        val raw = RawDatabase(device.driver)
        val rows = mutableListOf<Row>()
        raw.forEachRow("media_attachment", raw.columns("media_attachment")) { if (it["owner_id"] == owner) rows += it }
        return rows
    }

    /** Arquivos em disco das fotos de [owner]. */
    fun filesOf(owner: String): List<String> =
        photosOf(owner).map { it.getValue("relative_path") as String }.filter { device.media.exists(it) }

    fun aliveRows(): Map<String, Int> = TABLES.values.associateWith { table ->
        firstId("SELECT CAST(COUNT(*) AS TEXT) FROM $table WHERE deleted_at IS NULL").toInt()
    }

    private fun sql(statement: String) {
        device.driver.execute(null, statement, 0)
    }

    private fun firstId(query: String): String = device.driver.executeQuery(
        identifier = null,
        sql = query,
        mapper = { cursor ->
            check(cursor.next().value) { "no row: $query" }
            QueryResult.Value(cursor.getString(0)!!)
        },
        parameters = 0,
    ).value
}
