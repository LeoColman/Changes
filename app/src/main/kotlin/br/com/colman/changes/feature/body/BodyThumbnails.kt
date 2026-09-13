// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import br.com.colman.changes.core.data.MediaRepository
import br.com.colman.changes.core.model.MediaAttachment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File

private const val THUMBNAIL_MAX_EDGE = 512

/**
 * Miniatura de uma foto de mudança corporal: a decodificação de [load] roda fora
 * da thread principal, e `produceState` guarda o resultado em memória por [key] enquanto o
 * composable permanece na composição, que é o cache simples por id. Nunca grava
 * miniatura em disco.
 */
@Composable
fun BodyThumbnailImage(
    key: Any,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    load: suspend () -> ImageBitmap?,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = key) { value = load() }
    val loaded = bitmap
    if (loaded != null) {
        Image(bitmap = loaded, contentDescription = contentDescription, modifier = modifier)
    }
}

/** Lê e decodifica a miniatura de uma foto já anexada; `null` se o arquivo sumiu ou não decodifica. */
suspend fun loadBodyThumbnail(
    mediaRepository: MediaRepository,
    photo: MediaAttachment,
    io: CoroutineDispatcher,
): ImageBitmap? = withContext(io) {
    val bytes = mediaRepository.openRead(photo)?.use { it.readBytes() } ?: return@withContext null
    decodeSampled(bytes)
}

/** Lê e decodifica a miniatura de um arquivo local ainda não anexado (foto pendente de salvar). */
suspend fun loadLocalThumbnail(path: String, io: CoroutineDispatcher): ImageBitmap? = withContext(io) {
    val file = File(path)
    if (!file.isFile) return@withContext null
    decodeSampled(file.readBytes())
}

private fun decodeSampled(bytes: ByteArray): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight) }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}

/** Maior potência de 2 que mantém o lado maior acima de [THUMBNAIL_MAX_EDGE]. */
private fun sampleSizeFor(width: Int, height: Int): Int {
    var sample = 1
    while (maxOf(width, height) / (sample * 2) >= THUMBNAIL_MAX_EDGE) sample *= 2
    return sample
}
