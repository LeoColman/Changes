// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import androidx.compose.ui.graphics.ImageBitmap

/** Callbacks de plataforma que a Route liga: voltar, carregar preview, escolher foto e pedir gravação. */
data class BodyEntryEditActions(
    val onBack: () -> Unit,
    val loadPhoto: suspend (EntryPhoto) -> ImageBitmap?,
    val onPickPhoto: (BodyPhotoPickSource) -> Unit,
    val onRecordRequested: () -> Unit,
)
