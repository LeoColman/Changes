// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.platform

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import br.com.colman.changes.core.db.sql.ChangesDatabase

/**
 * Driver do banco no armazenamento interno privado. FKs ligadas por conexão.
 *
 * Seção 3.1: trocar para SQLCipher é trocar a factory do `SupportSQLiteOpenHelper` aqui, sem
 * mexer em feature nenhuma.
 */
fun androidDriver(context: Context): SqlDriver = AndroidSqliteDriver(
    schema = ChangesDatabase.Schema,
    context = context,
    name = DATABASE_NAME,
    callback = object : AndroidSqliteDriver.Callback(ChangesDatabase.Schema) {
        override fun onConfigure(db: SupportSQLiteDatabase) {
            super.onConfigure(db)
            db.setForeignKeyConstraintsEnabled(true)
        }
    },
)

const val DATABASE_NAME = "changes.db"
