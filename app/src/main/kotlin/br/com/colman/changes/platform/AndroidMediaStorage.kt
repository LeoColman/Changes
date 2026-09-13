// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.platform

import br.com.colman.changes.core.media.MediaStorage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Mídia no armazenamento interno privado (`filesDir/media`), nunca em MediaStore ou storage externo
 * (Seção 3.1). Um `.nomedia` impede indexação. Gravação atômica: arquivo temporário + rename.
 */
class AndroidMediaStorage(private val root: File, private val io: CoroutineDispatcher) : MediaStorage {
    init {
        root.mkdirs()
        File(root, NOMEDIA).takeUnless { it.exists() }?.createNewFile()
    }

    private val rootPath: String = root.canonicalPath + File.separator

    private fun resolve(relativePath: String): File {
        val file = File(root, relativePath).canonicalFile
        require(file.path.startsWith(rootPath)) { "Path escapes media root" }
        return file
    }

    override fun openRead(relativePath: String): InputStream? =
        resolve(relativePath).takeIf { it.isFile }?.inputStream()

    override suspend fun write(relativePath: String, source: InputStream): String = withContext(io) {
        val target = resolve(relativePath)
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.part")
        val digest = MessageDigest.getInstance("SHA-256")
        temp.outputStream().use { out ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
                out.write(buffer, 0, read)
            }
            out.fd.sync()
        }
        check(temp.renameTo(target)) { "Could not finalize media file" }
        digest.digest().toHex()
    }

    override suspend fun move(fromRelativePath: String, toRelativePath: String): Unit = withContext(io) {
        val to = resolve(toRelativePath)
        to.parentFile?.mkdirs()
        check(resolve(fromRelativePath).renameTo(to)) { "Could not move media file" }
    }

    override suspend fun delete(relativePath: String): Boolean = withContext(io) { resolve(relativePath).delete() }

    override fun exists(relativePath: String): Boolean = resolve(relativePath).isFile

    override fun listAll(): List<String> = root.walkTopDown()
        .filter { it.isFile && it.name != NOMEDIA }
        .map { it.relativeTo(root).invariantSeparatorsPath }
        .sorted()
        .toList()

    private companion object {
        const val NOMEDIA = ".nomedia"
        const val BUFFER_SIZE = 64 * 1024
        const val BYTE_MASK = 0xFF
        const val NIBBLE_BITS = 4
        const val NIBBLE_MASK = 0x0F
        val HEX = "0123456789abcdef".toCharArray()

        fun ByteArray.toHex(): String {
            val chars = CharArray(size * 2)
            forEachIndexed { i, byte ->
                val value = byte.toInt() and BYTE_MASK
                chars[i * 2] = HEX[value ushr NIBBLE_BITS]
                chars[i * 2 + 1] = HEX[value and NIBBLE_MASK]
            }
            return String(chars)
        }
    }
}
