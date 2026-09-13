// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import br.com.colman.changes.core.model.BodyRegion
import br.com.colman.changes.core.model.BodyVocabulary
import br.com.colman.changes.core.model.DoseSchedule
import br.com.colman.changes.core.model.RecurrenceRule
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.model.Schedule
import br.com.colman.changes.core.model.ScheduleType
import br.com.colman.changes.core.model.VocabularyChoice
import br.com.colman.changes.core.model.asSuccess
import br.com.colman.changes.core.model.getOrNull
import kotlinx.datetime.DayOfWeek
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * JSON das colunas estruturadas (`schedule_config`, `body_vocabulary`, `tags`).
 * Formato estável: faz parte do backup.
 */
internal object Codecs {
    private val json = Json {
        explicitNulls = false
        ignoreUnknownKeys = false
    }

    @Serializable
    private data class ScheduleConfig(
        val days: Int? = null,
        val daysOfWeek: List<String>? = null,
        val everyWeeks: Int? = null,
        val rrule: String? = null,
        val steps: List<Int>? = null,
        val thenEvery: Int? = null,
    )

    @Serializable
    private data class Choice(val preset: String? = null, val custom: String? = null)

    private val vocabularySerializer = MapSerializer(String.serializer(), Choice.serializer())
    private val tagsSerializer = ListSerializer(String.serializer())

    fun encodeSchedule(schedule: Schedule): String = json.encodeToString(
        ScheduleConfig.serializer(),
        when (schedule) {
            is Schedule.IntervalDays -> ScheduleConfig(days = schedule.days)
            is Schedule.Weekly -> ScheduleConfig(
                daysOfWeek = schedule.daysOfWeek.sortedBy { it.ordinal }.map { it.name },
                everyWeeks = schedule.everyWeeks,
            )
            is Schedule.Custom -> ScheduleConfig(rrule = schedule.rule.format())
            is Schedule.Stepped -> ScheduleConfig(steps = schedule.steps, thenEvery = schedule.thenEvery)
            else -> ScheduleConfig()
        },
    )

    /** `null` quando o JSON não corresponde ao tipo (dado corrompido ou adulterado). */
    fun decodeSchedule(type: ScheduleType, config: String): Schedule? {
        val parsed = try {
            json.decodeFromString(ScheduleConfig.serializer(), config)
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        return scheduleFrom(type, parsed)
    }

    private fun scheduleFrom(type: ScheduleType, parsed: ScheduleConfig): Schedule? = when (type) {
        ScheduleType.INTERVAL_DAYS -> parsed.days?.let { Schedule.IntervalDays(it) }
        ScheduleType.WEEKLY -> weekly(parsed)
        ScheduleType.AS_NEEDED -> Schedule.AsNeeded
        ScheduleType.CUSTOM_CRON ->
            parsed.rrule
                ?.let { rrule -> RecurrenceRule.parse(rrule).getOrNull() }
                ?.let { rule -> Schedule.Custom(rule) }
        // Validada já na leitura: um `thenEvery` zero ou negativo vindo de backup adulterado faria a
        // expansão da série andar para sempre.
        ScheduleType.STEPPED -> parsed.steps?.let {
            DoseSchedule.validate(Schedule.Stepped(it, parsed.thenEvery)).getOrNull()
        }
    }

    private fun weekly(config: ScheduleConfig): Schedule? {
        val names = config.daysOfWeek ?: return null
        val days = names.mapNotNull { name -> DayOfWeek.entries.firstOrNull { it.name == name } }
        return if (days.size == names.size) Schedule.Weekly(days.toSet(), config.everyWeeks ?: 1) else null
    }

    fun encodeVocabulary(vocabulary: BodyVocabulary): String = json.encodeToString(
        vocabularySerializer,
        vocabulary.choices.entries.sortedBy { it.key.ordinal }.associate { (region, choice) ->
            region.name to if (choice is VocabularyChoice.Preset) {
                Choice(preset = choice.option)
            } else {
                Choice(custom = (choice as VocabularyChoice.Custom).text)
            }
        },
    )

    /** JSON malformado ou região desconhecida caem no vocabulário default: nunca derruba o app. */
    fun decodeVocabulary(text: String): BodyVocabulary {
        val raw = try {
            json.decodeFromString(vocabularySerializer, text)
        } catch (_: SerializationException) {
            return BodyVocabulary()
        } catch (_: IllegalArgumentException) {
            return BodyVocabulary()
        }
        val choices = raw.mapNotNull { (name, choice) ->
            val region = BodyRegion.entries.firstOrNull { it.name == name }
            val decoded = choice.preset?.let { VocabularyChoice.Preset(it) }
                ?: choice.custom?.let { VocabularyChoice.Custom(it) }
            if (region != null && decoded != null) region to decoded else null
        }.toMap()
        return BodyVocabulary(choices)
    }

    fun encodeTags(tags: List<String>): String = json.encodeToString(tagsSerializer, tags)

    fun decodeTags(text: String): Result<List<String>> = try {
        json.decodeFromString(tagsSerializer, text).asSuccess()
    } catch (_: SerializationException) {
        invalidTags()
    } catch (_: IllegalArgumentException) {
        invalidTags()
    }

    private fun invalidTags(): Result<Nothing> =
        br.com.colman.changes.core.model.DomainError.Invalid(
            "tags",
            br.com.colman.changes.core.model.DomainError.Reason.OUT_OF_RANGE
        )
            .let { Result.Failure(it) }
}
