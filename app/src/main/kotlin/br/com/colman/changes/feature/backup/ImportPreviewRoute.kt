// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.backup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

/**
 * Wrapper fino da tela de prévia do import (Seção 5). O botão de voltar da barra superior manda um
 * [ImportPreviewUiEvent.Cancel] em vez de sair direto, porque cancelar precisa apagar a cópia
 * temporária do arquivo antes de navegar.
 */
@Composable
fun ImportPreviewRoute(
    onBack: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImportPreviewViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ImportPreviewEffect.NavigateBack -> onBack()
                ImportPreviewEffect.Finished -> onDone()
            }
        }
    }

    ImportPreviewScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = { viewModel.onEvent(ImportPreviewUiEvent.Cancel) },
        modifier = modifier,
    )
}
