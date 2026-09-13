// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.platform.reminders

import br.com.colman.changes.platform.AppSettings
import br.com.colman.changes.platform.SettingsStore
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.seconds

/** Seção 7.9: telas só gravam; os alarmes acompanham os dados sozinhos. */
class ReminderResyncerSpec : FunSpec({
    test("syncs once at start, once per burst of settings changes, and again when an event with a reminder is saved") {
        runTest {
            val env = PlanningEnvironment(UnconfinedTestDispatcher(testScheduler))
            val settings = FakeSettingsStore()
            var syncs = 0
            val sync = ReminderSync({
                syncs++
                emptyList()
            }, NoAlarms, NoRegistry, env.clock)
            ReminderResyncer(env.regimens, env.calendar, settings, sync).start(backgroundScope)

            advanceTimeBy(1.seconds)
            runCurrent()
            syncs shouldBe 1

            settings.update { it.copy(doseRemindersEnabled = false) }
            settings.update { it.copy(moodReminderEnabled = true) }
            advanceTimeBy(1.seconds)
            runCurrent()
            syncs shouldBe 2

            env.eventAt(
                LocalDateTime(LocalDate(2026, 9, 20), LocalTime(10, 0)).toInstant(env.zones.zone),
                reminderMinutes = 15
            )
            advanceTimeBy(1.seconds)
            runCurrent()
            syncs shouldBe 3
        }
    }
})

internal class FakeSettingsStore(initial: AppSettings = AppSettings()) : SettingsStore {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<AppSettings> = state

    override fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}

private object NoAlarms : AlarmGateway {
    override fun schedule(requestCode: Int, reminder: ScheduledReminder) = Unit

    override fun cancel(requestCode: Int) = Unit
}

private object NoRegistry : ScheduledRegistry {
    override fun load(): Set<Int> = emptySet()

    override fun save(requestCodes: Set<Int>) = Unit
}
