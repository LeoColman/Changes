// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data.backup

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement

internal enum class ColumnType {
    INTEGER,
    REAL,
    TEXT,
    ;

    companion object {
        fun of(declared: String): ColumnType = when (declared.trim().uppercase()) {
            "INTEGER" -> INTEGER
            "REAL" -> REAL
            else -> TEXT
        }
    }
}

internal data class Column(val name: String, val type: ColumnType)

/** Uma linha como está no banco: nome da coluna -> Long, Double, String ou null, na ordem das colunas. */
internal typealias Row = Map<String, Any?>

/** `android_metadata` é criada pelo Android em todo banco; não é do app e não entra no backup. */
private const val TABLES_SQL = "SELECT name FROM sqlite_master " +
    "WHERE type = 'table' AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata'"
private const val NAME = 1
private const val TYPE = 2

/**
 * Acesso genérico às tabelas pelo `SqlDriver`, com colunas e tipos lidos do próprio schema
 * (`PRAGMA table_info`). Nomes de tabela e coluna vêm sempre do schema, nunca do arquivo importado:
 * o importador só aceita chaves que existem aqui, então nada de identificador externo chega ao SQL.
 */
internal class RawDatabase(private val driver: SqlDriver) {

    fun tables(): Set<String> = driver.selectAll(TABLES_SQL) { it.getString(0)!! }.toSet()

    fun columns(table: String): List<Column> = driver.selectAll("PRAGMA table_info(${quote(table)})") { cursor ->
        Column(name = cursor.getString(NAME)!!, type = ColumnType.of(cursor.getString(TYPE).orEmpty()))
    }

    /** Percorre as linhas por cursor, uma de cada vez, em ordem de `id`. Memória constante. */
    fun forEachRow(table: String, columns: List<Column>, block: (Row) -> Unit) {
        driver.executeQuery(
            identifier = null,
            sql = "SELECT ${names(columns)} FROM ${quote(table)} ORDER BY ${quote(ID)}",
            mapper = { cursor ->
                while (cursor.next().value) block(cursor.readRow(columns))
                QueryResult.Unit
            },
            parameters = 0,
        )
    }

    fun rowWhere(table: String, columns: List<Column>, column: String, value: Any): Row? = driver.executeQuery(
        identifier = null,
        sql = "SELECT ${names(columns)} FROM ${quote(table)} WHERE ${quote(column)} = ? LIMIT 1",
        mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.readRow(columns) else null) },
        parameters = 1,
    ) { bindValue(0, value) }.value

    fun insert(table: String, columns: List<Column>, row: Row) {
        val placeholders = columns.joinToString(", ") { "?" }
        driver.execute(null, "INSERT INTO ${quote(table)} (${names(columns)}) VALUES ($placeholders)", columns.size) {
            columns.forEachIndexed { index, column -> bindValue(index, row[column.name]) }
        }
    }

    fun update(table: String, columns: List<Column>, row: Row) {
        val assignments = columns.joinToString(", ") { "${quote(it.name)} = ?" }
        driver.execute(null, "UPDATE ${quote(table)} SET $assignments WHERE ${quote(ID)} = ?", columns.size + 1) {
            columns.forEachIndexed { index, column -> bindValue(index, row[column.name]) }
            bindValue(columns.size, row.getValue(ID))
        }
    }

    fun deleteWhere(table: String, column: String, value: Any) {
        driver.execute(null, "DELETE FROM ${quote(table)} WHERE ${quote(column)} = ?", 1) { bindValue(0, value) }
    }

    fun deleteAll(table: String) {
        driver.execute(null, "DELETE FROM ${quote(table)}", 0)
    }

    fun count(table: String): Int = driver.selectAll("SELECT COUNT(*) FROM ${quote(table)}") { it.getLong(0)!! }
        .single()
        .toInt()

    /** Quantidade de violações de FK no banco inteiro (`PRAGMA foreign_key_check`). */
    fun foreignKeyViolations(): Int = driver.selectAll("PRAGMA foreign_key_check") { 1 }.size

    companion object {
        const val ID: String = "id"

        fun quote(identifier: String): String = "\"" + identifier.replace("\"", "\"\"") + "\""
    }
}

private fun <T : Any> SqlDriver.selectAll(sql: String, map: (SqlCursor) -> T): List<T> = executeQuery(
    identifier = null,
    sql = sql,
    mapper = { cursor ->
        val result = mutableListOf<T>()
        while (cursor.next().value) result += map(cursor)
        QueryResult.Value(result.toList())
    },
    parameters = 0,
).value

private fun SqlCursor.readRow(columns: List<Column>): Row {
    val row = LinkedHashMap<String, Any?>(columns.size)
    columns.forEachIndexed { index, column ->
        row[column.name] = when (column.type) {
            ColumnType.INTEGER -> getLong(index)
            ColumnType.REAL -> getDouble(index)
            ColumnType.TEXT -> getString(index)
        }
    }
    return row
}

private fun SqlPreparedStatement.bindValue(index: Int, value: Any?) {
    if (value is Long) {
        bindLong(index, value)
    } else if (value is Double) {
        bindDouble(index, value)
    } else if (value is String) {
        bindString(index, value)
    } else {
        check(value == null) { "Unsupported value type" }
        bindString(index, null)
    }
}

private fun names(columns: List<Column>): String = columns.joinToString(", ") { RawDatabase.quote(it.name) }
