// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.mood

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Wrapper fino de Apoio (Seção 5). Conteúdo estático, sem ViewModel: não há estado nem escrita.
 */
@Composable
fun SupportRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    SupportScreen(onBack = onBack, modifier = modifier)
}
