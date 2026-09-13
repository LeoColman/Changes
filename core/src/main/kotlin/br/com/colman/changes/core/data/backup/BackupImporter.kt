// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data.backup

import app.cash.sqldelight.db.SqlDriver
import br.com.colman.changes.core.db.createDatabase
import br.com.colman.changes.core.db.sql.ChangesDatabase
import br.com.colman.changes.core.model.BackupRejection

internal enum class Decision { INSERT, UPDATE, REPLACE_RIVAL, UNCHANGED, KEEP }

internal class RowDecision(val decision: Decision, val rival: Row? = null)

/**
 * Aplica ao banco principal um backup já validado ([PreparedImport]), ou só conta o efeito para a
 * prévia (ADR 0009). Mesclar é "máximo por id" pela [RowOrder]; Substituir apaga e recarrega.
 */
internal class BackupImporter(private val driver: SqlDriver) {
    private val database: ChangesDatabase = createDatabase(driver)

    /** Contagens por tabela sem escrever nada. */
    fun count(prepared: PreparedImport, mode: ImportMode): List<TableCounts> {
        val main = RawDatabase(driver)
        val scratch = RawDatabase(prepared.scratch)
        return BackupFormat.EXPORTED.map { table ->
            if (mode == ImportMode.REPLACE) {
                TableCounts(table, inserted = scratch.count(table), removed = main.count(table))
            } else {
                val columns = main.columns(table)
                val tally = Tally(table)
                scratch.forEachRow(table, columns) { tally += decide(main, table, columns, it).decision }
                tally.counts()
            }
        }
    }

    /** Aplica numa única transação do banco principal. Qualquer falha desfaz tudo. */
    fun apply(prepared: PreparedImport, mode: ImportMode): List<TableCounts> {
        val main = RawDatabase(driver)
        val scratch = RawDatabase(prepared.scratch)
        val counts = database.transactionWithResult {
            driver.execute(null, "PRAGMA defer_foreign_keys = ON", 0)
            val result = if (mode == ImportMode.REPLACE) replace(main, scratch) else merge(main, scratch)
            if (main.foreignKeyViolations() > 0) abort(BackupRejection.INVALID_DATA, "references")
            result
        }
        // Escrita pelo driver cru não avisa as queries observadas; sem isso a UI mostraria dados velhos.
        for (table in BackupFormat.EXPORTED) driver.notifyListeners(table)
        return counts
    }

    private fun replace(main: RawDatabase, scratch: RawDatabase): List<TableCounts> {
        val removed = BackupFormat.EXPORTED.associateWith { main.count(it) }
        for (table in BackupFormat.EXPORTED.asReversed()) main.deleteAll(table)
        return BackupFormat.EXPORTED.map { table ->
            val columns = main.columns(table)
            var inserted = 0
            scratch.forEachRow(table, columns) { row ->
                guarded(table) { main.insert(table, columns, row) }
                inserted++
            }
            TableCounts(table, inserted = inserted, removed = removed.getValue(table))
        }
    }

    private fun merge(main: RawDatabase, scratch: RawDatabase): List<TableCounts> = BackupFormat.EXPORTED.map { table ->
        val columns = main.columns(table)
        val tally = Tally(table)
        scratch.forEachRow(table, columns) { incoming ->
            val decision = decide(main, table, columns, incoming)
            guarded(table) { write(main, table, columns, incoming, decision) }
            tally += decision.decision
        }
        tally.counts()
    }

    private fun write(main: RawDatabase, table: String, columns: List<Column>, incoming: Row, decision: RowDecision) {
        when (decision.decision) {
            Decision.INSERT -> main.insert(table, columns, incoming)
            Decision.UPDATE -> main.update(table, columns, incoming)
            Decision.REPLACE_RIVAL -> {
                main.deleteWhere(table, RawDatabase.ID, decision.rival!!.getValue(RawDatabase.ID)!!)
                main.insert(table, columns, incoming)
            }
            Decision.UNCHANGED, Decision.KEEP -> Unit
        }
    }

    /**
     * Máximo pela ordem total (ADR 0009): vence a versão maior; `mood_log` compara também com a linha que
     * ocupa a mesma data com outro id. Colisão de código de catálogo com outro id rejeita o import.
     */
    private fun decide(main: RawDatabase, table: String, columns: List<Column>, incoming: Row): RowDecision {
        val existing = main.rowWhere(table, columns, RawDatabase.ID, incoming.getValue(RawDatabase.ID)!!)
        val decision = if (existing == null) {
            decideAgainstRival(main, table, columns, incoming)
        } else if (existing == incoming) {
            RowDecision(Decision.UNCHANGED)
        } else if (RowOrder.compare(columns, incoming, existing) > 0) {
            RowDecision(Decision.UPDATE)
        } else {
            RowDecision(Decision.KEEP)
        }
        if (decision.decision == Decision.INSERT || decision.decision == Decision.UPDATE) {
            checkUniqueCode(main, table, columns, incoming)
        }
        return decision
    }

    private fun decideAgainstRival(
        main: RawDatabase,
        table: String,
        columns: List<Column>,
        incoming: Row,
    ): RowDecision {
        val key = BackupFormat.NATURAL_KEYS[table]
        val rival = key?.let { main.rowWhere(table, columns, it, incoming.getValue(it)!!) }
        return if (rival == null) {
            RowDecision(Decision.INSERT)
        } else if (RowOrder.compare(columns, incoming, rival) > 0) {
            RowDecision(Decision.REPLACE_RIVAL, rival)
        } else {
            RowDecision(Decision.KEEP)
        }
    }

    private fun checkUniqueCode(main: RawDatabase, table: String, columns: List<Column>, incoming: Row) {
        val column = BackupFormat.UNIQUE_CODES[table]
        val other = column?.let { main.rowWhere(table, columns, it, incoming.getValue(it)!!) }
        if (other != null && other[RawDatabase.ID] != incoming[RawDatabase.ID]) {
            abort(BackupRejection.INVALID_DATA, "$table $column")
        }
    }

    private class Tally(private val table: String) {
        private val counts = IntArray(Decision.entries.size)

        operator fun plusAssign(decision: Decision) {
            counts[decision.ordinal]++
        }

        fun counts() = TableCounts(
            table = table,
            inserted = counts[Decision.INSERT.ordinal],
            updated = counts[Decision.UPDATE.ordinal] + counts[Decision.REPLACE_RIVAL.ordinal],
            unchanged = counts[Decision.UNCHANGED.ordinal],
            kept = counts[Decision.KEEP.ordinal],
        )
    }
}
