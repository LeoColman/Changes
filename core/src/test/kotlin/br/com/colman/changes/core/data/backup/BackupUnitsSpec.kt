// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data.backup

import br.com.colman.changes.core.model.BackupRejection
import br.com.colman.changes.core.testing.inMemoryDriver
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotContainDuplicates
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.sql.SQLException
import kotlin.random.Random

class BackupUnitsSpec : FunSpec({
    val columns = listOf(Column("id", ColumnType.TEXT), Column("n", ColumnType.INTEGER), Column("x", ColumnType.REAL))

    fun decode(json: String) = RowJson.decode(columns, Json.parseToJsonElement(json).jsonObject)

    context("RowJson.decode is strict") {
        test("accepts exactly the table's columns, with matching types or null") {
            decode("""{"id":"a","n":1,"x":1.5}""") shouldBe mapOf("id" to "a", "n" to 1L, "x" to 1.5)
            decode("""{"x":null,"n":null,"id":null}""") shouldBe mapOf("id" to null, "n" to null, "x" to null)
        }
        test("refuses a missing or an extra column") {
            decode("""{"id":"a","n":1}""").shouldBeNull()
            decode("""{"id":"a","n":1,"x":1.0,"y":2}""").shouldBeNull()
        }
        test("refuses numbers in quotes and text without quotes") {
            decode("""{"id":"a","n":"1","x":1.0}""").shouldBeNull()
            decode("""{"id":"a","n":1,"x":"1.0"}""").shouldBeNull()
            decode("""{"id":1,"n":1,"x":1.0}""").shouldBeNull()
        }
        test("refuses an integer with a fraction, a non-finite real and nested values") {
            decode("""{"id":"a","n":1.5,"x":1.0}""").shouldBeNull()
            decode("""{"id":"a","n":1,"x":1e400}""").shouldBeNull()
            decode("""{"id":{},"n":1,"x":1.0}""").shouldBeNull()
            decode("""{"id":"a","n":[],"x":1.0}""").shouldBeNull()
        }
    }

    context("RowJson.encode") {
        test("writes each database type as its JSON counterpart") {
            RowJson.encode(columns, mapOf("id" to "a", "n" to 2L, "x" to null)) shouldBe
                JsonObject(mapOf("id" to JsonPrimitive("a"), "n" to JsonPrimitive(2L), "x" to JsonNull))
            RowJson.encode(columns, mapOf("id" to null, "n" to null, "x" to 0.5)).getValue("x") shouldBe JsonPrimitive(0.5)
        }
        test("refuses a type the database cannot hold") {
            shouldThrow<IllegalStateException> { RowJson.encode(columns, mapOf("id" to 1, "n" to 1L, "x" to 1.0)) }
        }
    }

    context("RowOrder is a total order (ADR 0009)") {
        val ordered = listOf(
            Column("id", ColumnType.TEXT),
            Column("updated_at", ColumnType.INTEGER),
            Column("v", ColumnType.TEXT)
        )
        fun row(updatedAt: Long, value: Any?) = mapOf("id" to "a", "updated_at" to updatedAt, "v" to value)

        test("the newer updated_at wins whatever the content") {
            RowOrder.compare(ordered, row(2, null), row(1, "z")) shouldBeGreaterThan 0
            RowOrder.compare(ordered, row(1, "z"), row(2, null)) shouldBeLessThan 0
        }
        test("a tie is broken by value: null < integer < real < text, then natural order within a type") {
            val ascending = listOf(null, 1L, 2L, 0.5, 1.5, "a", "b")
            for (i in ascending.indices) {
                for (j in ascending.indices) {
                    val expected = i.compareTo(j)
                    Integer.signum(RowOrder.compare(ordered, row(1, ascending[i]), row(1, ascending[j]))) shouldBe expected
                }
            }
        }
        test("earlier columns decide before later ones") {
            val a = mapOf("id" to "a", "updated_at" to 1L, "v" to "z")
            val b = mapOf("id" to "b", "updated_at" to 1L, "v" to "a")
            RowOrder.compare(ordered, a, b) shouldBeLessThan 0
        }
    }

    test("ColumnType reads SQLite declared types, anything else is text") {
        ColumnType.of(" integer ") shouldBe ColumnType.INTEGER
        ColumnType.of("REAL") shouldBe ColumnType.REAL
        ColumnType.of("TEXT") shouldBe ColumnType.TEXT
        ColumnType.of("") shouldBe ColumnType.TEXT
        ColumnType.of("NUMERIC") shouldBe ColumnType.TEXT
    }

    context("streams") {
        test("toHex writes lowercase pairs, high nibble first") {
            byteArrayOf(0x00, 0x0F, 0xF0.toByte(), 0xFF.toByte(), 0x5A).toHex() shouldBe "000ff0ff5a"
        }
        test("copyHashing copies every byte across buffers, hashes them and closes the input") {
            val bytes = Random(1).nextBytes(200_000)
            val input = ClosingProbe(bytes)
            val out = ByteArrayOutputStream()
            val digest = sha256()
            copyHashing(input, out, digest) shouldBe bytes.size.toLong()
            out.toByteArray() shouldBe bytes
            digest.digest().toHex() shouldBe sha(bytes)
            input.closed shouldBe true
        }
        test("copyHashing without an output only counts and hashes") {
            val digest = sha256()
            copyHashing(ByteArrayInputStream(ByteArray(0)), null, digest) shouldBe 0L
            digest.digest().toHex() shouldBe sha(ByteArray(0))
        }
        test("readAtMost returns content up to the limit, null beyond it, and always closes") {
            val exact = ClosingProbe(byteArrayOf(1, 2, 3))
            exact.readAtMost(3) shouldBe byteArrayOf(1, 2, 3)
            exact.closed shouldBe true
            val over = ClosingProbe(byteArrayOf(1, 2, 3))
            over.readAtMost(2).shouldBeNull()
            over.closed shouldBe true
        }
    }

    context("RawDatabase") {
        test("quotes identifiers, doubling embedded quotes") {
            RawDatabase.quote("a\"b") shouldBe "\"a\"\"b\""
        }
        test("refuses to bind a value type the database cannot hold") {
            val raw = RawDatabase(inMemoryDriver())
            val cols = raw.columns("exercise_session")
            shouldThrow<IllegalStateException> { raw.insert("exercise_session", cols, cols.associate { it.name to 1 }) }
        }
        test("every table of the schema is either exported or internal, never both (ADR 0009)") {
            RawDatabase(inMemoryDriver()).tables() shouldBe BackupFormat.EXPORTED.toSet() + BackupFormat.INTERNAL
            BackupFormat.EXPORTED.filter { it in BackupFormat.INTERNAL }.shouldBeEmpty()
            BackupFormat.EXPORTED.shouldNotContainDuplicates()
        }
    }

    context("guarded") {
        test("runtime and driver errors become INVALID_DATA naming the table") {
            val runtime = shouldThrow<BackupAbort> { guarded("t") { error("boom") } }
            runtime.reason shouldBe BackupRejection.INVALID_DATA
            runtime.detail shouldBe "t: boom"
            shouldThrow<BackupAbort> { guarded("t") { throw SQLException("constraint") } }.detail shouldBe "t: constraint"
        }
        test("a rejection with its own reason passes through unchanged") {
            shouldThrow<BackupAbort> { guarded("t") { abort(BackupRejection.CHECKSUM_MISMATCH, "x") } }.reason shouldBe
                BackupRejection.CHECKSUM_MISMATCH
        }
        test("a block that succeeds is left alone") {
            var ran = false
            guarded("t") { ran = true }
            ran shouldBe true
        }
    }
})

private class ClosingProbe(bytes: ByteArray) : ByteArrayInputStream(bytes) {
    var closed = false
        private set

    override fun close() {
        closed = true
        super.close()
    }
}
