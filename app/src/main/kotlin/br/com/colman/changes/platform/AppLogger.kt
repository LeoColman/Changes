// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.platform

import android.util.Log
import br.com.colman.changes.BuildConfig

/**
 * Único ponto de log do app (Seção 3.1). No-op em release. Nunca registre dados da pessoa
 * (valores, notas, nomes de medicação): logue apenas eventos técnicos.
 */
object AppLogger {
    fun debug(tag: String, message: () -> String) {
        if (BuildConfig.DEBUG) Log.d(tag, message())
    }

    fun warn(tag: String, throwable: Throwable? = null, message: () -> String) {
        if (BuildConfig.DEBUG) Log.w(tag, message(), throwable)
    }
}
