// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.db

import br.com.colman.changes.core.db.sql.Body_change_type
import br.com.colman.changes.core.db.sql.ChangesDatabase
import br.com.colman.changes.core.db.sql.Expected_change
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.testDatabase
import br.com.colman.changes.core.testing.testDataset
import br.com.colman.changes.core.testing.testLabels
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.days

class SeederSpec : FunSpec({
    val clock = FixedClock()

    fun seeded(): ChangesDatabase = testDatabase(clock)

    fun seeder(database: ChangesDatabase) = Seeder(database, testDataset, testLabels, clock)

    fun ChangesDatabase.counts() = listOf(
        bodyChangeQueries.selectAllTypes().executeAsList().size,
        medicationQueries.selectAllActive().executeAsList().size,
        labQueries.selectAllAnalytes().executeAsList().size,
        expectedChangeQueries.selectByProtocol("MASCULINIZING").executeAsList().size,
    )

    test("first seed creates single rows and all catalogs") {
        val database = testDatabase(clock, seed = false)
        database.profileQueries.get().executeAsOneOrNull().shouldBeNull()
        seeder(database).seed()
        database.counts() shouldBe listOf(15, 10, 23, 9)
        val meta = database.appMetaQueries.get().executeAsOne()
        meta.schema_version shouldBe CURRENT_SCHEMA_VERSION
        meta.clinical_dataset_version shouldBe 1L
        meta.last_export_at.shouldBeNull()
        val profile = database.profileQueries.get().executeAsOne()
        profile.treatment_protocol shouldBe "MASCULINIZING"
        profile.unit_system shouldBe "METRIC"
        profile.show_bmi shouldBe 0L
        profile.body_vocabulary shouldBe "{}"
        profile.created_at shouldBe clock.now.toEpochMilliseconds()
    }

    test("seeding again is idempotent") {
        val database = seeded()
        clock.advance(1.days)
        seeder(database).seed()
        seeder(database).seed()
        database.counts() shouldBe listOf(15, 10, 23, 9)
    }

    test("hidden state and timestamps chosen by the person survive a reseed") {
        val database = seeded()
        val type = database.bodyChangeQueries.selectTypeByCode("LIBIDO").executeAsOne()
        database.bodyChangeQueries.setTypeHidden(1L, 42L, type.id)
        clock.advance(1.days)
        seeder(database).seed()
        val after = database.bodyChangeQueries.selectTypeByCode("LIBIDO").executeAsOne()
        after.is_hidden shouldBe 1L
        after.updated_at shouldBe 42L
        after.created_at shouldBe type.created_at
    }

    test("builtin definitions are restored from the dataset") {
        val database = seeded()
        val type = database.bodyChangeQueries.selectTypeByCode("VOICE_DEEPENING").executeAsOne()
        database.bodyChangeQueries.updateBuiltinTypeDefinition("WRONG", "OTHER", 1L, 0L, null, type.id)
        val medication = testDataset.medications.first { it.key == "DEPOSTERON" }.id.toString()
        database.medicationQueries.updateBuiltinDefinition("x", "y", null, 1.0, "MG_PER_G", medication)
        val analyte = testDataset.labAnalytes.first().id.toString()
        database.labQueries.updateBuiltinAnalyteDefinition("WRONG", "x", analyte)

        seeder(database).seed()

        val restored = database.bodyChangeQueries.selectTypeById(type.id).executeAsOne()
        restored.copy(created_at = 0, updated_at = 0) shouldBe type.copy(created_at = 0, updated_at = 0)
        val med = database.medicationQueries.selectById(medication).executeAsOne()
        med.name shouldBe "Deposteron"
        med.substance shouldBe "cipionato de testosterona"
        med.default_route shouldBe "INTRAMUSCULAR"
        med.concentration_value shouldBe 100.0
        med.concentration_unit shouldBe "MG_PER_ML"
        val lab = database.labQueries.selectAnalyteById(analyte).executeAsOne()
        lab.label_key shouldBe "TESTOSTERONE_TOTAL"
        lab.default_unit shouldBe "ng/dL"
    }

    test("builtin reversibility comes from the dataset permanence") {
        val database = seeded()
        fun reversible(code: String) = database.bodyChangeQueries.selectTypeByCode(code).executeAsOne().is_reversible
        reversible("VOICE_DEEPENING") shouldBe 0L
        reversible("CLITORAL_ENLARGEMENT") shouldBe 0L
        reversible("MENSES_CESSATION") shouldBe 1L
        reversible("SKIN_OILINESS_ACNE").shouldBeNull()
        reversible("FACIAL_BODY_HAIR").shouldBeNull()
        reversible("LIBIDO").shouldBeNull()
        val voice = database.bodyChangeQueries.selectTypeByCode("VOICE_DEEPENING").executeAsOne()
        voice.supports_measurement shouldBe 1L
        voice.measurement_unit shouldBe "HZ"
        voice.label_key shouldBe "VOICE_DEEPENING"
        voice.custom_label.shouldBeNull()
        voice.is_builtin shouldBe 1L
        database.bodyChangeQueries.selectTypeByCode("LIBIDO").executeAsOne().supports_measurement shouldBe 0L
    }

    test("generic medication names are stored in pt-BR") {
        val database = seeded()
        val other = testDataset.medications.first { it.key == "OTHER_MEDICATION" }.id.toString()
        val row = database.medicationQueries.selectById(other).executeAsOne()
        row.name shouldBe "Outro medicamento"
        row.substance.shouldBeNull()
        row.default_route.shouldBeNull()
        row.concentration_value.shouldBeNull()
        row.is_builtin shouldBe 1L
    }

    test("expected changes are rewritten only when the dataset is newer or the table is empty") {
        val database = seeded()
        val stored = database.expectedChangeQueries.selectByProtocol("MASCULINIZING").executeAsList()
        stored.first().id shouldBe "MASCULINIZING:${stored.first().change_type_code}:ENDO_2017"
        stored.all { it.dataset_version == 1L } shouldBe true
        val bogus =
            Expected_change("bogus", "BOGUS", "MASCULINIZING", 0.0, null, null, null, "NOT_STATED", null, "ENDO_2017", 1L)
        database.expectedChangeQueries.insert(bogus)

        seeder(database).seed()
        database.expectedChangeQueries.selectByProtocol("MASCULINIZING").executeAsList() shouldHaveSize 10

        database.appMetaQueries.setClinicalDatasetVersion(0L)
        seeder(database).seed()
        database.expectedChangeQueries.selectByProtocol("MASCULINIZING").executeAsList() shouldHaveSize 9
        database.appMetaQueries.get().executeAsOne().clinical_dataset_version shouldBe 1L

        database.expectedChangeQueries.deleteAll()
        seeder(database).seed()
        database.expectedChangeQueries.selectByProtocol("MASCULINIZING").executeAsList() shouldHaveSize 9
    }

    test("the person's own records are never touched") {
        val database = seeded()
        val custom = Body_change_type(
            id = "6a0e4f5e-7f53-4d5b-9b44-3f3e7c1d2a10",
            code = "CUSTOM_6a0e4f5e-7f53-4d5b-9b44-3f3e7c1d2a10",
            label_key = null,
            custom_label = "Minha",
            category = "OTHER",
            is_reversible = null,
            supports_measurement = 0L,
            measurement_unit = null,
            is_builtin = 0L,
            is_hidden = 0L,
            created_at = 1L,
            updated_at = 2L,
            deleted_at = null,
        )
        database.bodyChangeQueries.insertType(custom)
        seeder(database).seed()
        database.bodyChangeQueries.selectTypeById(custom.id).executeAsOne() shouldBe custom
        database.bodyChangeQueries.selectAllTypes().executeAsList() shouldHaveSize 16
    }

    test("the profile row keeps the person's edits") {
        val database = seeded()
        database.profileQueries.update("Ana", 170.0, 1990L, "MASCULINIZING", 20_000L, "pt-BR", "IMPERIAL", 1L, "{}", 99L)
        seeder(database).seed()
        val profile = database.profileQueries.get().executeAsOne().shouldNotBeNull()
        profile.display_name shouldBe "Ana"
        profile.unit_system shouldBe "IMPERIAL"
        profile.updated_at shouldBe 99L
    }
})
