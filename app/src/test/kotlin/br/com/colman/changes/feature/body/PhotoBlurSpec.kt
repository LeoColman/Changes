// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe

private fun argb(alpha: Int, red: Int, green: Int, blue: Int) = (alpha shl 24) or (red shl 16) or (green shl 8) or blue

private fun red(color: Int) = (color shr 16) and 0xFF

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
})
