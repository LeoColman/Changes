// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.number
import kotlinx.datetime.plus

/**
 * Subconjunto de RRULE (RFC 5545) usado no calendário e em regimes customizados:
 * `FREQ` (DAILY, WEEKLY, MONTHLY, YEARLY), `INTERVAL`, `BYDAY` (só com WEEKLY), `UNTIL` (data,
 * inclusiva) e `COUNT`. `UNTIL` e `COUNT` são mutuamente exclusivos, como na RFC.
 *
 * Datas inexistentes são puladas, como na RFC: dia 31 mensal só cai em meses de 31 dias e 29 de
 * fevereiro anual só em anos bissextos.
 */
public data class RecurrenceRule(
    val frequency: Frequency,
    val interval: Int = 1,
    val byDay: Set<DayOfWeek> = emptySet(),
    val until: LocalDate? = null,
    val count: Int? = null,
) {
    public enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

    /** Texto RRULE canônico (ordem fixa de chaves, BYDAY em ordem ISO). */
    public fun format(): String = buildList {
        add("FREQ=$frequency")
        if (interval != 1) add("INTERVAL=$interval")
        if (byDay.isNotEmpty()) {
            add("BYDAY=" + byDay.sortedBy { it.isoDayNumber }.joinToString(",") { DAY_CODES.getValue(it) })
        }
        until?.let { add("UNTIL=" + formatDate(it)) }
        count?.let { add("COUNT=$it") }
    }.joinToString(";")

    /**
     * Ocorrências em `[from, to]` (inclusive) de uma série que começa em [start]. [start] é sempre a
     * primeira ocorrência quando cai num dia válido da regra. COUNT conta a partir de [start].
     */
    public fun occurrences(start: LocalDate, from: LocalDate, to: LocalDate): List<LocalDate> {
        // Sem retorno antecipado para janela vazia: as séries já saem vazias quando limit < start.
        val limit = if (until == null) to else minOf(until, to)
        val series = series(start, limit)
        val counted = if (count != null) series.take(count) else series
        val result = mutableListOf<LocalDate>()
        for (date in counted) {
            if (date >= from) result += date
        }
        return result
    }

    // Laços simples, sem `sequence {}`: o builder vira máquina de estados de corrotina e gera
    // mutantes impossíveis de matar. Todas as séries param em [limit], que é finito.
    private fun series(start: LocalDate, limit: LocalDate): List<LocalDate> = when (frequency) {
        Frequency.DAILY -> stepping(start, limit, interval.toLong())
        Frequency.WEEKLY -> if (byDay.isEmpty()) stepping(start, limit, WEEK * interval) else weekly(start, limit)
        Frequency.MONTHLY -> monthly(start, limit)
        Frequency.YEARLY -> yearly(start, limit)
    }

    private fun stepping(start: LocalDate, limit: LocalDate, stepDays: Long): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        var date = start
        while (date <= limit) {
            result += date
            date = date.plus(stepDays, DateTimeUnit.DAY)
        }
        return result
    }

    private fun weekly(start: LocalDate, limit: LocalDate): List<LocalDate> {
        val days = byDay.sortedBy { it.isoDayNumber }
        val result = mutableListOf<LocalDate>()
        var weekStart = mondayOf(start)
        while (weekStart <= limit) {
            for (day in days) {
                val date = weekStart.plus(day.isoDayNumber - 1, DateTimeUnit.DAY)
                if (start <= date && date <= limit) result += date
            }
            weekStart = weekStart.plus(WEEK * interval, DateTimeUnit.DAY)
        }
        return result
    }

    private fun monthly(start: LocalDate, limit: LocalDate): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        var monthIndex = start.year * MONTHS_PER_YEAR + start.month.number - 1
        while (LocalDate(monthIndex / MONTHS_PER_YEAR, monthIndex % MONTHS_PER_YEAR + 1, 1) <= limit) {
            val date = validDate(monthIndex / MONTHS_PER_YEAR, monthIndex % MONTHS_PER_YEAR + 1, start.day)
            if (date != null && date <= limit) result += date
            monthIndex += interval
        }
        return result
    }

    private fun yearly(start: LocalDate, limit: LocalDate): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        var year = start.year
        while (LocalDate(year, 1, 1) <= limit) {
            val date = validDate(year, start.month.number, start.day)
            if (date != null && date <= limit) result += date
            year += interval
        }
        return result
    }

    public companion object {
        private const val WEEK = 7L
        private const val MONTHS_PER_YEAR = 12
        private const val MAX_INTERVAL = 999
        private const val MAX_COUNT = 9999
        private val DAY_CODES = mapOf(
            DayOfWeek.MONDAY to "MO",
            DayOfWeek.TUESDAY to "TU",
            DayOfWeek.WEDNESDAY to "WE",
            DayOfWeek.THURSDAY to "TH",
            DayOfWeek.FRIDAY to "FR",
            DayOfWeek.SATURDAY to "SA",
            DayOfWeek.SUNDAY to "SU",
        )
        private val CODE_DAYS = DAY_CODES.entries.associate { (day, code) -> code to day }

        public fun parse(text: String): Result<RecurrenceRule> {
            val rule = fieldsOf(text)?.let(::ruleFrom) ?: return invalid()
            return validated(rule)
        }

        private fun fieldsOf(text: String): Fields? {
            val parts = text.trim().split(';').filter { it.isNotBlank() }.map { it.split('=', limit = 2) }
            if (parts.any { it.size != 2 }) return null
            val fields = parts.associate { (key, value) -> key.trim().uppercase() to value.trim().uppercase() }
            val complete = fields.size == parts.size && KEYS.containsAll(fields.keys)
            return if (complete) Fields(fields) else null
        }

        private fun ruleFrom(fields: Fields): RecurrenceRule? {
            val frequency = fields.optional("FREQ") { value -> Frequency.entries.firstOrNull { it.name == value } }
            val interval = fields.optional("INTERVAL") { it.toIntOrNull() }
            val byDay = fields.optional("BYDAY", ::parseDays)
            val until = fields.optional("UNTIL", ::parseDate)
            val count = fields.optional("COUNT") { it.toIntOrNull() }
            return if (frequency != null && fields.allParsed) {
                RecurrenceRule(frequency, interval ?: 1, byDay ?: emptySet(), until, count)
            } else {
                null
            }
        }

        /** Campos de uma RRULE. Um campo presente que não se deixa ler marca o conjunto como inválido. */
        private class Fields(private val values: Map<String, String>) {
            var allParsed: Boolean = true
                private set

            fun <T> optional(key: String, parse: (String) -> T?): T? {
                val raw = values[key] ?: return null
                return parse(raw).also { if (it == null) allParsed = false }
            }
        }

        /** Regras que o subconjunto não aceita viram erro de domínio, nunca exceção. */
        public fun validated(rule: RecurrenceRule): Result<RecurrenceRule> {
            val ok = rule.interval in 1..MAX_INTERVAL &&
                (rule.count == null || rule.count in 1..MAX_COUNT) &&
                (rule.count == null || rule.until == null) &&
                (rule.byDay.isEmpty() || rule.frequency == Frequency.WEEKLY)
            return if (ok) rule.asSuccess() else invalid()
        }

        private val KEYS = setOf("FREQ", "INTERVAL", "BYDAY", "UNTIL", "COUNT")

        private fun invalid(): Result<Nothing> =
            DomainError.Invalid("recurrenceRule", DomainError.Reason.OUT_OF_RANGE).asFailure()

        private fun parseDays(text: String): Set<DayOfWeek>? {
            val codes = text.split(',').map { it.trim() }
            val days = codes.mapNotNull { CODE_DAYS[it] }
            return if (days.isNotEmpty() && days.size == codes.size) days.toSet() else null
        }

        private fun parseDate(text: String): LocalDate? {
            val digits = text.take(DATE_LENGTH)
            if (digits.length != DATE_LENGTH || !digits.all { it.isDigit() }) return null
            return validDate(
                digits.substring(0, YEAR_DIGITS).toInt(),
                digits.substring(YEAR_DIGITS, YEAR_DIGITS + FIELD_DIGITS).toInt(),
                digits.substring(YEAR_DIGITS + FIELD_DIGITS, DATE_LENGTH).toInt(),
            )
        }

        private const val YEAR_DIGITS = 4
        private const val FIELD_DIGITS = 2
        private const val DATE_LENGTH = YEAR_DIGITS + 2 * FIELD_DIGITS

        // Sem String.format: em alguns locales ele produz dígitos não ASCII.
        private fun formatDate(date: LocalDate): String =
            date.year.toString().padStart(YEAR_DIGITS, '0') +
                date.month.number.toString().padStart(FIELD_DIGITS, '0') +
                date.day.toString().padStart(FIELD_DIGITS, '0')

        internal fun mondayOf(date: LocalDate): LocalDate = date.minus(
            date.dayOfWeek.isoDayNumber - 1,
            DateTimeUnit.DAY
        )

        internal fun validDate(year: Int, month: Int, day: Int): LocalDate? =
            runCatching { LocalDate(year, month, day) }.getOrNull()
    }
}
