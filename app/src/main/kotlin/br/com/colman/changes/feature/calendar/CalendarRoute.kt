// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.calendar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

/**
 * Wrapper fino da tela Calendário (Seção 5): coleta o estado do [CalendarViewModel] e delega a
 * renderização para [CalendarScreen]. Navegação para outras telas sai por [navigation].
 */
@Composable
fun CalendarRoute(
    navigation: CalendarNavigation,
    modifier: Modifier = Modifier,
    viewModel: CalendarViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CalendarScreen(state = state, onEvent = viewModel::onEvent, navigation = navigation, modifier = modifier)
}
