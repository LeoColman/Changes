// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import android.net.Uri
import java.io.File

/** Foto ainda não sanitizada: arquivo local gravado pela câmera, ou conteúdo escolhido na galeria. */
sealed interface RawBodyPhoto {
    data class Camera(val file: File) : RawBodyPhoto

    data class Gallery(val uri: Uri) : RawBodyPhoto
}

/**
 * Sanitiza uma foto antes do anexo (seção "Dados sensíveis" do guia de código): toda foto passa por
 * aqui antes de `MediaRepository.attach`. A Route conecta a implementação real, apoiada no
 * `PhotoSanitizer` de `app.platform`; os testes de ViewModel usam uma versão falsa, sem Android.
 */
fun interface BodyPhotoIntake {
    /** Sanitiza [raw] e devolve uma cópia limpa (sem EXIF de localização). Quem chama apaga o arquivo. */
    suspend fun sanitize(raw: RawBodyPhoto): File
}
