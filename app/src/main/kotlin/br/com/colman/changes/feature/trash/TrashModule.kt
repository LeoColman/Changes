// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.trash

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/** Módulo da feature `trash` (Seção 9): lixeira de 30 dias em Ajustes. */
val trashModule: Module = module {
    viewModelOf(::TrashViewModel)
}
