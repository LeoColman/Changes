// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.settings

import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.core.module.Module
import org.koin.dsl.module

/** Módulo Koin da feature settings (Seção 5). Somado à lista de módulos em `di/AppModules.kt`. */
val settingsModule: Module = module {
    viewModelOf(::SettingsHomeViewModel)
    viewModelOf(::ProfileViewModel)
    viewModelOf(::VocabularyViewModel)
    viewModelOf(::OnboardingViewModel)
}
