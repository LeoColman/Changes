// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import br.com.colman.changes.R

private const val MIN_SCALE = 1f
private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f
private val VIEWER_BUTTON_PADDING = 16.dp

/**
 * Foto de mudança corporal em tela cheia (T19): tocar em qualquer foto do corpo abre esta tela, no
 * mesmo estado de censura da miniatura que foi tocada ([initialRevealed]). O olho aqui dentro é
 * próprio desta tela e independente da miniatura por trás: fechar não muda o estado de volta lá.
 * Fecha pelo botão de fechar ou pelo botão voltar do sistema (`dismissOnBackPress`, o padrão de
 * `DialogProperties`).
 */
@Composable
fun BodyPhotoViewerDialog(
    key: Any,
    contentDescription: String?,
    initialRevealed: Boolean,
    load: suspend () -> ImageBitmap?,
    onDismiss: () -> Unit,
) {
    var revealed by rememberSaveable(key) { mutableStateOf(initialRevealed) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SecureViewerWindow()
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            ZoomableCensoredImage(key, revealed, contentDescription, load, Modifier.fillMaxSize())
            RevealButton(
                revealed = revealed,
                showLabel = stringResource(R.string.body_photo_show),
                hideLabel = stringResource(R.string.body_photo_hide),
                onToggle = { revealed = !revealed },
                modifier = Modifier.align(Alignment.BottomStart).padding(VIEWER_BUTTON_PADDING),
            )
            FilledTonalIconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(VIEWER_BUTTON_PADDING),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.body_photo_close))
            }
        }
    }
}

/**
 * Marca a janela deste diálogo com `FLAG_SECURE` (Seção 7.3 do dossiê): a janela da activity já é
 * segura (`MainActivity`), mas um `Dialog` do Compose abre a própria janela por baixo, que não herda
 * a flag sozinha. Sem isto, a foto revelada em tela cheia poderia escapar de um bloqueio de captura
 * de tela que a tela por trás já respeita.
 */
@Composable
private fun SecureViewerWindow() {
    val view = LocalView.current
    SideEffect {
        (view.parent as? DialogWindowProvider)?.window?.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
    }
}

/**
 * A mesma imagem de [CensoredImage] (nunca a original quando censurada, só a cópia borrada), com
 * zoom por pinça de [MIN_SCALE] a [MAX_SCALE], arraste quando ampliada, e toque duplo alternando 1x e
 * [DOUBLE_TAP_SCALE]x (T19). `ContentScale` padrão (`Fit`) já centraliza a foto sem cortar.
 */
@Composable
private fun ZoomableCensoredImage(
    key: Any,
    revealed: Boolean,
    contentDescription: String?,
    load: suspend () -> ImageBitmap?,
    modifier: Modifier,
) {
    var scale by remember(key) { mutableStateOf(MIN_SCALE) }
    var offset by remember(key) { mutableStateOf(Offset.Zero) }
    CensoredImage(
        key = key,
        revealed = revealed,
        contentDescription = contentDescription,
        modifier = modifier
            .pointerInput(key) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                    scale = newScale
                    offset = if (newScale > MIN_SCALE) offset + pan else Offset.Zero
                }
            }
            .pointerInput(key) {
                detectTapGestures(
                    onDoubleTap = {
                        scale = if (scale > MIN_SCALE) MIN_SCALE else DOUBLE_TAP_SCALE
                        offset = Offset.Zero
                    },
                )
            }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
        load = load,
    )
}
