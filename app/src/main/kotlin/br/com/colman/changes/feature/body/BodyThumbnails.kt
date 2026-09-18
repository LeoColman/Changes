// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import br.com.colman.changes.R
import br.com.colman.changes.core.data.MediaRepository
import br.com.colman.changes.core.model.MediaAttachment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File

private const val THUMBNAIL_MAX_EDGE = 512

/** Largura da cópia pixelada (ADR 0013): poucos pixels, ampliados com [FilterQuality.None]. */
private const val PIXELATION_WIDTH = 12
private const val DARKEN_ALPHA = 0.55f

/**
 * Miniatura de uma foto de mudança corporal, sempre censurada por padrão (ADR 0013): pixelada e
 * escurecida, com um botão de olho por cima para mostrar. A revelação vale só enquanto a tela está
 * aberta (`rememberSaveable` por [key]) e só para esta foto; usada em `BodyHomeScreen`,
 * `BodyChangeTypeScreen` e `BodyEntryEditScreen`. A decodificação de [load] roda fora da thread
 * principal, e `produceState` guarda o resultado em memória por [key] enquanto o composable
 * permanece na composição, que é o cache simples por id. Nunca grava miniatura em disco.
 */
@Composable
fun BodyThumbnailImage(
    key: Any,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    load: suspend () -> ImageBitmap?,
) {
    var revealed by rememberSaveable(key) { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.Center) {
        CensoredImage(key, revealed, contentDescription, Modifier.matchParentSize(), load)
        RevealButton(
            revealed = revealed,
            showLabel = stringResource(R.string.body_photo_show),
            hideLabel = stringResource(R.string.body_photo_hide),
            onToggle = { revealed = !revealed },
        )
    }
}

/**
 * Uma foto já carregada, censurada ou não conforme [revealed]. Usada diretamente (sem botão
 * próprio) pelo slider de comparação, onde um único olho revela as duas fotos de uma vez.
 */
@Composable
fun CensoredImage(
    key: Any,
    revealed: Boolean,
    contentDescription: String?,
    modifier: Modifier,
    load: suspend () -> ImageBitmap?,
) {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = key) { value = load() }
    val loaded = bitmap ?: return
    if (revealed) {
        Image(bitmap = loaded, contentDescription = contentDescription, modifier = modifier)
    } else {
        val censoredDescription = stringResource(R.string.body_photo_censored)
        val pixelated = remember(loaded) { pixelatedCopy(loaded) }
        Image(
            bitmap = pixelated,
            contentDescription = null,
            modifier = modifier
                .semantics { this.contentDescription = censoredDescription }
                .drawWithContent {
                    drawContent()
                    drawRect(color = Color.Black, alpha = DARKEN_ALPHA)
                },
            filterQuality = FilterQuality.None,
        )
    }
}

/** Botão de olho (ADR 0013): [Icons.Outlined.Visibility] mostra, [Icons.Outlined.VisibilityOff] censura de novo. */
@Composable
fun RevealButton(
    revealed: Boolean,
    showLabel: String,
    hideLabel: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(onClick = onToggle, modifier = modifier) {
        if (revealed) {
            Icon(Icons.Outlined.VisibilityOff, contentDescription = hideLabel)
        } else {
            Icon(Icons.Outlined.Visibility, contentDescription = showLabel)
        }
    }
}

/** Reduz [bitmap] a poucos pixels de largura; ampliada com `FilterQuality.None` dá o efeito pixelado. */
private fun pixelatedCopy(bitmap: ImageBitmap): ImageBitmap {
    val source = bitmap.asAndroidBitmap()
    val width = PIXELATION_WIDTH.coerceAtMost(source.width).coerceAtLeast(1)
    val height = (source.height * width / source.width).coerceAtLeast(1)
    return Bitmap.createScaledBitmap(source, width, height, false).asImageBitmap()
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
