// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import android.graphics.Bitmap

/** Largura da cópia borrada (ADR 0013): o borrão é proporcional à foto, em qualquer tamanho. */
private const val BLUR_WIDTH = 20
private const val BLUR_RADIUS = 4
private const val BLUR_PASSES = 3
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8
private const val CHANNEL_MASK = 0xFF

/**
 * Cópia fortemente borrada de [source] para uma foto censurada (ADR 0013): reduz a [BLUR_WIDTH] pixels
 * de largura e passa [BLUR_PASSES] vezes um box blur nas duas direções, o que aproxima um blur
 * gaussiano largo. Ampliada com filtro bilinear, vira um borrão liso, sem contorno nem detalhe.
 * Funciona em qualquer Android (o `Modifier.blur` só existe a partir do 12) e nunca devolve os pixels
 * originais.
 */
internal fun blurredCopy(source: Bitmap): Bitmap {
    val width = BLUR_WIDTH.coerceAtMost(source.width).coerceAtLeast(1)
    val height = (source.height.toLong() * width / source.width).toInt().coerceAtLeast(1)
    val small = Bitmap.createScaledBitmap(source, width, height, true)
    val pixels = IntArray(width * height)
    small.getPixels(pixels, 0, width, 0, 0, width, height)
    blurPixels(pixels, width, height)
    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
}

/** Borra [pixels] (ARGB, linha a linha) no lugar: [BLUR_PASSES] passadas de box blur em cada direção. */
internal fun blurPixels(pixels: IntArray, width: Int, height: Int) {
    repeat(BLUR_PASSES) {
        boxBlur(pixels, width, height, horizontal = true)
        boxBlur(pixels, width, height, horizontal = false)
    }
}

private fun boxBlur(pixels: IntArray, width: Int, height: Int, horizontal: Boolean) {
    val lines = if (horizontal) height else width
    val length = if (horizontal) width else height
    val line = IntArray(length)
    for (lineIndex in 0 until lines) {
        for (i in 0 until length) line[i] = pixels[indexOf(lineIndex, i, width, horizontal)]
        for (i in 0 until length) pixels[indexOf(lineIndex, i, width, horizontal)] = averageAround(line, i)
    }
}

private fun indexOf(line: Int, position: Int, width: Int, horizontal: Boolean): Int =
    if (horizontal) line * width + position else position * width + line

/** Média de cada canal na janela de raio [BLUR_RADIUS], repetindo o pixel da borda fora da imagem. */
private fun averageAround(line: IntArray, center: Int): Int {
    var alpha = 0
    var red = 0
    var green = 0
    var blue = 0
    for (offset in -BLUR_RADIUS..BLUR_RADIUS) {
        val color = line[(center + offset).coerceIn(0, line.lastIndex)]
        alpha += (color ushr ALPHA_SHIFT) and CHANNEL_MASK
        red += (color shr RED_SHIFT) and CHANNEL_MASK
        green += (color shr GREEN_SHIFT) and CHANNEL_MASK
        blue += color and CHANNEL_MASK
    }
    val count = 2 * BLUR_RADIUS + 1
    return ((alpha / count) shl ALPHA_SHIFT) or ((red / count) shl RED_SHIFT) or
        ((green / count) shl GREEN_SHIFT) or (blue / count)
}
