// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.platform

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * ADR 0014: a gravação real é AAC em MPEG-4, como o `AndroidVoiceRecorder` grava. O teste codifica uma voz
 * sintética com o codificador do aparelho e confere que o decodificador entrega a fundamental de volta.
 */
@RunWith(AndroidJUnit4::class)
class VoicePitchAnalyzerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val mediaRoot = File(context.filesDir, MEDIA_DIR)
    private val analyzer = AndroidVoicePitchAnalyzer(mediaRoot, Dispatchers.IO)

    @Test
    fun aacRecordingGivesBackItsFundamental() {
        val file = File.createTempFile("pitch", ".m4a", context.cacheDir)
        encodeAac(voice(PITCH_HZ, seconds = 3.0), file)

        val estimate = runBlocking { analyzer.medianPitchHz(file) }

        assertNotNull(estimate)
        assertEquals(PITCH_HZ, requireNotNull(estimate), PITCH_HZ * 0.01)
        file.delete()
    }

    @Test
    fun attachedRecordingIsReadFromTheMediaRoot() {
        mediaRoot.mkdirs()
        val file = File(mediaRoot, "00000000-0000-4000-8000-0000000000a1.m4a")
        encodeAac(voice(PITCH_HZ, seconds = 2.0), file)

        val estimate = runBlocking { analyzer.medianPitchHzOfMedia(file.name) }

        assertEquals(PITCH_HZ, requireNotNull(estimate), PITCH_HZ * 0.01)
        file.delete()
    }

    @Test
    fun unreadableFileOrInvalidPathGivesNoEstimate() {
        val garbage = File.createTempFile("garbage", ".m4a", context.cacheDir).apply { writeBytes(ByteArray(64) { 7 }) }

        assertNull(runBlocking { analyzer.medianPitchHz(garbage) })
        assertNull(runBlocking { analyzer.medianPitchHzOfMedia("../escape.m4a") })
        garbage.delete()
    }

    private fun voice(pitchHz: Double, seconds: Double): ShortArray {
        val weights = doubleArrayOf(1.0, 0.5, 0.33, 0.25)
        return ShortArray((seconds * SAMPLE_RATE).toInt()) { n ->
            var value = 0.0
            for (h in weights.indices) value += weights[h] * sin(2 * PI * pitchHz * (h + 1) * n / SAMPLE_RATE)
            (value / weights.sum() * 0.5 * Short.MAX_VALUE).toInt().toShort()
        }
    }

    /** Codifica [samples] (mono, [SAMPLE_RATE]) em AAC-LC num MPEG-4, como o gravador do app. */
    private fun encodeAac(samples: ShortArray, target: File) {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 96_000)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(target.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        AacEncoding(samples, codec, muxer).run()
        codec.stop()
        codec.release()
        muxer.stop()
        muxer.release()
    }

    /** Laço do codificador: entrega o PCM em pedaços e grava no [muxer] cada quadro AAC que sai. */
    private class AacEncoding(
        private val samples: ShortArray,
        private val codec: MediaCodec,
        private val muxer: MediaMuxer,
    ) {
        private val info = MediaCodec.BufferInfo()
        private var track = -1
        private var offset = 0
        private var inputDone = false
        private var outputDone = false

        fun run() {
            while (!outputDone) {
                if (!inputDone) feed()
                drain()
            }
        }

        private fun feed() {
            val index = codec.dequeueInputBuffer(TIMEOUT_US)
            if (index < 0) return
            val buffer = requireNotNull(codec.getInputBuffer(index)).apply { clear() }
            val count = minOf(buffer.capacity() / 2, samples.size - offset)
            val presentationUs = offset * 1_000_000L / SAMPLE_RATE
            if (count <= 0) {
                codec.queueInputBuffer(index, 0, 0, presentationUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                inputDone = true
            } else {
                buffer.order(ByteOrder.nativeOrder()).asShortBuffer().put(samples, offset, count)
                codec.queueInputBuffer(index, 0, count * 2, presentationUs, 0)
                offset += count
            }
        }

        private fun drain() {
            val index = codec.dequeueOutputBuffer(info, TIMEOUT_US)
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                track = muxer.addTrack(codec.outputFormat)
                muxer.start()
            } else if (index >= 0) {
                val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                if (!isConfig && info.size > 0) {
                    muxer.writeSampleData(track, requireNotNull(codec.getOutputBuffer(index)), info)
                }
                codec.releaseOutputBuffer(index, false)
                outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
            }
        }
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val PITCH_HZ = 152.0
        const val TIMEOUT_US = 10_000L
    }
}
