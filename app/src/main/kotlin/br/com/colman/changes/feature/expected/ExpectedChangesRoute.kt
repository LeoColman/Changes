// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.expected

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

/**
 * Wrapper fino (Seção 5): coleta o estado e chama [ExpectedChangesScreen]. [onOpenProfile] leva à
 * tela de perfil para preencher a data de início da TH; o grafo (`nav/`) decide a rota de destino.
 */
@Composable
fun ExpectedChangesRoute(
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExpectedChangesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ExpectedChangesScreen(state = state, onBack = onBack, onOpenProfile = onOpenProfile, modifier = modifier)
}
