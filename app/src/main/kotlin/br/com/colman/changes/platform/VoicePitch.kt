// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.platform

import kotlin.math.ceil

/** Taxa a que o áudio é reduzido antes da análise: de sobra para a fundamental da fala. */
private const val ANALYSIS_RATE = 11_025

/** Faixa de busca da fundamental (ADR 0014): cobre a fala adulta antes e depois da testosterona. */
private const val MIN_PITCH_HZ = 60.0
private const val MAX_PITCH_HZ = 400.0

/** Um quadro a cada 10 ms. */
private const val HOP_SECONDS = 0.01

/** Limiar absoluto do YIN: abaixo dele, o atraso é tomado como período da voz. */
private const val YIN_THRESHOLD = 0.15

/** Quadro com menos de 1% da energia do quadro mais forte (10% da amplitude) é silêncio ou ruído de fundo. */
private const val SILENCE_ENERGY_RATIO = 0.01

/** Menos de 10 quadros com voz (0,1 s) não dá estimativa. */
private const val MIN_VOICED_FRAMES = 10

/**
 * Mediana da frequência fundamental de uma gravação de fala, em Hz (ADR 0014); `null` quando não há
 * voz suficiente. [samples] é PCM de 16 bits mono a [sampleRate] Hz.
 *
 * O áudio é reduzido a cerca de [ANALYSIS_RATE] Hz pela média de blocos, cortado em quadros de 10 ms,
 * e cada quadro com energia acima de [SILENCE_ENERGY_RATIO] do mais forte passa pelo YIN (de Cheveigné
 * e Kawahara, 2002): diferença ao quadrado, normalização pela média acumulada, primeiro atraso abaixo de
 * [YIN_THRESHOLD] descido até o mínimo local, refinado por interpolação parabólica. A mediana dos quadros
 * com voz ignora os poucos quadros que erram a oitava.
 */
internal fun medianPitchHz(samples: ShortArray, sampleRate: Int): Double? {
    val factor = (sampleRate / ANALYSIS_RATE).coerceAtLeast(1)
    val rate = sampleRate.toDouble() / factor
    val signal = downsample(samples, factor)
    val minLag = (rate / MAX_PITCH_HZ).toInt()
    val maxLag = ceil(rate / MIN_PITCH_HZ).toInt()
    // A janela cobre o período mais longo; o quadro ainda precisa do maior atraso e de um vizinho para a parábola.
    val window = maxLag
    val frameLength = window + maxLag + 1
    val hop = (rate * HOP_SECONDS).toInt()
    val starts = frameStarts(signal.size, frameLength, hop)
    val energies = DoubleArray(starts.size) { energy(signal, starts[it], frameLength) }
    val gate = (energies.maxOrNull() ?: 0.0) * SILENCE_ENERGY_RATIO
    val pitches = ArrayList<Double>()
    for (i in starts.indices) {
        val lag = if (energies[i] > gate) yinLag(signal, starts[i], window, minLag, maxLag) else null
        if (lag != null) pitches.add(rate / lag)
    }
    return if (pitches.size >= MIN_VOICED_FRAMES) median(pitches) else null
}

/**
 * Soma de cada bloco de [factor] amostras: um filtro passa-baixa simples antes de reduzir a taxa. Sem
 * dividir pelo tamanho do bloco: o YIN normaliza e o corte de silêncio é relativo, então a escala não
 * importa. Sobra incompleta no fim é descartada.
 */
private fun downsample(samples: ShortArray, factor: Int): DoubleArray {
    val output = DoubleArray(samples.size / factor)
    for (i in output.indices) {
        var sum = 0.0
        for (j in 0 until factor) sum += samples[i * factor + j]
        output[i] = sum
    }
    return output
}

/** Início de cada quadro inteiro que cabe no sinal; nenhum quando o sinal é mais curto que um quadro. */
private fun frameStarts(size: Int, frameLength: Int, hop: Int): IntArray {
    val count = (Math.floorDiv(size - frameLength, hop) + 1).coerceAtLeast(0)
    return IntArray(count) { it * hop }
}

private fun energy(signal: DoubleArray, start: Int, length: Int): Double {
    var sum = 0.0
    for (i in start until start + length) sum += signal[i] * signal[i]
    return sum
}

/**
 * Período do quadro em amostras, com fração. `null` quando nenhum atraso da faixa fica abaixo do limiar
 * (quadro sem voz) ou quando o vale continua depois do maior atraso (voz abaixo de [MIN_PITCH_HZ]).
 */
private fun yinLag(signal: DoubleArray, start: Int, window: Int, minLag: Int, maxLag: Int): Double? {
    val normalized = DoubleArray(maxLag + 2)
    var cumulative = 0.0
    for (lag in 1..maxLag + 1) {
        var difference = 0.0
        for (j in start until start + window) {
            val delta = signal[j] - signal[j + lag]
            difference += delta * delta
        }
        cumulative += difference
        normalized[lag] = if (cumulative > 0.0) difference * lag / cumulative else 1.0
    }
    var lag = minLag
    while (lag <= maxLag && normalized[lag] >= YIN_THRESHOLD) lag++
    while (lag <= maxLag && normalized[lag + 1] < normalized[lag]) lag++
    return if (lag <= maxLag) parabolicMinimum(normalized, lag) else null
}

/**
 * Vértice da parábola pelos pontos vizinhos de [lag]. Dentro da faixa de busca o ponto é mínimo local (veio
 * de cima do limiar ou desceu até ali), então o ajuste fica dentro de meia amostra.
 */
private fun parabolicMinimum(values: DoubleArray, lag: Int): Double {
    val before = values[lag - 1]
    val at = values[lag]
    val after = values[lag + 1]
    return lag + (before - after) / (2 * (before - 2 * at + after))
}

internal fun median(values: List<Double>): Double {
    val sorted = values.sorted()
    val middle = sorted.size / 2
    return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
}
