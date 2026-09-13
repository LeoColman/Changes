// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.mood

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Módulo da feature `mood` (Seção 5): check-in diário e histórico. Apoio não tem ViewModel. */
val moodModule: Module = module {
    viewModelOf(::MoodCheckInViewModel)
    viewModelOf(::MoodHistoryViewModel)
}
