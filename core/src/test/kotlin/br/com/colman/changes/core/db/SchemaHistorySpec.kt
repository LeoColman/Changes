// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import br.com.colman.changes.core.testing.inMemoryDriver
import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.io.File

/** ADR 0009: cada versão do schema tem o DDL congelado, e o da versão atual é exatamente o `Schema.create`. */
class SchemaHistorySpec : FunSpec({
    test("the snapshot of the current version is exactly what Schema.create produces") {
        val actual = SchemaHistory.ddlOf(inMemoryDriver())
        if (SchemaHistory.snapshot(CURRENT_SCHEMA_VERSION) != actual) {
            val out = File("build/schema/v$CURRENT_SCHEMA_VERSION.sql").apply { parentFile.mkdirs() }
            out.writeText(actual)
            fail(
                "The schema differs from src/main/resources/schema/v$CURRENT_SCHEMA_VERSION.sql. " +
                    "A released schema never changes: freeze it, add a migration and bump the version (ADR 0006). " +
                    "Actual DDL written to ${out.absolutePath}.",
            )
        }
    }

    test("a database created from the snapshot has exactly the snapshot's DDL") {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        SchemaHistory.create(driver, CURRENT_SCHEMA_VERSION)
        SchemaHistory.ddlOf(driver) shouldBe SchemaHistory.snapshot(CURRENT_SCHEMA_VERSION)
    }

    test("a version that was never frozen has no snapshot and cannot be created") {
        SchemaHistory.snapshot(0).shouldBeNull()
        shouldThrow<IllegalStateException> { SchemaHistory.create(JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY), 0) }
    }

    test("statements are split on semicolons without blanks, and whitespace is collapsed") {
        SchemaHistory.statements(" CREATE TABLE a (x);\n\n;CREATE INDEX i ON a(x) ;\n") shouldBe
            listOf("CREATE TABLE a (x)", "CREATE INDEX i ON a(x)")
        SchemaHistory.normalize("CREATE TABLE a (\n    x INTEGER,\n\ty TEXT\n)  ") shouldBe "CREATE TABLE a ( x INTEGER, y TEXT )"
    }
})
