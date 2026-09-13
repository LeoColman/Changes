// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.medication

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
 * Wrapper fino do histórico de doses. [onEditLog] abre [LogDoseRoute] com o `doseLogId` tocado; o
 * grafo (`nav/`) decide a rota.
 */
@Composable
fun DoseHistoryRoute(
    onBack: () -> Unit,
    onEditLog: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DoseHistoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.action_undo)
    val deletedMessage = stringResource(R.string.medication_log_deleted)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is DoseHistoryEffect.ShowUndoSnackbar -> {
                    val result = snackbarHostState.showSnackbar(message = deletedMessage, actionLabel = undoLabel)
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.onEvent(DoseHistoryUiEvent.Undo(effect.doseLogId))
                    }
                }
                is DoseHistoryEffect.NavigateToEdit -> onEditLog(effect.doseLogId)
                DoseHistoryEffect.NavigateBack -> onBack()
            }
        }
    }

    DoseHistoryScreen(
        state = state,
        onEvent = viewModel::onEvent,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}
