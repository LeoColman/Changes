// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.platform.reminders

import br.com.colman.changes.platform.AppSettings
import br.com.colman.changes.platform.SettingsStore
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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
import kotlin.time.Duration.Companion.milliseconds
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

    test("the quiet period is 500ms: changes closer together than that still coalesce into one sync") {
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
            advanceTimeBy(400.milliseconds)
            runCurrent()
            settings.update { it.copy(moodReminderEnabled = true) }
            advanceTimeBy(1.seconds)
            runCurrent()

            syncs shouldBe 2
        }
    }

    test("a source that truly fails after suspending cancels the resync job, it isn't swallowed") {
        runTest {
            val dispatcher = UnconfinedTestDispatcher(testScheduler)
            val env = PlanningEnvironment(dispatcher)
            val settings = FakeSettingsStore()
            val failingSource = UpcomingReminders {
                delay(1)
                error("source unavailable")
            }
            val sync = ReminderSync(failingSource, NoAlarms, NoRegistry, env.clock)

            // Job e handler próprios, sem propagar para o teste (diferente de backgroundScope): a
            // falha é exatamente o que este teste verifica, não um erro do teste em si.
            var failure: Throwable? = null
            val handler = CoroutineExceptionHandler { _, throwable -> failure = throwable }
            val scope = CoroutineScope(dispatcher + SupervisorJob() + handler)
            val job = ReminderResyncer(env.regimens, env.calendar, settings, sync).start(scope)
            advanceTimeBy(1.seconds)
            runCurrent()

            job.isCancelled shouldBe true
            failure.shouldBeInstanceOf<IllegalStateException>()
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
