// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * Módulo Koin da feature `body` (Seção 5). Somado à lista de módulos em
 * `di/AppModules.kt`; esta feature não edita esse arquivo.
 */
val bodyModule: Module = module {
    single { BodyLabels(get(), get()) }
    // Agrupam dependências para não estourar o limite de parâmetros do construtor (LongParameterList).
    single { BodyEntryEditRepositories(get(), get(), get()) }
    single { BodyVoiceControls(get(), get(), get()) }
    viewModelOf(::BodyHomeViewModel)
    viewModel { (typeId: String) -> BodyChangeTypeViewModel(get(), get(), get(), get(), typeId) }
    viewModel { (args: BodyEntryEditArgs, photoIntake: BodyPhotoIntake) ->
        BodyEntryEditViewModel(get(), photoIntake, get(), get(), args)
    }
}
