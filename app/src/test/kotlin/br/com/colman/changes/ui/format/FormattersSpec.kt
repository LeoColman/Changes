// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.ui.format

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/** Seção 9: número digitado não depende do locale do aparelho. */
class FormattersSpec : FunSpec({
    test("a single comma or dot is the decimal separator") {
        Formatters.parseNumber("81,5") shouldBe 81.5
        Formatters.parseNumber("81.5") shouldBe 81.5
        Formatters.parseNumber(" 0,25 ") shouldBe 0.25
        Formatters.parseNumber("-2.5") shouldBe -2.5
        Formatters.parseNumber("70") shouldBe 70.0
    }

    test("with both separators the last one is decimal; a repeated one groups thousands") {
        Formatters.parseNumber("1.234,5") shouldBe 1234.5
        Formatters.parseNumber("1,234.5") shouldBe 1234.5
        Formatters.parseNumber("1.000.000") shouldBe 1_000_000.0
        Formatters.parseNumber("1 000") shouldBe 1000.0
    }

    test("anything that is not a finite number is null") {
        listOf(
            "",
            " ",
            "abc",
            ",",
            "NaN",
            "Infinity",
            "1.2.3,4,5"
        ).forEach { Formatters.parseNumber(it).shouldBeNull() }
    }
})
