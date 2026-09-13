// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.platform.reminders

import kotlin.time.Clock
import kotlin.time.Instant

enum class ReminderKind { DOSE, EVENT, MOOD }

/** Um lembrete a disparar em [at]. [key] é estável entre sincronizações (ex.: "dose:<regime>:<data>"). */
data class ScheduledReminder(val key: String, val at: Instant, val kind: ReminderKind)

/** Fonte dos próximos lembretes (doses, eventos com lembrete e o check-in diário). */
fun interface UpcomingReminders {
    suspend fun upcoming(now: Instant): List<ScheduledReminder>
}

/** Porta para o AlarmManager, para que a regra de sincronização seja testável em JVM. */
interface AlarmGateway {
    fun schedule(requestCode: Int, reminder: ScheduledReminder)

    fun cancel(requestCode: Int)
}

/** Lembra quais alarmes estão agendados, para cancelar os que deixaram de existir. */
interface ScheduledRegistry {
    fun load(): Set<Int>

    fun save(requestCodes: Set<Int>)
}

/**
 * Reconcilia os alarmes do sistema com os lembretes previstos (Seção 7.9). Roda ao abrir o app,
 * depois de cada alarme, depois de reinício do aparelho, mudança de hora ou fuso, atualização do app
 * e de todo import de backup (critério 7.9.3).
 */
class ReminderSync(
    private val source: UpcomingReminders,
    private val gateway: AlarmGateway,
    private val registry: ScheduledRegistry,
    private val clock: Clock,
) {
    suspend fun sync() {
        val now = clock.now()
        val upcoming = source.upcoming(now)
            .filter { it.at > now }
            .sortedBy { it.at }
            .take(MAX_ALARMS)
            .associateBy { requestCodeOf(it.key) }
        (registry.load() - upcoming.keys).forEach(gateway::cancel)
        upcoming.forEach { (code, reminder) -> gateway.schedule(code, reminder) }
        registry.save(upcoming.keys)
    }

    companion object {
        /** O sistema limita alarmes por app; os próximos 64 bastam, o resto entra nas sincronizações seguintes. */
        const val MAX_ALARMS: Int = 64

        fun requestCodeOf(key: String): Int = key.hashCode()
    }
}
