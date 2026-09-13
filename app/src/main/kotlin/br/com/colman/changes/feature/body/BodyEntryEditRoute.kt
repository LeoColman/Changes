// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.colman.changes.core.data.MediaRepository
import br.com.colman.changes.core.di.IoDispatcher
import br.com.colman.changes.platform.PhotoSanitizer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import java.io.File

/**
 * Wrapper fino de criação/edição de uma entrada (Seção 5). Conecta o `PhotoSanitizer` real da
 * plataforma ao `BodyPhotoIntake` que o ViewModel espera, e os contratos de câmera/galeria.
 */
@Composable
fun BodyEntryEditRoute(typeId: String?, entryId: String?, onSaved: () -> Unit, onBack: () -> Unit) {
    val mediaRepository = koinInject<MediaRepository>()
    val io = koinInject<CoroutineDispatcher>(IoDispatcher)
    val sanitizer = koinInject<PhotoSanitizer>()
    val photoIntake = remember(sanitizer, io) { realPhotoIntake(sanitizer, io) }

    val viewModel: BodyEntryEditViewModel = koinViewModel(
        parameters = { parametersOf(BodyEntryEditArgs(typeId, entryId), photoIntake) },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveSavedEffect(viewModel, onSaved)

    val onPickPhoto = rememberPhotoPickHandler(sanitizer) { raw ->
        viewModel.onEvent(BodyEntryEditUiEvent.PhotoSelected(raw))
    }

    BodyEntryEditScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onBack = onBack,
        loadPhoto = { photo -> loadEntryPhoto(mediaRepository, io, photo) },
        onPickPhoto = onPickPhoto,
    )
}

@Composable
private fun ObserveSavedEffect(viewModel: BodyEntryEditViewModel, onSaved: () -> Unit) {
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                BodyEntryEditEffect.Saved -> onSaved()
            }
        }
    }
}

/** Liga os contratos de câmera e galeria; devolve o callback que a tela usa para pedir uma foto. */
@Composable
private fun rememberPhotoPickHandler(
    sanitizer: PhotoSanitizer,
    onPhotoSelected: (RawBodyPhoto) -> Unit,
): (BodyPhotoPickSource) -> Unit {
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = pendingCameraFile
        pendingCameraFile = null
        if (success && file != null) onPhotoSelected(RawBodyPhoto.Camera(file))
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) onPhotoSelected(RawBodyPhoto.Gallery(uri))
    }
    return { source ->
        when (source) {
            BodyPhotoPickSource.Camera -> {
                val (file, uri) = sanitizer.cameraTarget()
                pendingCameraFile = file
                cameraLauncher.launch(uri)
            }
            BodyPhotoPickSource.Gallery -> galleryLauncher.launch(
                PickVisualMediaRequest(mediaType = ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }
}

private suspend fun loadEntryPhoto(mediaRepository: MediaRepository, io: CoroutineDispatcher, photo: EntryPhoto) =
    when (photo) {
        is EntryPhoto.Attached -> loadBodyThumbnail(mediaRepository, photo.media, io)
        is EntryPhoto.Pending -> loadLocalThumbnail(photo.filePath, io)
    }

/** Implementação real de [BodyPhotoIntake], apoiada no [PhotoSanitizer] da plataforma. */
private fun realPhotoIntake(sanitizer: PhotoSanitizer, io: CoroutineDispatcher): BodyPhotoIntake =
    BodyPhotoIntake { raw ->
        when (raw) {
            is RawBodyPhoto.Camera -> withContext(io) { sanitizer.sanitize(raw.file) }
            is RawBodyPhoto.Gallery -> sanitizer.sanitizedCopy(raw.uri)
        }
    }
