// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Entrada de fotos (Seção 7.3). Toda foto, da câmera ou da galeria, é decodificada e recodificada
 * como JPEG antes de entrar no armazenamento do app. O encoder do Android não escreve EXIF, então o
 * arquivo final não tem GPS, data, modelo de câmera, XMP nem maker notes (critério 7.3.1). A
 * orientação EXIF é aplicada aos pixels antes de ser descartada.
 */
class PhotoSanitizer(private val context: Context, private val io: CoroutineDispatcher) {

    /** Cópia limpa de uma imagem da galeria. Quem chama apaga o arquivo devolvido depois de usar. */
    suspend fun sanitizedCopy(source: Uri): File = withContext(io) {
        val raw = tempFile("import-", ".img")
        try {
            val input = context.contentResolver.openInputStream(source) ?: throw IOException("Unreadable image")
            input.use { stream -> raw.outputStream().use { stream.copyTo(it) } }
            sanitize(raw)
        } finally {
            raw.delete()
        }
    }

    /** Recodifica [raw] num JPEG novo, sem metadados. [raw] não é alterado. */
    fun sanitize(raw: File): File {
        val orientation = ExifInterface(raw).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(raw.path, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight) }
        val decoded = BitmapFactory.decodeFile(raw.path, options) ?: throw IOException("Not an image")
        val oriented = rotate(decoded, orientation)
        val clean = tempFile("clean-", ".jpg")
        clean.outputStream().use { oriented.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
        if (oriented !== decoded) decoded.recycle()
        oriented.recycle()
        return clean
    }

    /** Arquivo de destino para a câmera (TakePicture) e o Uri do FileProvider que aponta para ele. */
    fun cameraTarget(): Pair<File, Uri> {
        val file = tempFile("camera-", ".jpg")
        return file to FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    private fun tempFile(prefix: String, suffix: String): File {
        val dir = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }
        return File.createTempFile(prefix, suffix, dir)
    }

    private companion object {
        const val CACHE_DIR = "photos"
        const val MAX_EDGE = 4096
        const val JPEG_QUALITY = 92
        const val QUARTER_TURN = 90f
        const val HALF_TURN = 180f
        const val THREE_QUARTER_TURN = 270f

        /** Maior potência de 2 que mantém o lado maior acima de [MAX_EDGE]. */
        fun sampleSize(width: Int, height: Int): Int {
            var sample = 1
            while (maxOf(width, height) / (sample * 2) >= MAX_EDGE) sample *= 2
            return sample
        }

        fun rotate(bitmap: Bitmap, orientation: Int): Bitmap {
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(QUARTER_TURN)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(HALF_TURN)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(THREE_QUARTER_TURN)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.postRotate(QUARTER_TURN)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.postRotate(THREE_QUARTER_TURN)
                    matrix.postScale(-1f, 1f)
                }
                else -> return bitmap
            }
            return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    }
}
