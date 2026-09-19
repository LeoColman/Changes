// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

/** Tela cheia: decodifica maior, para o zoom ter detalhe, com teto para não estourar a memória. */
private const val VIEWER_MAX_EDGE = 2048

/**
 * Miniatura de uma foto de mudança corporal, sempre censurada por padrão (ADR 0013): fortemente
 * borrada ([blurredCopy]), com um botão de olho por cima para mostrar. A revelação vale só enquanto a tela está
 * aberta (`rememberSaveable` por [key]) e só para esta foto; usada em `BodyHomeScreen`,
 * `BodyChangeTypeScreen` e `BodyEntryEditScreen`. A decodificação de [load] roda fora da thread
 * principal, e `produceState` guarda o resultado em memória por [key] enquanto o composable
 * permanece na composição, que é o cache simples por id. Nunca grava miniatura em disco.
 *
 * Tocar na foto (fora do olho) abre a tela cheia (ADR 0013), no mesmo estado de censura desta miniatura;
 * o "x" da tira de fotos é um botão irmão por cima, então continua consumindo o toque dele antes.
 */
@Composable
fun BodyThumbnailImage(
    key: Any,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    load: suspend (fullSize: Boolean) -> ImageBitmap?,
) {
    var revealed by rememberSaveable(key) { mutableStateOf(false) }
    var viewerOpen by rememberSaveable(key) { mutableStateOf(false) }
    val openLabel = stringResource(R.string.body_photo_open)
    Box(modifier, contentAlignment = Alignment.Center) {
        CensoredImage(
            key,
            revealed,
            contentDescription,
            Modifier.matchParentSize().clickable(onClickLabel = openLabel) { viewerOpen = true },
        ) { load(false) }
        // No canto inferior esquerdo: o canto superior direito é do botão de remover na tira de fotos.
        RevealButton(
            revealed = revealed,
            showLabel = stringResource(R.string.body_photo_show),
            hideLabel = stringResource(R.string.body_photo_hide),
            onToggle = { revealed = !revealed },
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
    if (viewerOpen) {
        // A tela cheia carrega a foto maior: o zoom mostra detalhe, não a miniatura ampliada.
        BodyPhotoViewerDialog(
            key = key,
            contentDescription = contentDescription,
            initialRevealed = revealed,
            load = { load(true) },
            onDismiss = { viewerOpen = false },
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
        val blurred = remember(loaded) { blurredCopy(loaded.asAndroidBitmap()).asImageBitmap() }
        // Ampliada com o filtro bilinear padrão: o borrão fica liso, sem blocos nem contorno.
        Image(
            bitmap = blurred,
            contentDescription = null,
            modifier = modifier.semantics { this.contentDescription = censoredDescription },
        )
    }
}

/**
 * Botão de olho (ADR 0013): [Icons.Outlined.Visibility] mostra, [Icons.Outlined.VisibilityOff] censura de
 * novo. Com fundo próprio, para aparecer sobre qualquer foto borrada.
 */
@Composable
fun RevealButton(
    revealed: Boolean,
    showLabel: String,
    hideLabel: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalIconButton(onClick = onToggle, modifier = modifier) {
        if (revealed) {
            Icon(Icons.Outlined.VisibilityOff, contentDescription = hideLabel)
        } else {
            Icon(Icons.Outlined.Visibility, contentDescription = showLabel)
        }
    }
}

/**
 * Lê e decodifica uma foto já anexada; `null` se o arquivo sumiu ou não decodifica. Com [fullSize], a
 * decodificação vai até [VIEWER_MAX_EDGE] (tela cheia); sem ele, até [THUMBNAIL_MAX_EDGE] (listas).
 */
suspend fun loadBodyPhoto(
    mediaRepository: MediaRepository,
    photo: MediaAttachment,
    io: CoroutineDispatcher,
    fullSize: Boolean,
): ImageBitmap? = withContext(io) {
    val bytes = mediaRepository.openRead(photo)?.use { it.readBytes() } ?: return@withContext null
    decodeSampled(bytes, maxEdgeFor(fullSize))
}

/** O mesmo para um arquivo local ainda não anexado (foto pendente de salvar). */
suspend fun loadLocalPhoto(path: String, io: CoroutineDispatcher, fullSize: Boolean): ImageBitmap? = withContext(io) {
    val file = File(path)
    if (!file.isFile) return@withContext null
    decodeSampled(file.readBytes(), maxEdgeFor(fullSize))
}

private fun maxEdgeFor(fullSize: Boolean): Int = if (fullSize) VIEWER_MAX_EDGE else THUMBNAIL_MAX_EDGE

private fun decodeSampled(bytes: ByteArray, maxEdge: Int): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxEdge)
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
}

/** Maior potência de 2 que mantém o lado maior acima de [maxEdge]. */
private fun sampleSizeFor(width: Int, height: Int, maxEdge: Int): Int {
    var sample = 1
    while (maxOf(width, height) / (sample * 2) >= maxEdge) sample *= 2
    return sample
}
