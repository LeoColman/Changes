// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver

/** Driver vazio (sem schema) para bancos temporários: validação e migração de backups (ADR 0009). */
public fun interface ScratchDriverFactory {
    public fun create(): SqlDriver
}

private const val DDL_SQL = "SELECT sql FROM sqlite_master " +
    "WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata' ORDER BY type DESC, name"

/**
 * DDL congelado de cada versão do schema, em `src/main/resources/schema/v<N>.sql`. Um backup da
 * versão N é carregado num banco temporário criado com o DDL de N e levado à versão atual pelas mesmas
 * migrações do app. O snapshot da versão atual é verificado contra o `Schema.create` em teste: mudar o
 * schema sem subir a versão (e sem congelar o DDL anterior) quebra o build.
 */
public object SchemaHistory {
    public fun snapshot(version: Long): String? =
        SchemaHistory::class.java.getResource("/schema/v$version.sql")?.readText(Charsets.UTF_8)

    public fun create(driver: SqlDriver, version: Long) {
        val ddl = checkNotNull(snapshot(version)) { "No schema snapshot for version $version" }
        for (statement in statements(ddl)) driver.execute(null, statement, 0)
    }

    /** DDL do banco em [driver], normalizado e ordenado por tipo e nome, no mesmo formato dos snapshots. */
    public fun ddlOf(driver: SqlDriver): String = driver.executeQuery(
        identifier = null,
        sql = DDL_SQL,
        mapper = { cursor ->
            val statements = mutableListOf<String>()
            while (cursor.next().value) statements += normalize(cursor.getString(0)!!)
            QueryResult.Value(statements.joinToString(separator = ";\n", postfix = ";\n"))
        },
        parameters = 0,
    ).value

    internal fun statements(ddl: String): List<String> = ddl.split(';').map { it.trim() }.filter { it.isNotEmpty() }

    internal fun normalize(statement: String): String = statement.replace(Regex("\\s+"), " ").trim()
}
