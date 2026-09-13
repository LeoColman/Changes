// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.media

import java.io.InputStream

/**
 * Armazenamento privado de mídia (Seção 3.1). Caminhos são relativos à raiz de mídia do app
 * (`filesDir/media/` no Android) e validados por [MediaPaths]. A implementação Android fica em
 * `app.platform` e é responsável por remover EXIF de localização antes de gravar fotos importadas.
 */
public interface MediaStorage {
    /** Abre o arquivo para leitura, ou `null` se não existir. Quem chama fecha o stream. */
    public fun openRead(relativePath: String): InputStream?

    /** Grava [source] em [relativePath] (substitui se existir) e devolve o SHA-256 em hex minúsculo. */
    public suspend fun write(relativePath: String, source: InputStream): String

    /** Move um arquivo já gravado. Usado para promover arquivos de staging no import. */
    public suspend fun move(fromRelativePath: String, toRelativePath: String)

    public suspend fun delete(relativePath: String): Boolean

    public fun exists(relativePath: String): Boolean

    /** Todos os arquivos sob a raiz (recursivo), como caminhos relativos. */
    public fun listAll(): List<String>
}
