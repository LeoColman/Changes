// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import br.com.colman.changes.core.model.BodyRegion
import br.com.colman.changes.core.model.BodyVocabulary
import br.com.colman.changes.core.model.RecurrenceRule
import br.com.colman.changes.core.model.Schedule
import br.com.colman.changes.core.model.ScheduleType
import br.com.colman.changes.core.model.VocabularyChoice
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.core.model.isSuccess
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.datetime.DayOfWeek

class CodecsSpec : FunSpec({
    context("schedule_config") {
        test("stable JSON for each schedule type (part of the backup format)") {
            Codecs.encodeSchedule(Schedule.IntervalDays(14)) shouldBe """{"days":14}"""
            Codecs.encodeSchedule(Schedule.Weekly(setOf(DayOfWeek.THURSDAY, DayOfWeek.MONDAY))) shouldBe
                """{"daysOfWeek":["MONDAY","THURSDAY"],"everyWeeks":1}"""
            Codecs.encodeSchedule(Schedule.AsNeeded) shouldBe "{}"
            Codecs.encodeSchedule(Schedule.Custom(RecurrenceRule(RecurrenceRule.Frequency.MONTHLY, interval = 3))) shouldBe
                """{"rrule":"FREQ=MONTHLY;INTERVAL=3"}"""
            Codecs.encodeSchedule(Schedule.Stepped(listOf(45, 90), thenEvery = 90)) shouldBe
                """{"steps":[45,90],"thenEvery":90}"""
            Codecs.encodeSchedule(Schedule.Stepped(listOf(0))) shouldBe """{"steps":[0]}"""
        }

        test("round-trips every schedule type") {
            listOf(
                Schedule.IntervalDays(7),
                Schedule.Weekly(setOf(DayOfWeek.SUNDAY), 2),
                Schedule.AsNeeded,
                Schedule.Custom(RecurrenceRule(RecurrenceRule.Frequency.WEEKLY, byDay = setOf(DayOfWeek.FRIDAY))),
                Schedule.Stepped(listOf(45, 90), thenEvery = 90),
                Schedule.Stepped(listOf(30)),
            ).forEach { schedule -> Codecs.decodeSchedule(schedule.type, Codecs.encodeSchedule(schedule)) shouldBe schedule }
        }

        test("weekly without everyWeeks defaults to every week") {
            Codecs.decodeSchedule(ScheduleType.WEEKLY, """{"daysOfWeek":["MONDAY"]}""") shouldBe Schedule.Weekly(setOf(DayOfWeek.MONDAY), 1)
        }

        test("config that does not match the type is rejected, not guessed") {
            Codecs.decodeSchedule(ScheduleType.INTERVAL_DAYS, "{}").shouldBeNull()
            Codecs.decodeSchedule(ScheduleType.WEEKLY, """{"days":3}""").shouldBeNull()
            Codecs.decodeSchedule(ScheduleType.WEEKLY, """{"daysOfWeek":["MONDAY","FUNDAY"]}""").shouldBeNull()
            Codecs.decodeSchedule(ScheduleType.CUSTOM_CRON, """{"rrule":"FREQ=HOURLY"}""").shouldBeNull()
            Codecs.decodeSchedule(ScheduleType.CUSTOM_CRON, "{}").shouldBeNull()
            Codecs.decodeSchedule(ScheduleType.INTERVAL_DAYS, "not json").shouldBeNull()
            Codecs.decodeSchedule(ScheduleType.INTERVAL_DAYS, """{"days":"x"}""").shouldBeNull()
            Codecs.decodeSchedule(ScheduleType.INTERVAL_DAYS, """{"days":1,"unknown":2}""").shouldBeNull()
            Codecs.decodeSchedule(ScheduleType.AS_NEEDED, "{}") shouldBe Schedule.AsNeeded
        }

        test("varying intervals are validated on read: a series that could never end is rejected") {
            Codecs.decodeSchedule(ScheduleType.STEPPED, "{}").shouldBeNull()
            Codecs.decodeSchedule(ScheduleType.STEPPED, """{"steps":[]}""").shouldBeNull()
            Codecs.decodeSchedule(ScheduleType.STEPPED, """{"steps":[45],"thenEvery":0}""").shouldBeNull()
            Codecs.decodeSchedule(ScheduleType.STEPPED, """{"steps":[45,0]}""").shouldBeNull()
            Codecs.decodeSchedule(ScheduleType.STEPPED, """{"steps":[45],"thenEvery":90}""") shouldBe
                Schedule.Stepped(listOf(45), thenEvery = 90)
        }
    }

    context("body_vocabulary") {
        test("stable JSON, regions in declaration order") {
            val vocabulary = BodyVocabulary()
                .with(BodyRegion.CHEST, VocabularyChoice.Custom("meu peito"))
                .with(BodyRegion.GENITAL, VocabularyChoice.Preset("DICK"))
            Codecs.encodeVocabulary(vocabulary) shouldBe """{"GENITAL":{"preset":"DICK"},"CHEST":{"custom":"meu peito"}}"""
            Codecs.encodeVocabulary(BodyVocabulary()) shouldBe "{}"
        }

        test("round-trips") {
            val vocabulary = BodyVocabulary(
                mapOf(
                    BodyRegion.GENITAL to VocabularyChoice.Preset("PHALLUS"),
                    BodyRegion.MENSTRUATION to VocabularyChoice.Custom("🌙 \"ciclo\""),
                ),
            )
            Codecs.decodeVocabulary(Codecs.encodeVocabulary(vocabulary)) shouldBe vocabulary
        }

        test("malformed input falls back to the default and unknown entries are dropped") {
            Codecs.decodeVocabulary("not json") shouldBe BodyVocabulary()
            Codecs.decodeVocabulary("[]") shouldBe BodyVocabulary()
            Codecs.decodeVocabulary("""{"GENITAL":{"preset":1}}""") shouldBe BodyVocabulary()
            Codecs.decodeVocabulary("""{"ELBOW":{"preset":"X"},"CHEST":{}}""") shouldBe BodyVocabulary()
            Codecs.decodeVocabulary("""{"CHEST":{"preset":"CHEST","custom":"x"}}""") shouldBe
                BodyVocabulary(mapOf(BodyRegion.CHEST to VocabularyChoice.Preset("CHEST")))
        }
    }

    context("tags") {
        test("round-trips arbitrary strings, including empty and unicode ones") {
            checkAll(500, Arb.list(Arb.string(0..20), 0..6)) { tags ->
                Codecs.decodeTags(Codecs.encodeTags(tags)).getOrNull() shouldBe tags
            }
            Codecs.encodeTags(emptyList()) shouldBe "[]"
        }

        test("rejects malformed tags") {
            Codecs.decodeTags("{}").isSuccess shouldBe false
            Codecs.decodeTags("[1,2]").isSuccess shouldBe false
            Codecs.decodeTags("""["1","2"]""").getOrNull() shouldBe listOf("1", "2")
            Codecs.decodeTags("[").isSuccess shouldBe false
        }
    }
})
