// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.platform

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import br.com.colman.changes.core.model.MediaPaths
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteOrder

/**
 * Estimativa de quão grave está a voz numa gravação (ADR 0014): a mediana da frequência fundamental, em
 * Hz. Tudo no aparelho, sem rede.
 */
interface VoicePitchAnalyzer {
    /** Mediana da fundamental de [file]; `null` se o áudio não pôde ser lido ou não tem voz suficiente. */
    suspend fun medianPitchHz(file: File): Double?

    /** O mesmo, para uma gravação já anexada, pelo caminho relativo da mídia. */
    suspend fun medianPitchHzOfMedia(relativePath: String): Double?
}

/** Decodifica o AAC da gravação para PCM com `MediaCodec` e passa para [medianPitchHz]. */
class AndroidVoicePitchAnalyzer(private val mediaRoot: File, private val io: CoroutineDispatcher) : VoicePitchAnalyzer {
    override suspend fun medianPitchHz(file: File): Double? = withContext(io) {
        runCatching { decodeMono(file) }.getOrNull()?.let { medianPitchHz(it.samples, it.sampleRate) }
    }

    override suspend fun medianPitchHzOfMedia(relativePath: String): Double? =
        if (MediaPaths.isValid(relativePath)) medianPitchHz(File(mediaRoot, relativePath)) else null
}

private class DecodedAudio(val samples: ShortArray, val sampleRate: Int)

private const val DEQUEUE_TIMEOUT_US = 10_000L

/** Desiste se o decodificador ficar este tanto de vezes seguidas sem devolver nada (5 s). */
private const val MAX_IDLE_POLLS = 500

private fun decodeMono(file: File): DecodedAudio? {
    val extractor = MediaExtractor()
    try {
        extractor.setDataSource(file.path)
        val track = (0 until extractor.trackCount).firstOrNull { index ->
            extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: return null
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(requireNotNull(format.getString(MediaFormat.KEY_MIME)))
        try {
            codec.configure(format, null, null, 0)
            codec.start()
            return PcmDrain(extractor, codec, format).run()
        } finally {
            codec.release()
        }
    } finally {
        extractor.release()
    }
}

/**
 * Laço de decodificação: entrega amostras comprimidas do [extractor] ao [codec] e junta o PCM de 16 bits
 * que sai dele, mono (média dos canais, se houver mais de um).
 */
private class PcmDrain(
    private val extractor: MediaExtractor,
    private val codec: MediaCodec,
    format: MediaFormat,
) {
    private var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
    private var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
    private val pcm = ShortBuilder()
    private val info = MediaCodec.BufferInfo()
    private var inputDone = false
    private var outputDone = false
    private var idlePolls = 0

    fun run(): DecodedAudio? {
        while (!outputDone && idlePolls < MAX_IDLE_POLLS) {
            if (!inputDone) feedInput()
            drainOutput()
        }
        return DecodedAudio(pcm.build(), sampleRate).takeIf { outputDone }
    }

    private fun feedInput() {
        val index = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
        if (index < 0) return
        val buffer = requireNotNull(codec.getInputBuffer(index))
        val size = extractor.readSampleData(buffer, 0)
        if (size < 0) {
            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            inputDone = true
        } else {
            codec.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
            extractor.advance()
        }
    }

    private fun drainOutput() {
        val index = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
        when {
            index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                sampleRate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                channels = codec.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            }
            index >= 0 -> {
                idlePolls = 0
                val buffer = requireNotNull(codec.getOutputBuffer(index))
                buffer.position(info.offset)
                buffer.limit(info.offset + info.size)
                val shorts = buffer.order(ByteOrder.nativeOrder()).asShortBuffer()
                while (shorts.remaining() >= channels) {
                    var sum = 0
                    repeat(channels) { sum += shorts.get() }
                    pcm.add((sum / channels).toShort())
                }
                codec.releaseOutputBuffer(index, false)
                outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            }
            else -> idlePolls++
        }
    }
}

/** `ShortArray` que cresce sem caixa: 60 s de áudio a 48 kHz são menos de 6 MB. */
private class ShortBuilder {
    private var data = ShortArray(INITIAL_CAPACITY)
    private var size = 0

    fun add(value: Short) {
        if (size == data.size) data = data.copyOf(data.size * 2)
        data[size++] = value
    }

    fun build(): ShortArray = data.copyOf(size)

    private companion object {
        const val INITIAL_CAPACITY = 1 shl 16
    }
}
