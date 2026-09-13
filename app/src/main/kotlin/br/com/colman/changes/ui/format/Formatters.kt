// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.ui.format

import br.com.colman.changes.core.model.RecordedTime
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toJavaLocalDate
import kotlinx.datetime.toJavaLocalDateTime
import kotlinx.datetime.toJavaLocalTime
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Formatação por locale (Seção 9). Horários registrados usam a hora local gravada ([RecordedTime]),
 * nunca o fuso atual do aparelho.
 */
object Formatters {
    fun date(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(date.toJavaLocalDate())

    fun shortDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).withLocale(locale).format(date.toJavaLocalDate())

    fun time(time: LocalTime, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).format(time.toJavaLocalTime())

    fun dateTime(dateTime: LocalDateTime, locale: Locale = Locale.getDefault()): String =
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale)
            .format(dateTime.toJavaLocalDateTime())

    fun recorded(time: RecordedTime, locale: Locale = Locale.getDefault()): String =
        dateTime(time.localDateTime, locale)

    fun number(value: Double, maxFractionDigits: Int = 2, locale: Locale = Locale.getDefault()): String =
        NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = maxFractionDigits
            minimumFractionDigits = 0
        }.format(value)

    /**
     * Lê número digitado com vírgula ou ponto decimal, em qualquer locale: quem usa pt-BR digita "81,5",
     * quem usa en digita "81.5", e o resultado não pode depender do locale do aparelho. Com os dois
     * sinais, o último é o decimal e o outro separa milhares; um único sinal repetido separa milhares.
     * `null` se não for um número finito.
     */
    fun parseNumber(text: String): Double? {
        val compact = text.filterNot { it.isWhitespace() }
        val decimal = decimalSeparator(compact)
        val canonical = compact.filter { (it != ',' && it != '.') || it == decimal }.replace(decimal ?: '.', '.')
        return canonical.toDoubleOrNull()?.takeIf { java.lang.Double.isFinite(it) }
    }

    private fun decimalSeparator(text: String): Char? {
        val lastComma = text.lastIndexOf(',')
        val lastDot = text.lastIndexOf('.')
        return when {
            lastComma >= 0 && lastDot >= 0 -> if (lastComma > lastDot) ',' else '.'
            lastComma >= 0 -> ','.takeIf { text.count { it == ',' } == 1 }
            lastDot >= 0 -> '.'.takeIf { text.count { it == '.' } == 1 }
            else -> null
        }
    }
}
