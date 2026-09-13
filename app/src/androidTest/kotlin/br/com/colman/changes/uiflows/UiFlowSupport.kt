// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.uiflows

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.core.content.FileProvider
import br.com.colman.changes.di.graphGet
import br.com.colman.changes.platform.SettingsStore
import java.io.File
import kotlin.uuid.Uuid

/** Único diretório exposto pelo `FileProvider` do app (`res/xml/file_paths.xml`). */
private const val FILE_PROVIDER_DIR = "photos"
private const val SAMPLE_JPEG_EDGE = 12
private const val SAMPLE_JPEG_QUALITY = 90

/**
 * Onboarding sempre concluído e bloqueio biométrico desligado antes de abrir a `MainActivity` de
 * verdade (Seção 11.3): sem isso a primeira tela seria o onboarding, não a aba Hoje.
 */
fun markOnboardingDone() {
    graphGet<SettingsStore>().update { it.copy(onboardingDone = true, biometricLock = false) }
}

/** Texto único por execução: os dados persistem entre execuções no mesmo aparelho (Seção 11.3). */
fun uniqueText(prefix: String): String = "$prefix ${Uuid.random()}"

/** Sufixo único para nome de arquivo, sem espaço (a Uuid.random() já não tem). */
fun uniqueSuffix(): String = Uuid.random().toString()

/** Um arquivo (ainda sem conteúdo) na única pasta que o `FileProvider` do app expõe. */
fun fileProviderTarget(context: Context, name: String): File {
    val dir = File(context.cacheDir, FILE_PROVIDER_DIR).apply { mkdirs() }
    return File(dir, name)
}

/** Uri do `FileProvider` do próprio app para [file], igual à que a câmera ou o SAF entregariam. */
fun fileProviderUriFor(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.files", file)

/** JPEG pequeno e válido, decodificável pelo `PhotoSanitizer` real (Seção 11.3, critério 7.3.1). */
fun writeSampleJpeg(file: File) {
    val bitmap = Bitmap.createBitmap(SAMPLE_JPEG_EDGE, SAMPLE_JPEG_EDGE, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.DKGRAY)
    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, SAMPLE_JPEG_QUALITY, it) }
}

/**
 * `ActivityResultRegistry` falso (Seção 11.3): os seletores do sistema (galeria, SAF) não são
 * automatizáveis, então todo `launch` devolve na hora [resultUri], como se a pessoa tivesse escolhido
 * aquele arquivo ou aquela foto.
 */
class FakeActivityResultRegistryOwner(private val resultUri: Uri) : ActivityResultRegistryOwner {
    override val activityResultRegistry: ActivityResultRegistry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(
            requestCode: Int,
            contract: ActivityResultContract<I, O>,
            input: I,
            options: ActivityOptionsCompat?,
        ) {
            dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(resultUri))
        }
    }
}
