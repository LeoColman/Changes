// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.platform.reminders

import br.com.colman.changes.core.testing.FixedTimeZoneProvider
import br.com.colman.changes.platform.AppSettings
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant

/** Seção 7.7: lembrete diário opcional de check-in, um por dia no horário escolhido, só se ligado. */
class SettingsUpcomingRemindersSpec : FunSpec({
    val zones = FixedTimeZoneProvider()

    test("off, and with no other source wired in, there is nothing to remind") {
        runTest {
            val settings = FakeSettingsStore(AppSettings(moodReminderEnabled = false))
            val source = SettingsUpcomingReminders(settings, zones)

            val now = LocalDateTime(LocalDate(2026, 9, 12), LocalTime(9, 0)).toInstant(zones.zone)
            source.upcoming(now) shouldBe emptyList()
        }
    }

    test("off, only what the wrapped source provides comes back") {
        runTest {
            val settings = FakeSettingsStore(AppSettings(moodReminderEnabled = false))
            val now = LocalDateTime(LocalDate(2026, 9, 12), LocalTime(9, 0)).toInstant(zones.zone)
            val others = UpcomingReminders { now2 -> listOf(ScheduledReminder("other", now2, ReminderKind.EVENT)) }
            val source = SettingsUpcomingReminders(settings, zones, others)

            source.upcoming(now) shouldBe listOf(ScheduledReminder("other", now, ReminderKind.EVENT))
        }
    }

    test("on, schedules the next three days at the chosen minute, merged with the wrapped source") {
        runTest {
            val minutes = 8 * 60 + 30 // 08:30
            val settings = FakeSettingsStore(AppSettings(moodReminderEnabled = true, moodReminderMinutes = minutes))
            val now = LocalDateTime(LocalDate(2026, 9, 12), LocalTime(0, 0)).toInstant(zones.zone)
            val others = UpcomingReminders { listOf(ScheduledReminder("other", now, ReminderKind.EVENT)) }
            val source = SettingsUpcomingReminders(settings, zones, others)

            val reminders = source.upcoming(now)

            val expectedDates = listOf(LocalDate(2026, 9, 12), LocalDate(2026, 9, 13), LocalDate(2026, 9, 14))
            reminders.filter { it.kind == ReminderKind.MOOD }.map { it.key } shouldBe
                expectedDates.map { "mood:${it.toEpochDays()}" }
            reminders.filter { it.kind == ReminderKind.MOOD }.map { it.at } shouldBe
                expectedDates.map { LocalDateTime(it, LocalTime(8, 30)).toInstant(zones.zone) }
            reminders.count { it.kind == ReminderKind.EVENT } shouldBe 1
        }
    }

    test("today's reminder drops off exactly at the minute it is due, not a moment before") {
        runTest {
            val settings = FakeSettingsStore(AppSettings(moodReminderEnabled = true, moodReminderMinutes = 8 * 60))
            val source = SettingsUpcomingReminders(settings, zones)

            val dueNow = LocalDateTime(LocalDate(2026, 9, 12), LocalTime(8, 0)).toInstant(zones.zone)
            val reminders = source.upcoming(dueNow)

            reminders.map { it.key } shouldBe listOf(
                LocalDate(2026, 9, 13),
                LocalDate(2026, 9, 14),
            ).map { "mood:${it.toEpochDays()}" }
        }
    }

    test("if the wrapped source truly fails after suspending, the failure is not swallowed") {
        runTest {
            val settings = FakeSettingsStore(AppSettings(moodReminderEnabled = false))
            val failing = UpcomingReminders {
                delay(1)
                error("source unavailable")
            }
            val source = SettingsUpcomingReminders(settings, zones, failing)
            val now = LocalDateTime(LocalDate(2026, 9, 12), LocalTime(9, 0)).toInstant(zones.zone)

            shouldThrow<IllegalStateException> { source.upcoming(now) }
        }
    }
})
