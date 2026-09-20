// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe

private fun argb(alpha: Int, red: Int, green: Int, blue: Int) = (alpha shl 24) or (red shl 16) or (green shl 8) or blue

private fun red(color: Int) = (color shr 16) and 0xFF

/**
 * Média de janela 9 (raio 4), clampeando na borda: a mesma conta documentada em [averageAround], escrita
 * de outro jeito para servir de oráculo independente. Só precisa da direção horizontal: com altura 1 a
 * passada vertical não muda nada (a linha tem um só elemento, a média dele com ele mesmo é ele mesmo).
 */
private fun referenceBoxBlur1D(values: IntArray, radius: Int, passes: Int): IntArray {
    var current = values.copyOf()
    repeat(passes) {
        current = IntArray(current.size) { i ->
            var sum = 0
            for (offset in -radius..radius) sum += current[(i + offset).coerceIn(0, current.lastIndex)]
            sum / (2 * radius + 1)
        }
    }
    return current
}

/** ADR 0013: a foto censurada é um borrão sem contorno, nunca a foto original escurecida. */
class PhotoBlurSpec : FunSpec({
    test("uma imagem de uma cor só continua exatamente igual") {
        val color = argb(255, 200, 150, 100)
        val pixels = IntArray(20 * 10) { color }

        blurPixels(pixels, 20, 10)

        pixels.toSet() shouldBe setOf(color)
    }

    test("um contorno nítido vira uma transição gradual, sem degrau") {
        val width = 20
        val pixels = IntArray(
            width * 4
        ) { i -> if (i % width < width / 2) argb(255, 0, 0, 0) else argb(255, 255, 255, 255) }

        blurPixels(pixels, width, 4)

        val row = (0 until width).map { red(pixels[it]) }
        row.zipWithNext().all { (left, right) -> left <= right } shouldBe true
        row.count { it in 1..254 } shouldBeGreaterThan width / 2
        row.zipWithNext().maxOf { (left, right) -> right - left } shouldBeLessThan 80
    }

    test("um bloco claro se espalha pelos vizinhos e perde o brilho original") {
        val size = 21
        val pixels = IntArray(size * size) { argb(255, 0, 0, 0) }
        for (y in 8..12) for (x in 8..12) pixels[y * size + x] = argb(255, 255, 255, 255)

        blurPixels(pixels, size, size)

        red(pixels[10 * size + 10]) shouldBeLessThan 255
        red(pixels[10 * size + 15]) shouldBeGreaterThan 0
        pixels.maxOf { red(it) } shouldBeLessThan 255
    }

    test("a transparência é borrada junto, canal a canal") {
        val width = 20
        val pixels = IntArray(width) { i -> if (i < width / 2) argb(0, 0, 0, 0) else argb(255, 0, 0, 0) }

        blurPixels(pixels, width, 1)

        val alphas = pixels.map { (it ushr 24) and 0xFF }
        alphas.count { it in 1..254 } shouldBeGreaterThan 0
    }

    test("as 3 passadas de raio 4 seguem a média de janela documentada, canal a canal") {
        val width = 41
        val radius = 4
        val passes = 3
        val center = width / 2
        fun spike(peak: Int) = IntArray(width) { i -> if (i == center) peak else 0 }
        val alpha = referenceBoxBlur1D(spike(200), radius, passes)
        val redChannel = referenceBoxBlur1D(spike(150), radius, passes)
        val green = referenceBoxBlur1D(spike(100), radius, passes)
        val blue = referenceBoxBlur1D(spike(90), radius, passes)
        val expected = IntArray(width) { i -> argb(alpha[i], redChannel[i], green[i], blue[i]) }

        val pixels = IntArray(width) { i -> if (i == center) argb(200, 150, 100, 90) else argb(0, 0, 0, 0) }
        blurPixels(pixels, width, 1)

        pixels shouldBe expected
    }

    test("uma borda vertical também vira uma transição gradual, sem degrau") {
        val height = 20
        val width = 4
        val pixels = IntArray(width * height) { i ->
            if (i / width < height / 2) argb(255, 0, 0, 0) else argb(255, 255, 255, 255)
        }

        blurPixels(pixels, width, height)

        val column = (0 until height).map { y -> red(pixels[y * width]) }
        column.zipWithNext().all { (top, bottom) -> top <= bottom } shouldBe true
        column.count { it in 1..254 } shouldBeGreaterThan height / 2
    }
})
