// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.platform

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin

private const val RATE = 44_100

/** Pedaço de áudio sintético: [seconds] de uma voz com fundamental [pitchHz] e [amplitude] de pico (0 a 1). */
private data class Segment(val pitchHz: Double, val seconds: Double, val amplitude: Double = 0.5)

/**
 * Voz sintética: fundamental mais 3 harmônicos decrescentes, como numa vogal sustentada. [secondHarmonic]
 * controla o peso do segundo harmônico, que em vozes graves às vezes passa o da fundamental.
 */
private fun voice(vararg segments: Segment, sampleRate: Int = RATE, secondHarmonic: Double = 0.5): ShortArray {
    val weights = doubleArrayOf(1.0, secondHarmonic, 0.33, 0.25)
    val total = weights.sum()
    val output = ArrayList<Short>()
    for (segment in segments) {
        val count = (segment.seconds * sampleRate).toInt()
        for (n in 0 until count) {
            var value = 0.0
            for (h in weights.indices) value += weights[h] * sin(2 * PI * segment.pitchHz * (h + 1) * n / sampleRate)
            output.add((value / total * segment.amplitude * Short.MAX_VALUE).toInt().toShort())
        }
    }
    return output.toShortArray()
}

private fun silence(seconds: Double, sampleRate: Int = RATE) = ShortArray((seconds * sampleRate).toInt())

private fun noise(seconds: Double, amplitude: Double): ShortArray {
    val random = Random(42)
    return ShortArray((seconds * RATE).toInt()) {
        ((random.nextDouble() * 2 - 1) * amplitude * Short.MAX_VALUE).toInt().toShort()
    }
}

/** ADR 0014: mediana da frequência fundamental de uma gravação, para o gráfico da voz. */
class VoicePitchSpec : FunSpec({
    test("acha a fundamental de vozes graves e agudas, com fração de Hz") {
        for (pitch in listOf(85.0, 112.5, 131.0, 178.3, 215.0, 262.7, 340.0)) {
            medianPitchHz(voice(Segment(pitch, 1.0)), RATE).shouldNotBeNull() shouldBe (pitch plusOrMinus 0.3)
        }
    }

    test("a faixa de busca vai até 60 Hz: no limite tem estimativa, abaixo dele não") {
        // 11 025 / 184: o período cai exatamente no maior atraso da busca.
        val lowestPitch = 11_025.0 / 184
        medianPitchHz(voice(Segment(lowestPitch, 1.0)), RATE).shouldNotBeNull() shouldBe (lowestPitch plusOrMinus 0.3)
        medianPitchHz(voice(Segment(59.0, 1.0)), RATE).shouldBeNull()
    }

    test("a mesma voz dá a mesma estimativa a 44,1 kHz, 48 kHz, 16 kHz e 8 kHz") {
        for (rate in listOf(44_100, 48_000, 16_000, 8_000)) {
            medianPitchHz(voice(Segment(147.0, 1.0), sampleRate = rate), rate)
                .shouldNotBeNull() shouldBe (147.0 plusOrMinus 0.3)
        }
    }

    test("um segundo harmônico mais forte que a fundamental não dobra a estimativa") {
        medianPitchHz(voice(Segment(118.0, 1.0), secondHarmonic = 1.6), RATE)
            .shouldNotBeNull() shouldBe (118.0 plusOrMinus 0.5)
    }

    test("silêncio antes e depois da frase não muda a estimativa") {
        val audio = silence(1.0) + voice(Segment(190.0, 0.5)) + silence(2.0)

        medianPitchHz(audio, RATE).shouldNotBeNull() shouldBe (190.0 plusOrMinus 0.3)
    }

    test("um som baixo de fundo, abaixo de 10% da amplitude da voz, fica fora da mediana") {
        val audio = voice(Segment(120.0, 1.0, amplitude = 0.8)) + voice(Segment(300.0, 2.0, amplitude = 0.07))

        medianPitchHz(audio, RATE).shouldNotBeNull() shouldBe (120.0 plusOrMinus 0.3)
    }

    test("um som de fundo pouco acima de 10% da amplitude da voz entra na mediana") {
        val audio = voice(Segment(120.0, 1.0, amplitude = 0.8)) + voice(Segment(300.0, 2.0, amplitude = 0.09))

        medianPitchHz(audio, RATE).shouldNotBeNull() shouldBe (300.0 plusOrMinus 0.3)
    }

    test("a mediana fica com o trecho mais longo da gravação") {
        medianPitchHz(voice(Segment(100.0, 2.0), Segment(200.0, 1.0)), RATE)
            .shouldNotBeNull() shouldBe (100.0 plusOrMinus 0.3)
        medianPitchHz(voice(Segment(100.0, 1.0), Segment(200.0, 2.0)), RATE)
            .shouldNotBeNull() shouldBe (200.0 plusOrMinus 0.3)
    }

    test("silêncio puro não tem estimativa") {
        medianPitchHz(silence(3.0), RATE).shouldBeNull()
    }

    test("um sinal constante, sem oscilação, não tem estimativa") {
        medianPitchHz(ShortArray(RATE) { 1_000 }, RATE).shouldBeNull()
    }

    test("ruído sem tom não tem estimativa") {
        medianPitchHz(noise(2.0, amplitude = 0.5), RATE).shouldBeNull()
    }

    test("um áudio mais curto que um quadro não tem estimativa") {
        medianPitchHz(voice(Segment(150.0, 0.02)), RATE).shouldBeNull()
        medianPitchHz(ShortArray(0), RATE).shouldBeNull()
    }

    test("menos de 10 quadros com voz não dá estimativa; 10 quadros dão") {
        // Quadro de 369 amostras a 11 025 Hz (1 476 a 44,1 kHz), um a cada 110 (440 a 44,1 kHz).
        val tenFrames = 1_476 + 9 * 440
        val nineFrames = tenFrames - 1
        val tone = voice(Segment(200.0, 1.0))
        medianPitchHz(tone.copyOf(tenFrames), RATE).shouldNotBeNull() shouldBe (200.0 plusOrMinus 0.5)
        medianPitchHz(tone.copyOf(nineFrames), RATE).shouldBeNull()
    }

    test("mediana de uma quantidade ímpar é o valor do meio; de uma par, a média dos dois do meio") {
        median(listOf(300.0, 100.0, 200.0)) shouldBe 200.0
        median(listOf(400.0, 100.0, 300.0, 200.0)) shouldBe 250.0
        median(listOf(7.0)) shouldBe 7.0
    }
})
