// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

/** Wrapper fino (Seção 5) do painel de Ajustes (aba Ajustes). */
@Composable
fun SettingsHomeRoute(
    navigation: SettingsNavigation,
    modifier: Modifier = Modifier,
    viewModel: SettingsHomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsHomeScreen(state = state, onEvent = viewModel::onEvent, navigation = navigation, modifier = modifier)
}
