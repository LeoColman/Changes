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

/**
 * Wrapper fino da tela inicial da aba Corpo: coleta o estado e liga a navegação (Seção 5). Quem
 * chama (o grafo em `nav/`) fornece os atalhos para outras abas.
 */
@Composable
fun BodyHomeRoute(
    onOpenExpectedChanges: () -> Unit,
    onOpenMeasurements: () -> Unit,
    onOpenChangeType: (typeId: String) -> Unit,
    viewModel: BodyHomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val mediaRepository = koinInject<MediaRepository>()
    val io = koinInject<CoroutineDispatcher>(IoDispatcher)

    BodyHomeScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onNavigate = { navigation ->
            when (navigation) {
                BodyHomeNavigation.ExpectedChanges -> onOpenExpectedChanges()
                BodyHomeNavigation.Measurements -> onOpenMeasurements()
                is BodyHomeNavigation.ChangeType -> onOpenChangeType(navigation.typeId)
            }
        },
        loadThumbnail = { photo, fullSize -> loadBodyPhoto(mediaRepository, photo, io, fullSize) },
    )
}
