// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.platform.reminders

import br.com.colman.changes.core.model.TimeZoneProvider
import br.com.colman.changes.platform.SettingsStore
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Lembrete diário opcional de check-in (Seção 7.7): um por dia, no horário escolhido, só se ligado.
 * Doses e eventos entram por [others] (planejador do calendário).
 */
class SettingsUpcomingReminders(
    private val settings: SettingsStore,
    private val timeZones: TimeZoneProvider,
    private val others: UpcomingReminders = UpcomingReminders { emptyList() },
) : UpcomingReminders {
    override suspend fun upcoming(now: Instant): List<ScheduledReminder> = moodReminders(now) + others.upcoming(now)

    private fun moodReminders(now: Instant): List<ScheduledReminder> {
        val current = settings.settings.value
        if (!current.moodReminderEnabled) return emptyList()
        val zone = timeZones.current()
        val today = now.toLocalDateTime(zone).date
        val time = LocalTime(
            current.moodReminderMinutes / MINUTES_PER_HOUR,
            current.moodReminderMinutes % MINUTES_PER_HOUR
        )
        return (0 until DAYS_AHEAD).map { offset ->
            val date = today.plus(offset, DateTimeUnit.DAY)
            ScheduledReminder(
                "mood:${date.toEpochDays()}",
                LocalDateTime(date, time).toInstant(zone),
                ReminderKind.MOOD
            )
        }.filter { it.at > now }
    }

    private companion object {
        const val MINUTES_PER_HOUR = 60
        const val DAYS_AHEAD = 3
    }
}
