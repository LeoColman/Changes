// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core

import io.kotest.core.config.AbstractProjectConfig
import io.kotest.property.PropertyTesting

/**
 * Configuração de projeto do Kotest. A seed fixa torna os property tests reprodutíveis no CI
 * (sobrescreva com -Dchanges.seed=N para explorar localmente).
 */
class KotestProjectConfig : AbstractProjectConfig() {
    init {
        PropertyTesting.defaultSeed = System.getProperty("changes.seed")?.toLongOrNull() ?: FIXED_SEED
    }

    private companion object {
        const val FIXED_SEED = 20_260_912L
    }
}
