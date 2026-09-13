// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.backup

import br.com.colman.changes.core.data.backup.ImportPlan

/**
 * Guarda o plano de import entre as telas Backup e ImportPreview (Seção 7.8, ADR 0009): o plano
 * carrega um `File` temporário e contagens calculadas, e não cabe num argumento de navegação
 * serializável. Uma instância por Koin (ver `backupModule`); é limpa depois de aplicar ou cancelar
 * para não segurar a cópia temporária do arquivo além do necessário.
 */
class ImportPlanHolder {
    var plan: ImportPlan? = null
        private set

    fun set(plan: ImportPlan) {
        this.plan = plan
    }

    fun clear() {
        plan = null
    }
}
