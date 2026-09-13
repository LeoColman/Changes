// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.vitals

import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.core.module.Module
import org.koin.dsl.module

/** Módulo Koin da feature vitals (Seção 5). Somado à lista de módulos em `di/AppModules.kt`. */
val vitalsModule: Module = module {
    viewModelOf(::MeasurementsViewModel)
    viewModelOf(::ExerciseViewModel)
    viewModelOf(::RoutinesViewModel)
}
