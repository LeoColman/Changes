// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.platform

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import br.com.colman.changes.core.db.ScratchDriverFactory

/**
 * Banco temporário em memória (nome `null`), sem schema, com FKs ligadas: o importador cria nele o
 * schema da versão do backup e roda as migrações (ADR 0009). Nada toca o disco.
 */
class AndroidScratchDriverFactory(private val context: Context) : ScratchDriverFactory {
    override fun create(): SqlDriver = AndroidSqliteDriver(
        schema = EmptySchema,
        context = context,
        name = null,
        callback = object : AndroidSqliteDriver.Callback(EmptySchema) {
            override fun onConfigure(db: SupportSQLiteDatabase) {
                super.onConfigure(db)
                db.setForeignKeyConstraintsEnabled(true)
            }
        },
    )

    private object EmptySchema : SqlSchema<QueryResult.Value<Unit>> {
        override val version: Long = 1

        override fun create(driver: SqlDriver): QueryResult.Value<Unit> = QueryResult.Unit

        override fun migrate(
            driver: SqlDriver,
            oldVersion: Long,
            newVersion: Long,
            vararg callbacks: AfterVersion,
        ): QueryResult.Value<Unit> = QueryResult.Unit
    }
}
