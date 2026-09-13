// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.today

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Módulo da feature Today (Seção 5). Somado à lista de módulos em `di/AppModules.kt`. */
val todayModule: Module = module {
    viewModelOf(::TodayViewModel)
}
