// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.testing

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import br.com.colman.changes.core.clinical.ClinicalDataset
import br.com.colman.changes.core.clinical.ClinicalLabels
import br.com.colman.changes.core.db.Seeder
import br.com.colman.changes.core.db.createDatabase
import br.com.colman.changes.core.db.sql.ChangesDatabase
import io.kotest.core.listeners.TestListener
import io.kotest.core.test.TestCase
import java.util.Properties

/**
 * Dataset e rótulos lidos de novo a cada acesso. Sem cache de propósito: o Pitest reaproveita a JVM
 * entre mutantes, e um valor guardado esconderia mutações no código de leitura.
 */
public val testDataset: ClinicalDataset get() = ClinicalDataset.load()
public val testLabels: ClinicalLabels get() = ClinicalLabels.load()

/** Driver JDBC em memória com FKs ligadas e schema atual criado. */
public fun inMemoryDriver(): SqlDriver {
    val driver = JdbcSqliteDriver(
        JdbcSqliteDriver.IN_MEMORY,
        Properties().apply { setProperty("foreign_keys", "true") }
    )
    ChangesDatabase.Schema.create(driver)
    return driver
}

/** Banco em memória, já seedado (a menos que [seed] seja `false`). */
public fun testDatabase(clock: FixedClock = FixedClock(), seed: Boolean = true): ChangesDatabase {
    val database = createDatabase(inMemoryDriver())
    if (seed) Seeder(database, testDataset, testLabels, clock).seed()
    return database
}

/** Um banco novo por teste (Seção 11.1). Use `listener(DatabaseTestListener())` e leia [database]. */
public class DatabaseTestListener(public val clock: FixedClock = FixedClock()) : TestListener {
    public lateinit var database: ChangesDatabase
        private set

    override suspend fun beforeTest(testCase: TestCase) {
        database = testDatabase(clock)
    }
}
