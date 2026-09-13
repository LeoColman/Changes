// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlin.uuid.Uuid

public enum class ReminderSource { DOSE, EVENT }

/** Um lembrete calculado. [key] é estável entre recálculos: o app usa para cancelar e reagendar. */
public data class PlannedReminder(val key: String, val at: Instant, val source: ReminderSource)

/**
 * Instantes de lembrete (Seção 7.9, ADR 0010). A série é sempre calculada em datas locais e só
 * vira instante no fuso atual, como o [DoseSchedule]. Nada no passado é devolvido.
 */
public object ReminderPlanner {
    /** Doses previstas com hora marcada, de [from] até [from] + [days], de regimes ativos. */
    public fun doses(
        regimens: List<Regimen>,
        from: LocalDate,
        days: Int,
        zone: TimeZone,
        now: Instant,
    ): List<PlannedReminder> {
        val to = from.plus(days, DateTimeUnit.DAY)
        val result = mutableListOf<PlannedReminder>()
        for (regimen in regimens) result += doseReminders(regimen, from, to, zone, now)
        return result
    }

    /**
     * Eventos com lembrete. Evento de dia inteiro lembra à meia-noite local do dia menos a antecedência;
     * evento com hora usa a hora local gravada, repetida em cada ocorrência da recorrência.
     */
    public fun events(
        events: List<CalendarEvent>,
        from: LocalDate,
        to: LocalDate,
        zone: TimeZone,
        now: Instant,
    ): List<PlannedReminder> {
        val result = mutableListOf<PlannedReminder>()
        for (event in events) result += eventReminders(event, from, to, zone, now)
        return result
    }

    private fun doseReminders(
        regimen: Regimen,
        from: LocalDate,
        to: LocalDate,
        zone: TimeZone,
        now: Instant,
    ): List<PlannedReminder> {
        val time = regimen.timeOfDay
        val result = mutableListOf<PlannedReminder>()
        if (regimen.isActive && time != null) {
            for (date in DoseSchedule.occurrences(regimen, from, to)) {
                val at = LocalDateTime(date, time).toInstant(zone)
                if (at > now) result += PlannedReminder(key(DOSE_PREFIX, regimen.id, date), at, ReminderSource.DOSE)
            }
        }
        return result
    }

    private fun eventReminders(
        event: CalendarEvent,
        from: LocalDate,
        to: LocalDate,
        zone: TimeZone,
        now: Instant,
    ): List<PlannedReminder> {
        val minutes = event.reminderMinutesBefore
        val result = mutableListOf<PlannedReminder>()
        if (minutes != null && event.completedAt == null) {
            val start = event.start.localDateTime
            for (date in occurrenceDates(event, start.date, from, to)) {
                val at = LocalDateTime(date, start.time).toInstant(zone) - minutes.minutes
                if (at > now) result += PlannedReminder(key(EVENT_PREFIX, event.id, date), at, ReminderSource.EVENT)
            }
        }
        return result
    }

    private fun occurrenceDates(
        event: CalendarEvent,
        start: LocalDate,
        from: LocalDate,
        to: LocalDate,
    ): List<LocalDate> {
        val rule = event.recurrence
        return if (rule != null) {
            rule.occurrences(start, from, to)
        } else if (start in from..to) {
            listOf(start)
        } else {
            emptyList()
        }
    }

    private fun key(prefix: String, id: Uuid, date: LocalDate): String = "$prefix:$id:${date.toEpochDays()}"

    private const val DOSE_PREFIX = "dose"
    private const val EVENT_PREFIX = "event"
}
