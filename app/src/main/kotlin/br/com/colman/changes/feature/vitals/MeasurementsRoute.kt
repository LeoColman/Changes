// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.vitals

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.colman.changes.R
import org.koin.androidx.compose.koinViewModel

/**
 * Wrapper fino (Seção 5): coleta o estado, trata o efeito de desfazer e chama [MeasurementsScreen].
 * Não conhece Koin além de resolver o [MeasurementsViewModel].
 */
@Composable
fun MeasurementsRoute(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MeasurementsViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.vitals_entry_deleted)
    val undoLabel = stringResource(R.string.action_undo)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                MeasurementsEffect.ShowUndoDelete -> {
                    val result = snackbarHostState.showSnackbar(
                        message = deletedMessage,
                        actionLabel = undoLabel,
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.onEvent(MeasurementsUiEvent.UndoDeleteRequested)
                    }
                }
            }
        }
    }

    MeasurementsScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}
