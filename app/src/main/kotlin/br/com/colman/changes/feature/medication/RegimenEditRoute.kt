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
 * Wrapper fino de criar/editar regime. `regimenId` nulo cria um regime novo. Todo desfecho (voltar,
 * salvar, excluir) chama [onDone]: quem navegou até aqui decide o que fazer a seguir.
 */
@Composable
fun RegimenEditRoute(
    regimenId: String?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RegimenEditViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, regimenId) {
        viewModel.onEvent(RegimenEditUiEvent.Load(regimenId))
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                RegimenEditEffect.NavigateBack -> onDone()
            }
        }
    }

    RegimenEditScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}
