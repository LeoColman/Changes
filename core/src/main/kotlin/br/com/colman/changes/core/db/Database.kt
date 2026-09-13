// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.db

import app.cash.sqldelight.db.SqlDriver
import br.com.colman.changes.core.db.sql.ChangesDatabase

/** Versão atual do schema. Sobe junto com cada `.sqm` (ADR 0006). */
public val CURRENT_SCHEMA_VERSION: Long get() = ChangesDatabase.Schema.version

/**
 * Único ponto de construção do banco. O driver vem de `app.platform` (Android) ou dos testFixtures
 * (JDBC). Quem cria o driver liga `PRAGMA foreign_keys`.
 */
public fun createDatabase(driver: SqlDriver): ChangesDatabase = ChangesDatabase(driver)
