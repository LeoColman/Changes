// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Instant

/**
 * Doses previstas de um regime (Seção 7.1). A série é calculada em datas **locais**: o intervalo
 * entre duas doses é sempre o mesmo número de dias no calendário, atravesse ou não horário de
 * verão ou mudança de fuso. Só a conversão para instante (lembrete) usa o fuso.
 */
public object DoseSchedule {
    private const val MAX_INTERVAL_DAYS = 366
    private const val MAX_EVERY_WEEKS = 52
    private const val MAX_STEPS = 12

    public fun validate(schedule: Schedule): Result<Schedule> {
        val ok = if (schedule is Schedule.IntervalDays) {
            schedule.days in 1..MAX_INTERVAL_DAYS
        } else if (schedule is Schedule.Weekly) {
            schedule.daysOfWeek.isNotEmpty() && schedule.everyWeeks in 1..MAX_EVERY_WEEKS
        } else if (schedule is Schedule.Custom) {
            RecurrenceRule.validated(schedule.rule).isSuccess
        } else if (schedule is Schedule.Stepped) {
            steppedIsValid(schedule)
        } else {
            true
        }
        return if (ok) {
            schedule.asSuccess()
        } else {
            DomainError.Invalid("schedule", DomainError.Reason.OUT_OF_RANGE).asFailure()
        }
    }

    /** Datas previstas em `[from, to]`, limitadas por [startDate] e pela [endDate] inclusiva. */
    public fun occurrences(
        schedule: Schedule,
        startDate: LocalDate,
        endDate: LocalDate?,
        from: LocalDate,
        to: LocalDate,
    ): List<LocalDate> {
        val last = if (endDate == null) to else minOf(endDate, to)
        // Um único retorno: "devolver lista vazia" num ramo que já é vazio seria mutante equivalente.
        return if (schedule is Schedule.Stepped) {
            steppedOccurrences(schedule, startDate, from, last)
        } else {
            ruleFor(schedule)?.occurrences(startDate, from, last).orEmpty()
        }
    }

    /** Um a 12 passos; o primeiro de 0 a 366 dias, os seguintes e o `thenEvery` de 1 a 366. */
    private fun steppedIsValid(schedule: Schedule.Stepped): Boolean {
        val steps = schedule.steps
        val every = schedule.thenEvery
        return steps.size in 1..MAX_STEPS &&
            steps.first() in 0..MAX_INTERVAL_DAYS &&
            steps.drop(1).all { it in 1..MAX_INTERVAL_DAYS } &&
            (every == null || every in 1..MAX_INTERVAL_DAYS)
    }

    /**
     * Série de [Schedule.Stepped] em `[from, last]`: soma os passos a partir de [start] e, com
     * `thenEvery`, segue em progressão aritmética. A parte contínua pula direto para a primeira data
     * em ou depois de [from], sem andar dose a dose desde o início.
     */
    private fun steppedOccurrences(
        schedule: Schedule.Stepped,
        start: LocalDate,
        from: LocalDate,
        last: LocalDate,
    ): List<LocalDate> {
        val low = from.toEpochDays()
        val high = last.toEpochDays()
        val result = mutableListOf<LocalDate>()
        var day = start.toEpochDays()
        for (step in schedule.steps) {
            day += step
            if (day in low..high) result += LocalDate.fromEpochDays(day)
        }
        val every = schedule.thenEvery
        if (every != null) {
            val firstRepeat = day + every
            val skipped = maxOf(0L, Math.floorDiv(low - firstRepeat + every - 1, every.toLong()))
            var next = firstRepeat + skipped * every
            while (next <= high) {
                result += LocalDate.fromEpochDays(next)
                next += every
            }
        }
        return result
    }

    public fun occurrences(regimen: Regimen, from: LocalDate, to: LocalDate): List<LocalDate> =
        occurrences(regimen.schedule, regimen.startDate, regimen.endDate, from, to)

    /** Primeira dose prevista em ou depois de [from], procurando até [horizonDays] dias. */
    public fun next(regimen: Regimen, from: LocalDate, horizonDays: Int = 400): LocalDate? =
        occurrences(regimen, from, from.plus(horizonDays, DateTimeUnit.DAY)).firstOrNull()

    /**
     * Instante da dose prevista em [date] no fuso [zone]. `null` sem hora marcada. Num buraco de
     * horário de verão, o horário é empurrado para frente (comportamento do kotlinx-datetime).
     */
    public fun instantOf(date: LocalDate, timeOfDay: LocalTime?, zone: TimeZone): Instant? =
        timeOfDay?.let { LocalDateTime(date, it).toInstant(zone) }

    internal fun ruleFor(schedule: Schedule): RecurrenceRule? = when (schedule) {
        is Schedule.IntervalDays -> RecurrenceRule(RecurrenceRule.Frequency.DAILY, interval = schedule.days)
        is Schedule.Weekly -> RecurrenceRule(RecurrenceRule.Frequency.WEEKLY, schedule.everyWeeks, schedule.daysOfWeek)
        is Schedule.Custom -> schedule.rule
        else -> null
    }
}
