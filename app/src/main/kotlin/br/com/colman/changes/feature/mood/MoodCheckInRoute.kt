// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.mood

import androidx.compose.material3.SnackbarHostState
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
 * Wrapper fino do check-in diário (Seção 5). [epochDay] nulo é hoje no fuso atual; um valor abre ou
 * edita o registro daquele dia (critério 7.7.1). Não conhece Koin além de resolver o ViewModel.
 */
@Composable
fun MoodCheckInRoute(
    epochDay: Long?,
    onBack: () -> Unit,
    onOpenSupport: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MoodCheckInViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val savedMessage = stringResource(R.string.mood_saved)

    LaunchedEffect(viewModel, epochDay) {
        viewModel.onEvent(MoodCheckInUiEvent.Load(epochDay))
    }

    LaunchedEffect(viewModel, snackbarHostState) {
        viewModel.effects.collect { effect ->
            when (effect) {
                MoodCheckInEffect.Saved -> snackbarHostState.showSnackbar(savedMessage)
            }
        }
    }

    MoodCheckInScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenSupport = onOpenSupport,
        onBack = onBack,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
    )
}
