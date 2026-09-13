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
 * Wrapper fino (Seção 5): coleta o estado, trata os efeitos de desfazer (sessão e rotina, ADR 0011)
 * e chama [ExerciseScreen]. Não conhece Koin além de resolver os dois ViewModels.
 */
@Composable
fun ExerciseRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExerciseViewModel = koinViewModel(),
    routinesViewModel: RoutinesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val routinesState by routinesViewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.vitals_session_deleted)
    val routineDeletedMessage = stringResource(R.string.vitals_routine_deleted)
    val undoLabel = stringResource(R.string.action_undo)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect {
            snackbarHostState.showUndoSnackbar(deletedMessage, undoLabel) {
                viewModel.onEvent(ExerciseUiEvent.UndoDeleteRequested)
            }
        }
    }

    LaunchedEffect(routinesViewModel) {
        routinesViewModel.effects.collect {
            snackbarHostState.showUndoSnackbar(routineDeletedMessage, undoLabel) {
                routinesViewModel.onEvent(RoutinesUiEvent.UndoDeleteRequested)
            }
        }
    }

    ExerciseScreen(
        screenState = ExerciseScreenState(state, routinesState),
        onEvent = viewModel::onEvent,
        onRoutinesEvent = routinesViewModel::onEvent,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}

/** Snackbar padrão de "Desfazer" (Seção 9): mostra [message], e chama [onUndo] se a pessoa tocar. */
private suspend fun SnackbarHostState.showUndoSnackbar(message: String, undoLabel: String, onUndo: () -> Unit) {
    val result = showSnackbar(message = message, actionLabel = undoLabel, duration = SnackbarDuration.Short)
    if (result == SnackbarResult.ActionPerformed) onUndo()
}
