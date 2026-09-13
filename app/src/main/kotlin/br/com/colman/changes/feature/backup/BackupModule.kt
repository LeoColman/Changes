// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.backup

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Módulo da feature backup (Seção 7.8, ADR 0009). Somado à lista de módulos em
 * `di/AppModules.kt`. [ImportPlanHolder] é o holder que carrega o plano de import entre as telas
 * Backup e ImportPreview.
 */
val backupModule: Module = module {
    single { ImportPlanHolder() }
    viewModelOf(::BackupViewModel)
    viewModelOf(::ImportPreviewViewModel)
}
