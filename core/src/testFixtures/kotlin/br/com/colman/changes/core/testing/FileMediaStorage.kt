// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.testing

import br.com.colman.changes.core.media.MediaStorage
import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/** [MediaStorage] em diretório comum, para testes JVM. */
public class FileMediaStorage(public val root: File) : MediaStorage {
    init {
        root.mkdirs()
    }

    private fun resolve(relativePath: String): File {
        val file = File(root, relativePath).canonicalFile
        require(file.path.startsWith(root.canonicalPath + File.separator)) { "Path escapes media root: $relativePath" }
        return file
    }

    override fun openRead(relativePath: String): InputStream? = resolve(
        relativePath
    ).takeIf { it.isFile }?.inputStream()

    override suspend fun write(relativePath: String, source: InputStream): String {
        val target = resolve(relativePath)
        target.parentFile.mkdirs()
        val digest = MessageDigest.getInstance("SHA-256")
        target.outputStream().use { out ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
                out.write(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    override suspend fun move(fromRelativePath: String, toRelativePath: String) {
        val to = resolve(toRelativePath)
        to.parentFile.mkdirs()
        check(resolve(fromRelativePath).renameTo(to)) { "Could not move $fromRelativePath" }
    }

    override suspend fun delete(relativePath: String): Boolean = resolve(relativePath).delete()

    override fun exists(relativePath: String): Boolean = resolve(relativePath).isFile

    override fun listAll(): List<String> = root.walkTopDown()
        .filter { it.isFile }
        .map { it.relativeTo(root).invariantSeparatorsPath }
        .sorted()
        .toList()

    private companion object {
        const val BUFFER = 64 * 1024
    }
}
