// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.platform

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import br.com.colman.changes.core.model.MediaPaths
import java.io.File

/**
 * Gravação da frase de voz (ADR 0013): AAC em MPEG-4 (`audio/mp4`, `.m4a`), mono, no cache privado do
 * app até ser anexada à entrada. Uma gravação por vez. Quem chama pede a permissão `RECORD_AUDIO` antes.
 */
interface VoiceRecorder {
    /** Começa a gravar num arquivo novo; `null` se o microfone não pôde ser aberto (sem permissão, ocupado). */
    fun start(): File?

    /** Para a gravação em curso e devolve o arquivo; `null` se nada foi gravado. */
    fun stop(): File?

    /** Para e apaga a gravação em curso, se houver. */
    fun cancel()
}

/** Reprodução de uma gravação de voz. Uma por vez: tocar outra para a anterior. */
interface VoicePlayer {
    /** Toca [file] do começo; [onFinished] roda quando termina, falha ou é interrompida por [stop]. */
    fun play(file: File, onFinished: () -> Unit)

    /** Toca uma gravação já anexada, pelo caminho relativo da mídia. */
    fun playMedia(relativePath: String, onFinished: () -> Unit)

    fun stop()
}

class AndroidVoiceRecorder(private val context: Context) : VoiceRecorder {
    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var reachedLimit = false

    override fun start(): File? {
        cancel()
        val dir = File(context.cacheDir, VOICE_DIR).apply { mkdirs() }
        val file = File.createTempFile("voice-", ".m4a", dir)
        val created = newRecorder()
        reachedLimit = false
        val started = runCatching {
            created.setAudioSource(MediaRecorder.AudioSource.MIC)
            created.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            created.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            created.setAudioChannels(1)
            created.setAudioSamplingRate(SAMPLE_RATE)
            created.setAudioEncodingBitRate(BIT_RATE)
            created.setMaxDuration(MAX_DURATION_MS)
            created.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) reachedLimit = true
            }
            created.setOutputFile(file.path)
            created.prepare()
            created.start()
        }.isSuccess
        if (started) {
            recorder = created
            output = file
        } else {
            created.release()
            file.delete()
        }
        return file.takeIf { started }
    }

    override fun stop(): File? {
        val current = recorder
        val file = output
        recorder = null
        output = null
        // Depois do limite de duração o próprio MediaRecorder já parou; chamar stop() de novo lançaria.
        val stopped = current != null && (reachedLimit || runCatching { current.stop() }.isSuccess)
        current?.release()
        if (!stopped) file?.delete()
        return file.takeIf { stopped }
    }

    override fun cancel() {
        stop()?.delete()
    }

    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else legacyRecorder()

    @Suppress("DEPRECATION")
    private fun legacyRecorder(): MediaRecorder = MediaRecorder()

    companion object {
        /** Teto de uma gravação: a frase leva poucos segundos. */
        const val MAX_DURATION_MS = 60_000
        private const val SAMPLE_RATE = 44_100
        private const val BIT_RATE = 96_000
        private const val VOICE_DIR = "voice"
    }
}

class AndroidVoicePlayer(private val mediaRoot: File) : VoicePlayer {
    private var player: MediaPlayer? = null
    private var finished: (() -> Unit)? = null

    override fun play(file: File, onFinished: () -> Unit) {
        stop()
        finished = onFinished
        val created = MediaPlayer()
        val started = runCatching {
            created.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            created.setDataSource(file.path)
            created.setOnCompletionListener { stop() }
            created.setOnErrorListener { _, _, _ ->
                stop()
                true
            }
            created.prepare()
            created.start()
        }.isSuccess
        if (started) {
            player = created
        } else {
            created.release()
            finish()
        }
    }

    override fun playMedia(relativePath: String, onFinished: () -> Unit) {
        if (MediaPaths.isValid(relativePath)) play(File(mediaRoot, relativePath), onFinished) else onFinished()
    }

    override fun stop() {
        player?.let { current ->
            runCatching { current.stop() }
            current.release()
        }
        player = null
        finish()
    }

    private fun finish() {
        val callback = finished
        finished = null
        callback?.invoke()
    }
}
