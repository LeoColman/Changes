// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.medication

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Módulo da feature `medication` (Seção 5): regimes, registro e histórico de doses. */
val medicationModule: Module = module {
    single { MedicationDisplayNames(get(), get()) }

    viewModelOf(::RegimenListViewModel)
    viewModelOf(::RegimenEditViewModel)
    viewModelOf(::LogDoseViewModel)
    viewModelOf(::DoseHistoryViewModel)
}
