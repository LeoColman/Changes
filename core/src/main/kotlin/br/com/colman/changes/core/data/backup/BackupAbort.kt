// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data.backup

import br.com.colman.changes.core.model.BackupRejection
import java.sql.SQLException

/** Rejeição com motivo. Só circula dentro do pacote; a fronteira pública devolve `Result`. */
internal class BackupAbort(val reason: BackupRejection, val detail: String?) : Exception(detail)

internal fun abort(reason: BackupRejection, detail: String? = null): Nothing = throw BackupAbort(reason, detail)

/**
 * Erros do driver (constraint, tipo) e dos mapeadores viram rejeição da tabela, nunca crash. O driver
 * JDBC lança `SQLException`; o Android, `SQLiteException` (runtime); os mapeadores, `IllegalArgumentException`
 * e `IllegalStateException`.
 */
@Suppress("TooGenericExceptionCaught") // fronteira com dado não confiável: cada camada lança um tipo diferente
internal fun guarded(table: String, block: () -> Unit) {
    try {
        block()
    } catch (error: RuntimeException) {
        abort(BackupRejection.INVALID_DATA, "$table: ${error.message}")
    } catch (error: SQLException) {
        abort(BackupRejection.INVALID_DATA, "$table: ${error.message}")
    }
}
