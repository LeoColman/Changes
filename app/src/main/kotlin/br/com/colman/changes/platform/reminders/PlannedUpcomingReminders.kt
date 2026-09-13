// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.platform.reminders

import br.com.colman.changes.core.data.CalendarRepository
import br.com.colman.changes.core.data.RegimenRepository
import br.com.colman.changes.core.model.PlannedReminder
import br.com.colman.changes.core.model.ReminderPlanner
import br.com.colman.changes.core.model.ReminderSource
import br.com.colman.changes.core.model.TimeZoneProvider
import br.com.colman.changes.platform.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Doses de regimes ativos com hora marcada e eventos com lembrete (Seção 7.9), calculados pelo
 * [ReminderPlanner] do :core no fuso atual. Doses só entram se os lembretes de dose estiverem ligados.
 * A janela é curta de propósito: o que ficar além entra nas sincronizações seguintes.
 */
class PlannedUpcomingReminders(
    private val regimens: RegimenRepository,
    private val calendar: CalendarRepository,
    private val settings: SettingsStore,
    private val timeZones: TimeZoneProvider,
) : UpcomingReminders {
    override suspend fun upcoming(now: Instant): List<ScheduledReminder> {
        val zone = timeZones.current()
        val today = now.toLocalDateTime(zone).date
        val doses = if (settings.settings.value.doseRemindersEnabled) {
            ReminderPlanner.doses(regimens.observeActive().first(), today, DOSE_DAYS, zone, now)
        } else {
            emptyList()
        }
        val eventsUntil = today.plus(EVENT_DAYS, DateTimeUnit.DAY)
        val events = ReminderPlanner.events(calendar.observeWithReminders().first(), today, eventsUntil, zone, now)
        return (doses + events).map(::scheduled)
    }

    private fun scheduled(planned: PlannedReminder): ScheduledReminder {
        val kind = if (planned.source == ReminderSource.DOSE) ReminderKind.DOSE else ReminderKind.EVENT
        return ScheduledReminder(planned.key, planned.at, kind)
    }

    private companion object {
        const val DOSE_DAYS = 14
        const val EVENT_DAYS = 60
    }
}
