// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data.backup

import br.com.colman.changes.core.model.isFiniteNumber
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/**
 * Linha <-> objeto JSON de `data.json`. A leitura é estrita: o objeto tem exatamente as colunas da
 * tabela, e cada valor tem o tipo da coluna (inteiro sem parte decimal, real finito, texto entre aspas).
 * Qualquer desvio devolve `null` e o import é rejeitado.
 */
internal object RowJson {
    fun encode(columns: List<Column>, row: Row): JsonObject =
        JsonObject(columns.associate { it.name to toJson(row[it.name]) })

    fun decode(columns: List<Column>, json: JsonObject): Row? {
        if (json.keys != columns.mapTo(HashSet()) { it.name }) return null
        val row = LinkedHashMap<String, Any?>(columns.size)
        for (column in columns) {
            val cell = parse(column.type, json.getValue(column.name)) ?: return null
            row[column.name] = cell.value
        }
        return row
    }

    private fun toJson(value: Any?): JsonElement = if (value is Long) {
        JsonPrimitive(value)
    } else if (value is Double) {
        JsonPrimitive(value)
    } else if (value is String) {
        JsonPrimitive(value)
    } else {
        check(value == null) { "Unsupported value type" }
        JsonNull
    }

    /** Envelope para distinguir "valor nulo válido" de "valor inválido" (que é `null`). */
    private class Cell(val value: Any?)

    /** Texto só entre aspas, número só sem aspas: `"1"` numa coluna INTEGER é rejeitado. */
    private fun parse(type: ColumnType, element: JsonElement): Cell? {
        val primitive = element as? JsonPrimitive
        return if (element is JsonNull) {
            Cell(null)
        } else if (primitive == null || primitive.isString != (type == ColumnType.TEXT)) {
            null
        } else {
            when (type) {
                ColumnType.INTEGER -> primitive.longOrNull?.let(::Cell)
                ColumnType.REAL -> primitive.doubleOrNull?.takeIf(::isFiniteNumber)?.let(::Cell)
                ColumnType.TEXT -> Cell(primitive.content)
            }
        }
    }
}

/**
 * Ordem total entre duas versões da mesma linha (ADR 0009): maior `updated_at` vence; empate, a maior
 * pela comparação canônica coluna a coluna (null < inteiro < real < texto). Por ser uma ordem total, o
 * merge vira "máximo por id": comutativo, associativo e idempotente.
 */
internal object RowOrder {
    fun compare(columns: List<Column>, a: Row, b: Row): Int {
        val byUpdate = (a.getValue(UPDATED_AT) as Long).compareTo(b.getValue(UPDATED_AT) as Long)
        if (byUpdate != 0) return byUpdate
        for (column in columns) {
            val byCell = compareCells(a[column.name], b[column.name])
            if (byCell != 0) return byCell
        }
        return 0
    }

    private fun rank(value: Any?): Int = if (value == null) {
        0
    } else if (value is Long) {
        1
    } else if (value is Double) {
        2
    } else {
        RANK_TEXT
    }

    private fun compareCells(x: Any?, y: Any?): Int {
        val byRank = rank(x).compareTo(rank(y))
        return if (byRank != 0 || x == null) {
            byRank
        } else if (x is Long) {
            x.compareTo(y as Long)
        } else if (x is Double) {
            x.compareTo(y as Double)
        } else {
            (x as String).compareTo(y as String)
        }
    }

    private const val UPDATED_AT = "updated_at"
    private const val RANK_TEXT = 3
}
