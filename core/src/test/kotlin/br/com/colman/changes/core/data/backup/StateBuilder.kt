// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data.backup

import br.com.colman.changes.core.data.Codecs
import br.com.colman.changes.core.db.sql.Body_change_entry
import br.com.colman.changes.core.db.sql.Body_change_type
import br.com.colman.changes.core.db.sql.Calendar_event
import br.com.colman.changes.core.db.sql.Dose_log
import br.com.colman.changes.core.db.sql.Exercise_session
import br.com.colman.changes.core.db.sql.Health_condition
import br.com.colman.changes.core.db.sql.Lab_analyte
import br.com.colman.changes.core.db.sql.Lab_result
import br.com.colman.changes.core.db.sql.Measurement
import br.com.colman.changes.core.db.sql.Media_attachment
import br.com.colman.changes.core.db.sql.Medication
import br.com.colman.changes.core.db.sql.Mood_log
import br.com.colman.changes.core.db.sql.Regimen
import br.com.colman.changes.core.model.BodyRegion
import br.com.colman.changes.core.model.BodyVocabulary
import br.com.colman.changes.core.model.MAX_OFFSET_SECONDS
import br.com.colman.changes.core.model.MediaPaths
import br.com.colman.changes.core.model.RecurrenceRule
import br.com.colman.changes.core.model.Schedule
import br.com.colman.changes.core.model.VocabularyChoice
import br.com.colman.changes.core.testing.testDataset
import kotlinx.datetime.DayOfWeek
import kotlin.random.Random
import kotlin.uuid.Uuid

// ------------------------------------------------------------------------------------------------
// Estado arbitrário (Seção 11.2, item 1): todas as tabelas, tombstones, mídias, catálogos
// customizados, unicode e strings vazias. Gerado a partir de um Random semeado pelo property test.
// ------------------------------------------------------------------------------------------------

internal data class DatabaseState(
    val profile: ProfileValues,
    val hiddenTypes: List<Pair<String, Long>>,
    val medications: List<Medication>,
    val regimens: List<Regimen>,
    val doses: List<Dose_log>,
    val types: List<Body_change_type>,
    val entries: List<Body_change_entry>,
    val attachments: List<Media_attachment>,
    val measurements: List<Measurement>,
    val exercises: List<Exercise_session>,
    val conditions: List<Health_condition>,
    val analytes: List<Lab_analyte>,
    val results: List<Lab_result>,
    val moods: List<Mood_log>,
    val events: List<Calendar_event>,
)

internal data class ProfileValues(
    val displayName: String?,
    val heightCm: Double?,
    val hrtStartDate: Long?,
    val unitSystem: String,
    val showBmi: Long,
    val vocabulary: String,
    val updatedAt: Long,
)

/** Bytes determinísticos por caminho: a mesma mídia tem o mesmo conteúdo em qualquer estado. */
internal fun fileBytes(path: String): ByteArray = Random(path.hashCode()).let { it.nextBytes(1 + it.nextInt(2048)) }

/** Anexo excluído pode ter o arquivo purgado; anexo vivo sempre tem arquivo. */
internal fun Media_attachment.hasFile(): Boolean = deleted_at == null || relative_path.hashCode() % 2 == 0

internal suspend fun DatabaseState.applyTo(device: Device, withFiles: Boolean = true) {
    val db = device.database
    db.transaction {
        db.profileQueries.update(
            profile.displayName,
            profile.heightCm,
            null,
            "MASCULINIZING",
            profile.hrtStartDate,
            null,
            profile.unitSystem,
            profile.showBmi,
            profile.vocabulary,
            profile.updatedAt,
        )
        hiddenTypes.forEach { (id, at) -> db.bodyChangeQueries.setTypeHidden(1L, at, id) }
        medications.forEach(db.medicationQueries::insert)
        regimens.forEach(db.regimenQueries::insert)
        doses.forEach(db.doseLogQueries::insert)
        types.forEach(db.bodyChangeQueries::insertType)
        entries.forEach(db.bodyChangeQueries::insertEntry)
        attachments.forEach(db.mediaQueries::insert)
        measurements.forEach(db.measurementQueries::insert)
        exercises.forEach(db.exerciseQueries::insert)
        conditions.forEach(db.healthConditionQueries::insert)
        analytes.forEach(db.labQueries::insertAnalyte)
        results.forEach(db.labQueries::insertResult)
        moods.forEach(db.moodQueries::insert)
        events.forEach(db.calendarQueries::insert)
    }
    if (withFiles) {
        attachments.filter { it.hasFile() }.forEach {
            device.media.write(it.relative_path, fileBytes(it.relative_path).inputStream())
        }
    }
}

/** Toda tabela com linhas, e anexos vivos e na lixeira. */
internal fun DatabaseState.isComplete(): Boolean {
    val tables =
        listOf(medications, regimens, doses, types, entries, measurements, exercises, conditions, analytes, results, moods, events)
    return tables.all {
        it.isNotEmpty()
    } && attachments.any { it.deleted_at == null } && attachments.any { it.deleted_at != null }
}

/** O primeiro estado completo pela ordem das sementes: determinístico entre execuções. */
internal fun completeState(): DatabaseState = generateSequence(1L) { it + 1 }
    .map { StateBuilder(Random(it)).build() }
    .first { it.isComplete() }

internal class StateBuilder(private val r: Random) {
    private val medicationIds = testDataset.medications.map { it.id.toString() }.toMutableList()
    private val typeIds = testDataset.bodyChangeTypes.map { it.id.toString() }.toMutableList()
    private val analyteIds = testDataset.labAnalytes.map { it.id.toString() }.toMutableList()
    private val builtinTypes = typeIds.size

    fun build(): DatabaseState {
        val medications = List(r.nextInt(4)) { medication() }.also { list -> medicationIds += list.map { it.id } }
        val regimens = List(r.nextInt(4)) { regimen() }
        val types = List(r.nextInt(3)) { type() }.also { list -> typeIds += list.map { it.id } }
        val entries = List(r.nextInt(6)) { entry() }
        val analytes = List(r.nextInt(3)) { analyte() }.also { list -> analyteIds += list.map { it.id } }
        return DatabaseState(
            profile = profile(),
            hiddenTypes = List(r.nextInt(3)) { typeIds[r.nextInt(builtinTypes)] to instant() }.distinctBy { it.first },
            medications = medications,
            regimens = regimens,
            doses = List(r.nextInt(7)) { dose(regimens.map { it.id }) },
            types = types,
            entries = entries,
            attachments = entries.flatMap { e -> List(r.nextInt(3)) { attachment(e.id) } },
            measurements = List(r.nextInt(5)) { measurement() },
            exercises = List(r.nextInt(4)) { exercise() },
            conditions = List(r.nextInt(4)) { condition() },
            analytes = analytes,
            results = List(r.nextInt(5)) { labResult() },
            moods = List(r.nextInt(6)) { 19_000L + r.nextLong(3_000L) }.distinct().map { mood(it) },
            events = List(r.nextInt(4)) { event() },
        )
    }

    /**
     * Segundo estado, independente mas com ids em comum: campos mudados com `updated_at` maior, menor
     * ou **igual** (empate com conteúdo diferente), tombstones novos, check-in do mesmo dia com outro id
     * e linhas novas. Serve para a propriedade de comutatividade do merge (Seção 11.2, item 6).
     */
    fun conflicting(base: DatabaseState): DatabaseState {
        val extra = build()
        return base.copy(
            profile = base.profile.copy(displayName = text(), updatedAt = base.profile.updatedAt + drift()),
            medications = base.medications.sometimes {
                it.copy(name = nonEmpty("n"), updated_at = it.updated_at + drift())
            } + extra.medications,
            regimens = base.regimens.sometimes {
                it.copy(notes = text(), deleted_at = instant().takeIf { r.nextBoolean() }, updated_at = it.updated_at + drift())
            } + extra.regimens,
            doses = base.doses.sometimes { it.copy(notes = text(), updated_at = it.updated_at + drift()) } + extra.doses,
            types = base.types + extra.types,
            entries = base.entries.sometimes { it.copy(notes = text(), updated_at = it.updated_at + drift()) } + extra.entries,
            attachments = base.attachments.sometimes {
                it.copy(caption = text(), updated_at = it.updated_at + drift())
            } + extra.attachments,
            measurements = base.measurements + extra.measurements,
            exercises = base.exercises.sometimes {
                it.copy(activity = nonEmpty("a"), updated_at = it.updated_at + drift())
            },
            conditions = base.conditions + extra.conditions,
            analytes = base.analytes + extra.analytes,
            results = base.results + extra.results,
            moods = base.moods.sometimes {
                it.copy(id = uuid(), mood = 1L + r.nextInt(5), updated_at = it.updated_at + drift())
            },
            events = base.events.sometimes { it.copy(title = nonEmpty("t"), updated_at = it.updated_at + drift()) } + extra.events,
        )
    }

    private fun <T> List<T>.sometimes(change: (T) -> T): List<T> = map { if (r.nextBoolean()) change(it) else it }

    private fun drift(): Long = pick(-1_000L, 0L, 1_000L)

    private fun <T> pick(vararg options: T): T = options[r.nextInt(options.size)]

    private fun bit(): Long = if (r.nextBoolean()) 1L else 0L

    private fun maybe(): Boolean = r.nextBoolean()

    private fun text(): String = when (r.nextInt(5)) {
        0 -> ""
        1 -> "🌙 ç ã \"aspas\" 'simples' \n\t"
        2 -> String(CharArray(r.nextInt(12)) { (0x400 + r.nextInt(0x50)).toChar() })
        else -> String(CharArray(r.nextInt(24)) { (' ' + r.nextInt(95)) })
    }

    private fun nonEmpty(fallback: String): String = text().ifEmpty { fallback }

    private fun instant(): Long = r.nextLong(4_102_444_800_000L)

    private fun offset(): Long = r.nextLong(-MAX_OFFSET_SECONDS, MAX_OFFSET_SECONDS + 1)

    private fun deleted(): Long? = instant().takeIf { r.nextInt(10) < 3 }

    private fun day(): Long? = (10_000L + r.nextLong(20_000L)).takeIf { maybe() }

    private fun positive(): Double = 0.001 + r.nextDouble() * 1_000.0

    private fun uuid(): String {
        val msb = (r.nextLong() and -0xF001L) or 0x4000L
        val lsb = (r.nextLong() and Long.MAX_VALUE.shr(1)) or Long.MIN_VALUE
        return Uuid.fromLongs(msb, lsb).toString()
    }

    private fun created(): Pair<Long, Long> = instant().let { it to it + r.nextLong(1_000_000L) }

    private fun profile(): ProfileValues {
        val genital = if (maybe()) VocabularyChoice.Preset("DICK") else VocabularyChoice.Custom(text().ifBlank { "x" })
        val vocabulary = BodyVocabulary().with(
            BodyRegion.GENITAL,
            genital
        ).with(BodyRegion.CHEST, VocabularyChoice.Preset("CHEST"))
        return ProfileValues(
            displayName = text().takeIf { maybe() },
            heightCm = positive().takeIf { maybe() },
            hrtStartDate = day(),
            unitSystem = pick("METRIC", "IMPERIAL"),
            showBmi = bit(),
            vocabulary = Codecs.encodeVocabulary(vocabulary),
            updatedAt = instant(),
        )
    }

    private fun medication(): Medication {
        val (created, updated) = created()
        val concentration = positive().takeIf { maybe() }
        return Medication(
            id = uuid(),
            name = nonEmpty("m"),
            substance = text().takeIf { maybe() },
            default_route = pick("INTRAMUSCULAR", "ORAL", null),
            concentration_value = concentration,
            concentration_unit = concentration?.let { "MG_PER_ML" },
            is_builtin = 0L,
            is_hidden = bit(),
            created_at = created,
            updated_at = updated,
            deleted_at = deleted(),
        )
    }

    private fun schedule(): Schedule = when (r.nextInt(5)) {
        0 -> Schedule.IntervalDays(1 + r.nextInt(366))
        1 -> Schedule.Weekly(setOf(DayOfWeek.entries[r.nextInt(7)], DayOfWeek.MONDAY), 1 + r.nextInt(52))
        2 -> Schedule.AsNeeded
        3 -> Schedule.Stepped(listOf(r.nextInt(367), 1 + r.nextInt(366)), (1 + r.nextInt(366)).takeIf { maybe() })
        else -> Schedule.Custom(RecurrenceRule(RecurrenceRule.Frequency.MONTHLY, 1 + r.nextInt(12)))
    }

    private fun regimen(): Regimen {
        val (created, updated) = created()
        val start = 18_000L + r.nextLong(3_000L)
        val schedule = schedule()
        return Regimen(
            id = uuid(),
            medication_id = medicationIds[r.nextInt(medicationIds.size)],
            dose_value = positive(),
            dose_unit = pick("MG", "ML"),
            route = pick("INTRAMUSCULAR", "SUBCUTANEOUS"),
            schedule_type = schedule.type.name,
            schedule_config = Codecs.encodeSchedule(schedule),
            time_of_day = r.nextLong(1440L).takeIf { maybe() },
            start_date = start,
            end_date = (start + r.nextLong(500L)).takeIf { maybe() },
            is_active = bit(),
            notes = text().takeIf { maybe() },
            created_at = created,
            updated_at = updated,
            deleted_at = deleted(),
        )
    }

    private fun dose(regimens: List<String>): Dose_log {
        val (created, updated) = created()
        return Dose_log(
            id = uuid(),
            regimen_id = regimens.takeIf { it.isNotEmpty() && maybe() }?.let { it[r.nextInt(it.size)] },
            medication_id = medicationIds[r.nextInt(medicationIds.size)],
            dose_value = positive(),
            dose_unit = pick("MG", "ML", "IU"),
            route = pick("INTRAMUSCULAR", "TRANSDERMAL"),
            injection_site = pick("GLUTE_LEFT", "THIGH_RIGHT", "OTHER", null),
            taken_at = instant(),
            taken_at_offset_seconds = offset(),
            notes = text().takeIf { maybe() },
            created_at = created,
            updated_at = updated,
            deleted_at = deleted(),
        )
    }

    private fun type(): Body_change_type {
        val (created, updated) = created()
        val id = uuid()
        val unit = pick("CM", "HZ", null)
        return Body_change_type(
            id = id,
            code = "CUSTOM_$id",
            label_key = null,
            custom_label = nonEmpty("t"),
            category = pick("OTHER", "SKIN"),
            is_reversible = pick(0L, 1L, null),
            supports_measurement = if (unit == null) 0L else 1L,
            measurement_unit = unit,
            is_builtin = 0L,
            is_hidden = bit(),
            created_at = created,
            updated_at = updated,
            deleted_at = deleted(),
        )
    }

    private fun entry(): Body_change_entry {
        val (created, updated) = created()
        return Body_change_entry(
            id = uuid(),
            change_type_id = typeIds[r.nextInt(typeIds.size)],
            observed_at = instant(),
            observed_at_offset_seconds = offset(),
            intensity = r.nextLong(5L).takeIf { maybe() },
            measurement_value = positive().takeIf { maybe() },
            measurement_unit = pick("CM", null),
            notes = text().takeIf { maybe() },
            created_at = created,
            updated_at = updated,
            deleted_at = deleted(),
        )
    }

    private fun attachment(owner: String): Media_attachment {
        val (created, updated) = created()
        val path = MediaPaths.forNew(Uuid.parse(uuid()), pick("jpg", "png"))
        val captured = instant().takeIf { maybe() }
        return Media_attachment(
            id = uuid(),
            owner_type = "BODY_CHANGE_ENTRY",
            owner_id = owner,
            relative_path = path,
            mime_type = "image/jpeg",
            captured_at = captured,
            captured_at_offset_seconds = captured?.let { offset() },
            checksum_sha256 = sha(fileBytes(path)),
            caption = text().takeIf { maybe() },
            created_at = created,
            updated_at = updated,
            deleted_at = deleted(),
        )
    }

    private fun measurement(): Measurement {
        val (created, updated) = created()
        val type = pick("WEIGHT", "WAIST", "CUSTOM")
        return Measurement(
            id = uuid(),
            type = type,
            custom_label = if (type == "CUSTOM") nonEmpty("c") else null,
            value_ = (r.nextDouble() - 0.5) * 1e6,
            unit = pick("KG", "CM", "PERCENT"),
            measured_at = instant(),
            measured_at_offset_seconds = offset(),
            notes = text().takeIf { maybe() },
            created_at = created,
            updated_at = updated,
            deleted_at = deleted(),
        )
    }

    private fun exercise(): Exercise_session {
        val (created, updated) = created()
        return Exercise_session(
            id = uuid(),
            activity = nonEmpty("a"),
            duration_minutes = 1L + r.nextLong(600L),
            intensity = pick("LIGHT", "VIGOROUS"),
            occurred_at = instant(),
            occurred_at_offset_seconds = offset(),
            notes = text().takeIf { maybe() },
            created_at = created,
            updated_at = updated,
            deleted_at = deleted(),
        )
    }

    private fun condition(): Health_condition {
        val (created, updated) = created()
        val diagnosed = day()
        return Health_condition(
            id = uuid(),
            label = nonEmpty("c"),
            code = text().takeIf { maybe() },
            severity = pick("MILD", "UNKNOWN"),
            status = pick("ACTIVE", "RESOLVED"),
            diagnosed_at = diagnosed,
            resolved_at = diagnosed?.plus(r.nextLong(100L))?.takeIf { maybe() },
            affects_treatment = bit(),
            notes = text().takeIf { maybe() },
            created_at = created,
            updated_at = updated,
            deleted_at = deleted(),
        )
    }

    private fun analyte(): Lab_analyte {
        val (created, updated) = created()
        val id = uuid()
        return Lab_analyte(
            id = id,
            code = "CUSTOM_$id",
            label_key = null,
            custom_label = nonEmpty("a"),
            default_unit = text(),
            is_builtin = 0L,
            is_hidden = bit(),
            created_at = created,
            updated_at = updated,
            deleted_at = deleted(),
        )
    }

    private fun labResult(): Lab_result {
        val (created, updated) = created()
        val low = (r.nextDouble() * 100).takeIf { maybe() }
        return Lab_result(
            id = uuid(),
            analyte_id = analyteIds[r.nextInt(analyteIds.size)],
            value_ = r.nextDouble() * 1000,
            unit = text(),
            collected_at = instant(),
            collected_at_offset_seconds = offset(),
            reference_low = low,
            reference_high = low?.plus(r.nextDouble() * 100)?.takeIf { maybe() },
            lab_name = text().takeIf { maybe() },
            notes = text().takeIf { maybe() },
            created_at = created,
            updated_at = updated,
            deleted_at = deleted(),
        )
    }

    private fun mood(day: Long): Mood_log {
        val (created, updated) = created()
        return Mood_log(
            id = uuid(),
            entry_date = day,
            mood = 1L + r.nextInt(5),
            energy = 1L + r.nextInt(5),
            anxiety = (1L + r.nextInt(5)).takeIf { maybe() },
            dysphoria = (1L + r.nextInt(5)).takeIf { maybe() },
            sleep_hours = (r.nextDouble() * 24).takeIf { maybe() },
            note = text().takeIf { maybe() },
            tags = Codecs.encodeTags(List(r.nextInt(3)) { text() }),
            created_at = created,
            updated_at = updated,
            deleted_at = deleted(),
            relief = (1L + r.nextInt(5)).takeIf { maybe() },
            irritability = (1L + r.nextInt(5)).takeIf { maybe() },
            emotional_intensity = (1L + r.nextInt(5)).takeIf { maybe() },
        )
    }

    private fun event(): Calendar_event {
        val (created, updated) = created()
        val start = instant()
        val end = (start + r.nextLong(1_000_000L)).takeIf { maybe() }
        val completed = instant().takeIf { maybe() }
        return Calendar_event(
            id = uuid(),
            title = nonEmpty("t"),
            description = text().takeIf { maybe() },
            start_at = start,
            start_at_offset_seconds = offset(),
            end_at = end,
            end_at_offset_seconds = end?.let { offset() },
            is_all_day = bit(),
            category = pick("APPOINTMENT", "PERSONAL", "LAB"),
            source_type = "MANUAL",
            source_id = null,
            reminder_minutes_before = r.nextLong(1440L).takeIf { maybe() },
            recurrence_rule = pick("FREQ=WEEKLY;BYDAY=MO,TH", "FREQ=MONTHLY;COUNT=3", null),
            completed_at = completed,
            completed_at_offset_seconds = completed?.let { offset() },
            created_at = created,
            updated_at = updated,
            deleted_at = deleted(),
        )
    }
}
