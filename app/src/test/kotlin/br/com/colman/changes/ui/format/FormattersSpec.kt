// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.ui.format

import br.com.colman.changes.core.model.RecordedTime
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toJavaLocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** Seção 9: número digitado não depende do locale do aparelho. */
class FormattersSpec : FunSpec({
    test("a single comma or dot is the decimal separator") {
        Formatters.parseNumber("81,5") shouldBe 81.5
        Formatters.parseNumber("81.5") shouldBe 81.5
        Formatters.parseNumber(" 0,25 ") shouldBe 0.25
        Formatters.parseNumber("-2.5") shouldBe -2.5
        Formatters.parseNumber("70") shouldBe 70.0
    }

    test("a separator right at the start of the text is still the decimal separator") {
        Formatters.parseNumber(",5") shouldBe 0.5
        Formatters.parseNumber(".5") shouldBe 0.5
    }

    test("with both separators the last one is decimal; a repeated one groups thousands") {
        Formatters.parseNumber("1.234,5") shouldBe 1234.5
        Formatters.parseNumber("1,234.5") shouldBe 1234.5
        Formatters.parseNumber("1.000.000") shouldBe 1_000_000.0
        Formatters.parseNumber("1 000") shouldBe 1000.0
        Formatters.parseNumber(",1.5") shouldBe 1.5
    }

    test("anything that is not a finite number is null") {
        listOf(
            "",
            " ",
            "abc",
            ",",
            "NaN",
            "Infinity",
            "1.2.3,4,5",
            ".1,2,3"
        ).forEach { Formatters.parseNumber(it).shouldBeNull() }
    }

    test("number rounds to the requested fraction digits and drops a whole number's decimals") {
        val locale = Locale.forLanguageTag("en-US")

        Formatters.number(1.23456, 2, locale) shouldBe "1.23"
        Formatters.number(5.0, 2, locale) shouldBe "5"
    }

    test("date, shortDate, time and dateTime round-trip through the locale's own localized style") {
        val locale = Locale.forLanguageTag("pt-BR")
        val date = LocalDate(2026, 3, 10)
        val time = LocalTime(14, 5)
        val dateTime = LocalDateTime(date, time)

        java.time.LocalDate.parse(
            Formatters.date(date, locale),
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
        ) shouldBe date.toJavaLocalDate()

        java.time.LocalDate.parse(
            Formatters.shortDate(date, locale),
            DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale)
        ) shouldBe date.toJavaLocalDate()

        java.time.LocalTime.parse(
            Formatters.time(time, locale),
            DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
        ) shouldBe time.toJavaLocalTime()

        java.time.LocalDateTime.parse(
            Formatters.dateTime(dateTime, locale),
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
        ) shouldBe dateTime.toJavaLocalDateTime()
    }

    test("recorded shows the hour that was saved, offset included, not the current device zone") {
        val locale = Locale.forLanguageTag("pt-BR")
        val recorded = RecordedTime.fromDb(
            Instant.parse("2026-03-10T23:30:00Z").toEpochMilliseconds(),
            offsetSeconds = -3.hours.inWholeSeconds
        )

        Formatters.recorded(recorded, locale) shouldBe Formatters.dateTime(LocalDateTime(2026, 3, 10, 20, 30), locale)
    }
})
