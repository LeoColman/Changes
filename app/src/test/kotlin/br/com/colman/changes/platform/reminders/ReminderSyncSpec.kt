// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.platform.reminders

import br.com.colman.changes.core.testing.FixedClock
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.minutes

class ReminderSyncSpec : FunSpec({
    val clock = FixedClock()

    class FakeGateway : AlarmGateway {
        val scheduled = mutableMapOf<Int, ScheduledReminder>()
        val cancelled = mutableListOf<Int>()

        override fun schedule(requestCode: Int, reminder: ScheduledReminder) {
            scheduled[requestCode] = reminder
        }

        override fun cancel(requestCode: Int) {
            cancelled += requestCode
            scheduled.remove(requestCode)
        }
    }

    class FakeRegistry : ScheduledRegistry {
        var codes: Set<Int> = emptySet()

        override fun load(): Set<Int> = codes

        override fun save(requestCodes: Set<Int>) {
            codes = requestCodes
        }
    }

    fun reminder(key: String, inMinutes: Int, kind: ReminderKind = ReminderKind.DOSE) =
        ScheduledReminder(key, clock.now + inMinutes.minutes, kind)

    test("schedules every future reminder and remembers them") {
        val gateway = FakeGateway()
        val registry = FakeRegistry()
        val upcoming = listOf(reminder("a", 10), reminder("b", 20, ReminderKind.EVENT))
        ReminderSync({ upcoming }, gateway, registry, clock).sync()
        gateway.scheduled.values shouldContainExactlyInAnyOrder upcoming
        registry.codes shouldBe upcoming.map { ReminderSync.requestCodeOf(it.key) }.toSet()
    }

    test("criterion 7.9.3: after a reboot the same pending alarms are scheduled again") {
        val registry = FakeRegistry()
        val upcoming = listOf(reminder("dose:1", 60), reminder("event:2", 120))
        ReminderSync({ upcoming }, FakeGateway(), registry, clock).sync()

        val afterReboot = FakeGateway()
        ReminderSync({ upcoming }, afterReboot, registry, clock).sync()
        afterReboot.scheduled.values shouldContainExactlyInAnyOrder upcoming
    }

    test("reminders that disappeared are cancelled; past ones are never scheduled") {
        val gateway = FakeGateway()
        val registry = FakeRegistry()
        ReminderSync({ listOf(reminder("old", 10), reminder("kept", 20)) }, gateway, registry, clock).sync()
        ReminderSync({ listOf(reminder("kept", 20), reminder("past", -5)) }, gateway, registry, clock).sync()
        gateway.cancelled shouldBe listOf(ReminderSync.requestCodeOf("old"))
        gateway.scheduled.values.map { it.key } shouldBe listOf("kept")
    }

    test("only the next ${ReminderSync.MAX_ALARMS} reminders are scheduled, earliest first") {
        val gateway = FakeGateway()
        val many = (1..100).map { reminder("r$it", 100 - it) }
        ReminderSync({ many }, gateway, FakeRegistry(), clock).sync()
        gateway.scheduled.size shouldBe ReminderSync.MAX_ALARMS
        gateway.scheduled.values.maxOf { it.at } shouldBe clock.now + 64.minutes
        gateway.scheduled.values.minOf { it.at } shouldBe clock.now + 1.minutes
    }

    test("a source that fails after truly suspending is not swallowed; nothing gets scheduled or saved") {
        val gateway = FakeGateway()
        val registry = FakeRegistry()
        val failing = UpcomingReminders {
            withContext(Dispatchers.Default) {}
            error("source unavailable")
        }

        shouldThrow<IllegalStateException> { ReminderSync(failing, gateway, registry, clock).sync() }

        gateway.scheduled.shouldBeEmpty()
        registry.codes shouldBe emptySet()
    }
})
