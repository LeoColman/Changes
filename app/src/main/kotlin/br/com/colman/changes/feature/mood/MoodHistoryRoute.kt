// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.mood

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

/**
 * Wrapper fino do histórico de humor (Seção 5): coleta o estado do [MoodHistoryViewModel] e delega a
 * renderização para [MoodHistoryScreen]. Tocar num dia abre o check-in daquele dia por [onOpenDay].
 */
@Composable
fun MoodHistoryRoute(
    onOpenDay: (Long) -> Unit,
    onOpenSupport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MoodHistoryViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MoodHistoryScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenDay = onOpenDay,
        onOpenSupport = onOpenSupport,
        onBack = onBack,
        modifier = modifier,
    )
}
