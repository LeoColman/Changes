// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import br.com.colman.changes.platform.VoicePlayer
import br.com.colman.changes.platform.VoiceRecorder
import java.io.File
import java.nio.file.Files

/**
 * Fake de [VoiceRecorder] para os specs de `feature.body` (ADR 0013): sem `MediaRecorder`, grava um
 * arquivo temporário de verdade a cada [start] bem-sucedido, como o app precisa para anexar depois.
 */
class FakeVoiceRecorder(private val startSucceeds: Boolean = true) : VoiceRecorder {
    var startCount = 0
        private set

    var cancelled = false
        private set

    private var current: File? = null

    override fun start(): File? {
        startCount++
        if (!startSucceeds) return null
        val file = Files.createTempFile("fake-voice-", ".m4a").toFile()
        file.writeBytes(byteArrayOf(9, 9, 9))
        current = file
        return file
    }

    override fun stop(): File? {
        val file = current
        current = null
        return file
    }

    override fun cancel() {
        cancelled = true
        current?.delete()
        current = null
    }
}

/** Fake de [VoicePlayer]: não toca nada de verdade, só registra o que foi pedido. */
class FakeVoicePlayer : VoicePlayer {
    var playedFile: File? = null
        private set

    var playedRelativePath: String? = null
        private set

    var stopCount = 0
        private set

    override fun play(file: File, onFinished: () -> Unit) {
        playedFile = file
        onFinished()
    }

    override fun playMedia(relativePath: String, onFinished: () -> Unit) {
        playedRelativePath = relativePath
        onFinished()
    }

    override fun stop() {
        stopCount++
    }
}
