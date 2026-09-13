// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.colman.changes.core.data.MediaRepository
import br.com.colman.changes.core.di.IoDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

/** Wrapper fino da linha do tempo de um tipo (Seção 5). */
@Composable
fun BodyChangeTypeRoute(
    typeId: String,
    onBack: () -> Unit,
    onAddEntry: (typeId: String) -> Unit,
    onEditEntry: (typeId: String, entryId: String) -> Unit,
    viewModel: BodyChangeTypeViewModel = koinViewModel(parameters = { parametersOf(typeId) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mediaRepository = koinInject<MediaRepository>()
    val io = koinInject<CoroutineDispatcher>(IoDispatcher)

    BodyChangeTypeScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onNavigate = { navigation ->
            when (navigation) {
                BodyChangeTypeNavigation.Back -> onBack()
                BodyChangeTypeNavigation.AddEntry -> onAddEntry(typeId)
                is BodyChangeTypeNavigation.EditEntry -> onEditEntry(typeId, navigation.entryId)
            }
        },
        loadThumbnail = { photo -> loadBodyThumbnail(mediaRepository, photo, io) },
    )
}
