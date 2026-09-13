// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data.backup

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

private const val BUFFER_SIZE = 64 * 1024
private const val END_OF_STREAM = -1
private const val BYTE_MASK = 0xFF
private const val NIBBLE_BITS = 4
private const val NIBBLE_MASK = 0x0F
private val HEX = "0123456789abcdef".toCharArray()

internal fun sha256(): MessageDigest = MessageDigest.getInstance("SHA-256")

/**
 * Copia [input] em blocos para [output] (quando houver), atualizando [digest], e fecha [input].
 * Devolve o total de bytes. Memória constante.
 */
internal fun copyHashing(input: InputStream, output: OutputStream?, digest: MessageDigest): Long {
    val buffer = ByteArray(BUFFER_SIZE)
    var total = 0L
    try {
        var read = input.read(buffer)
        while (read != END_OF_STREAM) {
            digest.update(buffer, 0, read)
            output?.write(buffer, 0, read)
            total += read
            read = input.read(buffer)
        }
    } finally {
        input.close()
    }
    return total
}

/** Hex minúsculo, sem `String.format` (que depende de locale). */
internal fun ByteArray.toHex(): String {
    val chars = CharArray(size * 2)
    for (index in indices) {
        val value = this[index].toInt() and BYTE_MASK
        chars[index * 2] = HEX[value ushr NIBBLE_BITS]
        chars[index * 2 + 1] = HEX[value and NIBBLE_MASK]
    }
    return String(chars)
}

/**
 * Lê no máximo [limit] bytes e fecha o stream; `null` se o conteúdo passar do limite (arquivo hostil
 * ou corrompido).
 */
internal fun InputStream.readAtMost(limit: Long): ByteArray? {
    val bytes = try {
        readNBytes((limit + 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    } finally {
        close()
    }
    return if (bytes.size > limit) null else bytes
}
