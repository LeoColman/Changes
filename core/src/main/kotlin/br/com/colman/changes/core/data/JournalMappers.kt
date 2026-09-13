// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.data

import br.com.colman.changes.core.db.sql.Calendar_event
import br.com.colman.changes.core.db.sql.Mood_log
import br.com.colman.changes.core.model.CalendarCategory
import br.com.colman.changes.core.model.CalendarEvent
import br.com.colman.changes.core.model.EventSourceType
import br.com.colman.changes.core.model.MoodLog
import br.com.colman.changes.core.model.RecordedTime
import br.com.colman.changes.core.model.RecurrenceRule
import br.com.colman.changes.core.model.getOrNull

internal fun Mood_log.toModel(): MoodLog = MoodLog(
    id = id.asUuid(),
    date = entry_date.asLocalDate(),
    mood = mood.toInt(),
    energy = energy.toInt(),
    anxiety = anxiety?.toInt(),
    dysphoria = dysphoria?.toInt(),
    sleepHours = sleep_hours,
    note = note,
    tags = checkNotNull(Codecs.decodeTags(tags).getOrNull()) { "Corrupted tags for mood log $id" },
)

internal fun MoodLog.toRow(createdAt: Long, updatedAt: Long): Mood_log = Mood_log(
    id = id.toString(),
    entry_date = date.asEpochDay(),
    mood = mood.toLong(),
    energy = energy.toLong(),
    anxiety = anxiety?.toLong(),
    dysphoria = dysphoria?.toLong(),
    sleep_hours = sleepHours,
    note = note,
    tags = Codecs.encodeTags(tags),
    created_at = createdAt,
    updated_at = updatedAt,
    deleted_at = null,
)

internal fun Calendar_event.toModel(): CalendarEvent = CalendarEvent(
    id = id.asUuid(),
    title = title,
    description = description,
    start = RecordedTime.fromDb(start_at, start_at_offset_seconds),
    end = recordedOrNull(end_at, end_at_offset_seconds),
    isAllDay = is_all_day.asBoolean(),
    category = CalendarCategory.valueOf(category),
    sourceType = EventSourceType.valueOf(source_type),
    sourceId = source_id,
    reminderMinutesBefore = reminder_minutes_before?.toInt(),
    recurrence = recurrence_rule?.let {
        checkNotNull(RecurrenceRule.parse(it).getOrNull()) { "Corrupted rule for event $id" }
    },
    completedAt = recordedOrNull(completed_at, completed_at_offset_seconds),
)

internal fun CalendarEvent.toRow(createdAt: Long, updatedAt: Long): Calendar_event = Calendar_event(
    id = id.toString(),
    title = title,
    description = description,
    start_at = start.epochMillis,
    start_at_offset_seconds = start.offsetSeconds,
    end_at = end?.epochMillis,
    end_at_offset_seconds = end?.offsetSeconds,
    is_all_day = isAllDay.asLong(),
    category = category.name,
    source_type = sourceType.name,
    source_id = sourceId,
    reminder_minutes_before = reminderMinutesBefore?.toLong(),
    recurrence_rule = recurrence?.format(),
    completed_at = completedAt?.epochMillis,
    completed_at_offset_seconds = completedAt?.offsetSeconds,
    created_at = createdAt,
    updated_at = updatedAt,
    deleted_at = null,
)
