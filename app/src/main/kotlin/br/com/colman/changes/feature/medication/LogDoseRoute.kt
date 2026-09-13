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
 * Wrapper fino de registrar dose. `regimenId` pré-preenche a partir do regime; `plannedEpochDay` é a
 * data da dose prevista tocada no calendário; `doseLogId` edita um registro existente. Todo desfecho
 * chama [onDone].
 */
@Composable
fun LogDoseRoute(
    regimenId: String?,
    plannedEpochDay: Long?,
    doseLogId: String?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LogDoseViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, regimenId, plannedEpochDay, doseLogId) {
        viewModel.onEvent(LogDoseUiEvent.Load(regimenId, plannedEpochDay, doseLogId))
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                LogDoseEffect.NavigateBack -> onDone()
            }
        }
    }

    LogDoseScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}
