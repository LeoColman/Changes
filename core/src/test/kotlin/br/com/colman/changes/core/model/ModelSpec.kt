// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.model

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ModelSpec : FunSpec({
    context("reference range (7.6)") {
        val range = ReferenceRange(10.0, 20.0)

        test("comparison is inclusive at both limits") {
            range.isOutside(10.0).shouldBeFalse()
            range.isOutside(20.0).shouldBeFalse()
            range.isOutside(15.0).shouldBeFalse()
            range.isOutside(9.999).shouldBeTrue()
            range.isOutside(20.001).shouldBeTrue()
        }

        test("a single informed limit is respected, the missing one is ignored") {
            ReferenceRange(10.0, null).isOutside(1_000_000.0).shouldBeFalse()
            ReferenceRange(10.0, null).isOutside(9.0).shouldBeTrue()
            ReferenceRange(null, 20.0).isOutside(-1_000_000.0).shouldBeFalse()
            ReferenceRange(null, 20.0).isOutside(21.0).shouldBeTrue()
        }

        test("criterion 7.6.1: without an informed range there is no range at all") {
            ReferenceRange.ofNullable(null, null).shouldBeNull()
            ReferenceRange.ofNullable(1.0, null) shouldBe ReferenceRange(1.0, null)
            ReferenceRange.ofNullable(null, 2.0) shouldBe ReferenceRange(null, 2.0)
            ReferenceRange(null, null).isEmpty.shouldBeTrue()
            ReferenceRange(null, null).isOutside(0.0).shouldBeFalse()
            ReferenceRange(1.0, null).isEmpty.shouldBeFalse()
            ReferenceRange(null, 1.0).isEmpty.shouldBeFalse()
        }
    }

    context("body vocabulary (7.10)") {
        test("unset regions fall back to the neutral default") {
            BodyRegion.entries.forEach { region ->
                BodyVocabulary().choiceFor(region) shouldBe VocabularyChoice.Preset(region.defaultOption)
            }
        }

        test("valid choices are kept, invalid ones fall back to the default") {
            val vocabulary = BodyVocabulary()
                .with(BodyRegion.GENITAL, VocabularyChoice.Preset("DICK"))
                .with(BodyRegion.CHEST, VocabularyChoice.Custom("meu peito"))
                .with(BodyRegion.MENSTRUATION, VocabularyChoice.Preset("NOT_AN_OPTION"))
                .with(BodyRegion.FRONT_CANAL, VocabularyChoice.Custom("   "))
            vocabulary.choiceFor(BodyRegion.GENITAL) shouldBe VocabularyChoice.Preset("DICK")
            vocabulary.choiceFor(BodyRegion.CHEST) shouldBe VocabularyChoice.Custom("meu peito")
            vocabulary.choiceFor(BodyRegion.MENSTRUATION) shouldBe VocabularyChoice.Preset("BLEEDING")
            vocabulary.choiceFor(BodyRegion.FRONT_CANAL) shouldBe VocabularyChoice.Preset("FRONT_CANAL")
        }

        test("a preset of another region is not accepted") {
            BodyVocabulary().with(BodyRegion.CHEST, VocabularyChoice.Preset("DICK")).choiceFor(BodyRegion.CHEST) shouldBe
                VocabularyChoice.Preset("THORAX")
        }

        test("every default is one of the region options") {
            BodyRegion.entries.forEach { (it.defaultOption in it.options).shouldBeTrue() }
        }
    }

    context("small enums and helpers") {
        test("intensity levels") {
            Intensity.entries.map { it.level } shouldBe listOf(0, 1, 2, 3, 4)
            Intensity.fromLevel(0) shouldBe Intensity.NONE
            Intensity.fromLevel(4) shouldBe Intensity.COMPLETE
            Intensity.fromLevel(5).shouldBeNull()
            Intensity.fromLevel(-1).shouldBeNull()
        }

        test("only intramuscular and subcutaneous routes are injections") {
            Route.entries.filter { it.isInjection } shouldBe listOf(Route.INTRAMUSCULAR, Route.SUBCUTANEOUS)
        }

        test("treatment protocol is an open code") {
            TreatmentProtocol.MASCULINIZING.code shouldBe "MASCULINIZING"
            TreatmentProtocol("FEMINIZING").code shouldBe "FEMINIZING"
        }
    }

    context("media paths") {
        val id = Uuid.parse("0f8fad5b-d9cb-469f-a165-70867728950e")

        test("new paths are '<uuid>.<ext>' in lower case") {
            MediaPaths.forNew(id, "JPG") shouldBe "0f8fad5b-d9cb-469f-a165-70867728950e.jpg"
            MediaPaths.isValid(MediaPaths.forNew(id, "png")).shouldBeTrue()
            MediaPaths.isValid(MediaPaths.forNew(id, "m4a")).shouldBeTrue()
        }

        test("audio attachments are voice recordings, never images (ADR 0013)") {
            fun attachment(mimeType: String) = MediaAttachment(
                id,
                MediaOwnerType.BODY_CHANGE_ENTRY,
                id,
                MediaPaths.forNew(id, "m4a"),
                mimeType,
                null,
                "0".repeat(64),
                null,
            )
            attachment(MediaPaths.VOICE_MIME_TYPE).isAudio.shouldBeTrue()
            attachment("AUDIO/MP4").isAudio.shouldBeTrue()
            attachment("image/jpeg").isAudio.shouldBeFalse()
        }

        test("anything that could escape the media root is invalid") {
            listOf(
                "../0f8fad5b-d9cb-469f-a165-70867728950e.jpg",
                "a/0f8fad5b-d9cb-469f-a165-70867728950e.jpg",
                "/0f8fad5b-d9cb-469f-a165-70867728950e.jpg",
                "0f8fad5b-d9cb-469f-a165-70867728950e.exe",
                "0f8fad5b-d9cb-469f-a165-70867728950e.JPG",
                "0f8fad5b-d9cb-469f-a165-70867728950e.jpg\n",
                "not-a-uuid.jpg",
                "",
            ).forEach { MediaPaths.isValid(it).shouldBeFalse() }
        }

        test("extension from mime type") {
            MediaPaths.extensionForMimeType("image/png") shouldBe "png"
            MediaPaths.extensionForMimeType("IMAGE/WEBP") shouldBe "webp"
            MediaPaths.extensionForMimeType("image/heic") shouldBe "heic"
            MediaPaths.extensionForMimeType("image/heif") shouldBe "heif"
            MediaPaths.extensionForMimeType("image/jpeg") shouldBe "jpg"
            MediaPaths.extensionForMimeType("application/octet-stream") shouldBe "jpg"
            MediaPaths.extensionForMimeType("audio/mp4") shouldBe "m4a"
            MediaPaths.EXTENSIONS shouldBe setOf("jpg", "jpeg", "png", "webp", "heic", "heif", "m4a")
        }
    }

    context("recorded time") {
        test("keeps the local wall time of the moment it was recorded") {
            val instant = Instant.parse("2026-01-01T02:30:00Z")
            val recorded = RecordedTime.of(instant, TimeZone.of("America/Sao_Paulo"))
            recorded.offset shouldBe UtcOffset(hours = -3)
            recorded.offsetSeconds shouldBe -10_800L
            recorded.localDate shouldBe LocalDate(2025, 12, 31)
            recorded.localDateTime shouldBe LocalDateTime(2025, 12, 31, 23, 30)
        }

        test("truncates to milliseconds, like the database") {
            val recorded = RecordedTime.of(Instant.parse("2026-01-01T00:00:00.123456789Z"), TimeZone.UTC)
            recorded.instant shouldBe Instant.parse("2026-01-01T00:00:00.123Z")
            recorded.epochMillis shouldBe 1_767_225_600_123L
        }

        test("round-trips through database columns") {
            val recorded = RecordedTime.fromDb(1_767_225_600_123L, 19_800L)
            recorded.offset shouldBe UtcOffset(hours = 5, minutes = 30)
            RecordedTime.fromDb(recorded.epochMillis, recorded.offsetSeconds) shouldBe recorded
            MAX_OFFSET_SECONDS shouldBe 64_800L
        }
    }
})
