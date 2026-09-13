// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.today

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
 * Wrapper fino da tela Hoje (Seção 5): coleta o estado do [TodayViewModel], escuta os efeitos de
 * uma vez (mostra o snackbar de "Desfazer" depois de registrar uma dose) e delega a renderização
 * para [TodayScreen].
 */
@Composable
fun TodayRoute(
    navigation: TodayNavigation,
    modifier: Modifier = Modifier,
    viewModel: TodayViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val doseLoggedMessage = stringResource(R.string.today_dose_logged_message)
    val undoLabel = stringResource(R.string.action_undo)

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TodayEffect.DoseLogged -> {
                    val result = snackbarHostState.showSnackbar(message = doseLoggedMessage, actionLabel = undoLabel)
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.onEvent(TodayUiEvent.UndoDoseLog(effect.doseLogId))
                    }
                }
            }
        }
    }

    TodayScreen(
        state = state,
        onEvent = viewModel::onEvent,
        navigation = navigation,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}
