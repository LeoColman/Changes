// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import br.com.colman.changes.core.model.BodyChangeCategory
import br.com.colman.changes.core.model.BodyChangeEntry
import br.com.colman.changes.core.model.BodyChangeType
import br.com.colman.changes.core.model.BodyMeasurementUnit
import br.com.colman.changes.core.model.CalendarCategory
import br.com.colman.changes.core.model.CalendarEvent
import br.com.colman.changes.core.model.Concentration
import br.com.colman.changes.core.model.ConcentrationUnit
import br.com.colman.changes.core.model.ConditionSeverity
import br.com.colman.changes.core.model.ConditionStatus
import br.com.colman.changes.core.model.Dose
import br.com.colman.changes.core.model.DoseLog
import br.com.colman.changes.core.model.DoseUnit
import br.com.colman.changes.core.model.EventSourceType
import br.com.colman.changes.core.model.ExerciseIntensity
import br.com.colman.changes.core.model.ExerciseSession
import br.com.colman.changes.core.model.HealthCondition
import br.com.colman.changes.core.model.InjectionSite
import br.com.colman.changes.core.model.Intensity
import br.com.colman.changes.core.model.LabAnalyte
import br.com.colman.changes.core.model.LabResult
import br.com.colman.changes.core.model.Measurement
import br.com.colman.changes.core.model.MeasurementType
import br.com.colman.changes.core.model.MeasurementUnit
import br.com.colman.changes.core.model.MediaAttachment
import br.com.colman.changes.core.model.MediaOwnerType
import br.com.colman.changes.core.model.Medication
import br.com.colman.changes.core.model.MoodLog
import br.com.colman.changes.core.model.RecordedTime
import br.com.colman.changes.core.model.RecurrenceRule
import br.com.colman.changes.core.model.ReferenceRange
import br.com.colman.changes.core.model.Regimen
import br.com.colman.changes.core.model.Route
import br.com.colman.changes.core.model.Schedule
import br.com.colman.changes.core.testing.shouldEqual
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.uuid.Uuid

class RowMappersSpec : FunSpec({
    val at = RecordedTime.fromDb(1_757_678_400_000L, -10_800L)
    val later = RecordedTime.fromDb(1_757_682_000_000L, 3_600L)

    fun id() = Uuid.random()

    test("scalar helpers") {
        0L.asBoolean() shouldBe false
        1L.asBoolean() shouldBe true
        2L.asBoolean() shouldBe true
        true.asLong() shouldBe 1L
        false.asLong() shouldBe 0L
        0L.asLocalTime() shouldBe LocalTime(0, 0)
        1439L.asLocalTime() shouldBe LocalTime(23, 59)
        LocalTime(23, 59).asMinutes() shouldBe 1439L
        LocalTime(7, 5).asMinutes() shouldBe 425L
        LocalDate(1970, 1, 1).asEpochDay() shouldBe 0L
        (-1L).asLocalDate() shouldBe LocalDate(1969, 12, 31)
        recordedOrNull(null, 0L).shouldBeNull()
        recordedOrNull(0L, null).shouldBeNull()
        recordedOrNull(at.epochMillis, at.offsetSeconds) shouldBe at
    }

    test("medication round-trips, with and without concentration") {
        val full = Medication(
            id(),
            "Nebido",
            "undecilato",
            Route.INTRAMUSCULAR,
            Concentration(250.0, ConcentrationUnit.MG_PER_ML),
            true,
            true
        )
        full.toRow(1L, 2L).toModel() shouldEqual full
        val bare = Medication(id(), "x", null, null, null, false, false)
        bare.toRow(1L, 2L).toModel() shouldEqual bare
        full.toRow(1L, 2L).created_at shouldBe 1L
        full.toRow(1L, 2L).updated_at shouldBe 2L
        full.toRow(1L, 2L).deleted_at.shouldBeNull()
    }

    test("regimen round-trips every schedule type") {
        listOf(
            Schedule.IntervalDays(14),
            Schedule.Weekly(setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY), 2),
            Schedule.AsNeeded,
            Schedule.Custom(RecurrenceRule(RecurrenceRule.Frequency.MONTHLY, interval = 3)),
        ).forEach { schedule ->
            val regimen = Regimen(
                id(), id(), Dose(0.25, DoseUnit.ML), Route.SUBCUTANEOUS, schedule, LocalTime(8, 30),
                LocalDate(2026, 1, 1), LocalDate(2026, 12, 31), true, "nota",
            )
            regimen.toRow(1L, 2L).toModel() shouldEqual regimen
            regimen.copy(
                timeOfDay = null,
                endDate = null,
                isActive = false,
                notes = null
            ).let { it.toRow(1L, 1L).toModel() shouldEqual it }
        }
    }

    test("dose log round-trips") {
        val log = DoseLog(
            id(),
            id(),
            id(),
            Dose(100.0, DoseUnit.MG),
            Route.INTRAMUSCULAR,
            InjectionSite.GLUTE_LEFT,
            at,
            "ok"
        )
        log.toRow(1L, 2L).toModel() shouldEqual log
        log.copy(regimenId = null, injectionSite = null, notes = null).let { it.toRow(1L, 2L).toModel() shouldEqual it }
    }

    test("body change type and entry round-trip") {
        val type =
            BodyChangeType(id(), "CUSTOM_x", null, "Minha", BodyChangeCategory.OTHER, true, true, BodyMeasurementUnit.CM, false, true)
        type.toRow(1L, 2L).toModel() shouldEqual type
        type.copy(
            isReversible = null,
            measurementUnit = null,
            supportsMeasurement = false
        ).let { it.toRow(1L, 2L).toModel() shouldEqual it }
        type.copy(isReversible = false).let { it.toRow(1L, 2L).toModel() shouldEqual it }
        val entry = BodyChangeEntry(id(), type.id, at, Intensity.MARKED, 3.5, BodyMeasurementUnit.CM, "nota")
        entry.toRow(1L, 2L).toModel() shouldEqual entry
        entry.copy(intensity = null, measurementValue = null, measurementUnit = null, notes = null).let {
            it.toRow(1L, 2L).toModel() shouldEqual it
        }
        entry.copy(intensity = Intensity.NONE).let { it.toRow(1L, 2L).toModel() shouldEqual it }
    }

    test("media attachment round-trips") {
        val media = MediaAttachment(
            id(),
            MediaOwnerType.BODY_CHANGE_ENTRY,
            id(),
            "x.jpg",
            "image/jpeg",
            at,
            "ab",
            "legenda"
        )
        media.toRow(1L, 2L).toModel() shouldEqual media
        media.copy(capturedAt = null, caption = null).let { it.toRow(1L, 2L).toModel() shouldEqual it }
    }

    test("measurement, exercise, condition round-trip") {
        val measurement = Measurement(id(), MeasurementType.CUSTOM, "panturrilha", 38.2, MeasurementUnit.CM, at, "n")
        measurement.toRow(1L, 2L).toModel() shouldEqual measurement
        val exercise = ExerciseSession(id(), "corrida", 45, ExerciseIntensity.VIGOROUS, later, null)
        exercise.toRow(1L, 2L).toModel() shouldEqual exercise
        val condition = HealthCondition(
            id(), "Hipertensão", "I10", ConditionSeverity.MODERATE, ConditionStatus.IN_REMISSION,
            LocalDate(2020, 2, 29), LocalDate(2024, 2, 29), true, "nota",
        )
        condition.toRow(1L, 2L).toModel() shouldEqual condition
        condition.copy(code = null, diagnosedAt = null, resolvedAt = null, affectsTreatment = false).let {
            it.toRow(1L, 2L).toModel() shouldEqual it
        }
    }

    test("lab analyte and result round-trip; empty range stays empty") {
        val analyte = LabAnalyte(id(), "CUSTOM_y", null, "Zinco", "µg/dL", false, true)
        analyte.toRow(1L, 2L).toModel() shouldEqual analyte
        val result = LabResult(id(), analyte.id, 52.5, "%", at, ReferenceRange(40.0, 54.0), "Lab", "n")
        result.toRow(1L, 2L).toModel() shouldEqual result
        result.copy(referenceRange = ReferenceRange(null, 54.0)).let { it.toRow(1L, 2L).toModel() shouldEqual it }
        result.copy(
            referenceRange = null,
            labName = null,
            notes = null
        ).let { it.toRow(1L, 2L).toModel() shouldEqual it }
    }

    test("mood log round-trips tags") {
        val mood = MoodLog(id(), LocalDate(2026, 9, 12), 3, 4, 2, null, 7.5, "dia ok", listOf("trabalho", "", "🙂"))
        mood.toRow(1L, 2L).toModel() shouldEqual mood
        mood.copy(anxiety = null, dysphoria = 5, sleepHours = null, note = null, tags = emptyList()).let {
            it.toRow(1L, 2L).toModel() shouldEqual it
        }
    }

    test("calendar event round-trips with all optional parts") {
        val event = CalendarEvent(
            id(), "Consulta", "endócrino", at, later, false, CalendarCategory.APPOINTMENT, EventSourceType.MANUAL, null, 60,
            RecurrenceRule(RecurrenceRule.Frequency.MONTHLY, count = 3), later,
        )
        event.toRow(1L, 2L).toModel() shouldEqual event
        event.copy(
            description = null,
            end = null,
            isAllDay = true,
            reminderMinutesBefore = null,
            recurrence = null,
            completedAt = null
        ).let {
            it.toRow(1L, 2L).toModel() shouldEqual it
        }
    }
})
