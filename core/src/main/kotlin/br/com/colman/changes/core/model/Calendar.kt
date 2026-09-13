// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.model

import kotlinx.datetime.LocalDate
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** `EXERCISE` marca as rotinas de exercício, que são eventos recorrentes do calendário (ADR 0011). */
public enum class CalendarCategory { DOSE, APPOINTMENT, LAB, SURGERY, PERSONAL, EXERCISE, OTHER }

public enum class EventSourceType { MANUAL, DERIVED }

public data class CalendarEvent(
    val id: Uuid,
    val title: String,
    val description: String?,
    val start: RecordedTime,
    val end: RecordedTime?,
    /** Dia inteiro: a data é `start.localDate` (offset gravado) e nunca muda com o fuso do aparelho. */
    val isAllDay: Boolean,
    val category: CalendarCategory,
    val sourceType: EventSourceType,
    val sourceId: String?,
    val reminderMinutesBefore: Int?,
    val recurrence: RecurrenceRule?,
    val completedAt: RecordedTime?,
)

/** Item da agenda unificada (Seção 7.9). Doses previstas são derivadas em tempo de consulta. */
public sealed interface AgendaItem {
    public val date: LocalDate

    /** Dose prevista de um regime ativo. [at] é `null` quando o regime não tem hora marcada. */
    public data class PlannedDose(
        val regimenId: Uuid,
        val medicationId: Uuid,
        override val date: LocalDate,
        val at: Instant?,
    ) : AgendaItem

    public data class LoggedDose(val log: DoseLog) : AgendaItem {
        override val date: LocalDate get() = log.takenAt.localDate
    }

    /** Uma ocorrência de evento manual (eventos recorrentes geram uma por data). */
    public data class Event(val event: CalendarEvent, override val date: LocalDate) : AgendaItem

    /** Início da janela típica de uma mudança do dataset clínico (camada desligável). */
    public data class Milestone(val changeTypeCode: String, override val date: LocalDate) : AgendaItem
}
