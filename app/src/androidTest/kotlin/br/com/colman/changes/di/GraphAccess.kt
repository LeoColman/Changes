// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.di

import org.koin.core.context.GlobalContext

/**
 * Acesso ao grafo Koin da `ChangesApplication` de verdade (Seção 11.3): ela já iniciou o Koin quando
 * o processo do app sobe, antes de qualquer teste instrumentado rodar. Os testes de fluxo usam esta
 * função para pegar repositórios e serviços direto do grafo, em vez de duplicar bindings ou usar
 * `KoinComponent` fora de `di` (regra coberta por `ArchitectureSpec`).
 */
inline fun <reified T : Any> graphGet(): T = GlobalContext.get().get()
