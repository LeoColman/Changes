// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.medication

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

/**
 * Wrapper fino da lista de regimes: coleta estado e efeitos, chama [RegimenListScreen]. Não conhece
 * outra rota; o grafo (`nav/`) liga estes callbacks.
 */
@Composable
fun RegimenListRoute(
    onOpenRegimen: (String) -> Unit,
    onCreateRegimen: () -> Unit,
    onOpenHistory: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegimenListViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is RegimenListEffect.NavigateToRegimen -> onOpenRegimen(effect.id)
                RegimenListEffect.NavigateToCreate -> onCreateRegimen()
                RegimenListEffect.NavigateToHistory -> onOpenHistory()
                RegimenListEffect.NavigateBack -> onBack()
            }
        }
    }

    RegimenListScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}
