// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.platform

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.OutputStream

/**
 * Ponte com o Storage Access Framework (Seção 7.8): export e import sem permissão de armazenamento.
 * O arquivo de import é copiado para o cache privado, porque a validação lê o zip com acesso
 * aleatório e mais de uma vez. As Uris chegam como texto (`uri.toString()` do contrato de
 * ActivityResult): assim os ViewModels não dependem de `android.net.Uri` e são testáveis em JVM.
 */
interface DocumentStreams {
    /** Abre o documento criado pela pessoa (ACTION_CREATE_DOCUMENT). Quem chama fecha o stream. */
    suspend fun openOutput(uri: String): OutputStream

    /** Copia o documento escolhido (ACTION_OPEN_DOCUMENT) para um arquivo temporário privado. */
    suspend fun copyToCache(uri: String): File

    /** Apaga cópias temporárias de import que tenham sobrado. */
    suspend fun clearImportCache()
}

class AndroidDocumentStreams(private val context: Context, private val io: CoroutineDispatcher) : DocumentStreams {

    override suspend fun openOutput(uri: String): OutputStream = withContext(io) {
        context.contentResolver.openOutputStream(Uri.parse(uri), "wt") ?: throw IOException("Cannot write document")
    }

    override suspend fun copyToCache(uri: String): File = withContext(io) {
        val dir = File(context.cacheDir, IMPORT_DIR).apply { mkdirs() }
        val target = File.createTempFile("import-", ".zip", dir)
        val input = context.contentResolver.openInputStream(Uri.parse(uri)) ?: throw IOException("Cannot read document")
        input.use { stream -> target.outputStream().use { stream.copyTo(it) } }
        target
    }

    override suspend fun clearImportCache(): Unit = withContext(io) {
        File(context.cacheDir, IMPORT_DIR).listFiles()?.forEach { it.delete() }
    }

    private companion object {
        const val IMPORT_DIR = "imports"
    }
}
