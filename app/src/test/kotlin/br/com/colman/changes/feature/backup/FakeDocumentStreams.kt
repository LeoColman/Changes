// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.backup

import br.com.colman.changes.platform.DocumentStreams
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

/**
 * Fake de [DocumentStreams] para teste (Seção 7.8): "grava" em memória por uri e copia para um
 * diretório temporário. Um `uri` sem gravação prévia é tratado como um caminho de arquivo literal,
 * para os testes que preparam um arquivo adulterado fora do fluxo de export.
 */
class FakeDocumentStreams(private val cacheDir: File) : DocumentStreams {
    private val written = mutableMapOf<String, ByteArray>()
    private val copiedFiles = mutableListOf<File>()

    var importCacheCleared: Boolean = false
        private set

    override suspend fun openOutput(uri: String): OutputStream = object : ByteArrayOutputStream() {
        override fun close() {
            written[uri] = toByteArray()
            super.close()
        }
    }

    override suspend fun copyToCache(uri: String): File {
        val bytes = written[uri] ?: File(uri).readBytes()
        val target = File.createTempFile("import-", ".ttbackup.zip", cacheDir)
        target.writeBytes(bytes)
        copiedFiles += target
        return target
    }

    override suspend fun clearImportCache() {
        importCacheCleared = true
        copiedFiles.forEach { it.delete() }
        copiedFiles.clear()
    }
}
