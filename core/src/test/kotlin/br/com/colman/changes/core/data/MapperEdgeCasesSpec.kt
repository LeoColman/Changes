// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import br.com.colman.changes.core.db.sql.Calendar_event
import br.com.colman.changes.core.db.sql.Mood_log
import br.com.colman.changes.core.db.sql.Profile
import br.com.colman.changes.core.db.sql.Regimen
import br.com.colman.changes.core.model.BodyRegion
import br.com.colman.changes.core.model.BodyVocabulary
import br.com.colman.changes.core.model.TreatmentProtocol
import br.com.colman.changes.core.model.UnitSystem
import br.com.colman.changes.core.model.VocabularyChoice
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.datetime.LocalDate

/** Perfil e dados corrompidos: corrupção derruba com mensagem clara, nunca vira dado errado em silêncio. */
class MapperEdgeCasesSpec : FunSpec({
    val id = "0f8fad5b-d9cb-469f-a165-70867728950e"

    test("profile row maps every field") {
        val row = Profile(
            id = "default",
            display_name = "Theo",
            height_cm = 172.5,
            birth_year = 1994L,
            treatment_protocol = "MASCULINIZING",
            hrt_start_date = LocalDate(2025, 3, 1).toEpochDays(),
            locale = "pt-BR",
            unit_system = "IMPERIAL",
            show_bmi = 1L,
            body_vocabulary = """{"GENITAL":{"preset":"DICK"}}""",
            created_at = 1L,
            updated_at = 2L,
            deleted_at = null,
        )
        val profile = row.toModel()
        profile.displayName shouldBe "Theo"
        profile.heightCm shouldBe 172.5
        profile.birthYear shouldBe 1994
        profile.treatmentProtocol shouldBe TreatmentProtocol.MASCULINIZING
        profile.hrtStartDate shouldBe LocalDate(2025, 3, 1)
        profile.locale shouldBe "pt-BR"
        profile.unitSystem shouldBe UnitSystem.IMPERIAL
        profile.showBmi shouldBe true
        profile.bodyVocabulary shouldBe BodyVocabulary().with(BodyRegion.GENITAL, VocabularyChoice.Preset("DICK"))

        val empty = row.copy(
            display_name = null,
            height_cm = null,
            birth_year = null,
            hrt_start_date = null,
            locale = null,
            show_bmi = 0L,
        ).toModel()
        empty.displayName shouldBe null
        empty.heightCm shouldBe null
        empty.birthYear shouldBe null
        empty.hrtStartDate shouldBe null
        empty.locale shouldBe null
        empty.showBmi shouldBe false
    }

    test("a corrupted schedule is reported with the regimen id") {
        val row = Regimen(
            id, id, 1.0, "MG", "ORAL", "INTERVAL_DAYS", "{}", null, 0L, null, 1L, null, 1L, 1L, null,
        )
        shouldThrow<IllegalStateException> { row.toModel() }.message shouldContain id
    }

    test("corrupted tags are reported with the mood log id") {
        val row = Mood_log(id, 0L, 3L, 3L, null, null, null, null, "not json", 1L, 1L, null)
        shouldThrow<IllegalStateException> { row.toModel() }.message shouldContain id
    }

    test("a corrupted recurrence rule is reported with the event id") {
        val row = Calendar_event(
            id, "t", null, 0L, 0L, null, null, 0L, "OTHER", "MANUAL", null, null, "FREQ=HOURLY", null, null, 1L, 1L, null,
        )
        shouldThrow<IllegalStateException> { row.toModel() }.message shouldContain id
    }
})
