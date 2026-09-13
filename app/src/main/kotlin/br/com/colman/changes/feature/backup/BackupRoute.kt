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
 * Wrapper fino da tela Backup (Seção 5): coleta o estado do [BackupViewModel] e abre a prévia do
 * import quando o plano fica pronto.
 */
@Composable
fun BackupRoute(
    onBack: () -> Unit,
    onOpenPreview: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BackupViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                BackupEffect.OpenImportPreview -> onOpenPreview()
            }
        }
    }

    BackupScreen(state = state, onEvent = viewModel::onEvent, onBack = onBack, modifier = modifier)
}
