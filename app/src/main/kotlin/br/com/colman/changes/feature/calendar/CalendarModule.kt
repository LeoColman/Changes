// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.calendar

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Módulo Koin da feature calendar (Seção 5). Somado à lista de módulos em `di/AppModules.kt`. */
val calendarModule: Module = module {
    viewModelOf(::CalendarViewModel)
    viewModelOf(::EventEditViewModel)
}
