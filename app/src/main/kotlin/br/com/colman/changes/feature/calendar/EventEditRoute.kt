// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.calendar

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
 * Wrapper fino de criar/editar evento (Seção 5). `eventId` nulo cria um evento novo; `epochDay` é o
 * dia tocado no calendário. Excluir mostra "Desfazer" antes de voltar; sem desfazer, chama [onBack].
 */
@Composable
fun EventEditRoute(
    eventId: String?,
    epochDay: Long?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EventEditViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.calendar_event_deleted_message)
    val undoLabel = stringResource(R.string.action_undo)

    LaunchedEffect(viewModel, eventId, epochDay) {
        viewModel.onEvent(EventEditUiEvent.Load(eventId, epochDay))
    }

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.effects.collect { effect ->
            when (effect) {
                EventEditEffect.NavigateBack -> onBack()
                EventEditEffect.ShowUndoDelete -> {
                    val result = snackbarHostState.showSnackbar(message = deletedMessage, actionLabel = undoLabel)
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.onEvent(EventEditUiEvent.UndoDelete)
                    } else {
                        onBack()
                    }
                }
            }
        }
    }

    EventEditScreen(
        state = state,
        onEvent = viewModel::onEvent,
        modifier = modifier,
        snackbarHostState = snackbarHostState
    )
}
