// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.uuid.Uuid

public enum class ScheduleType { INTERVAL_DAYS, WEEKLY, AS_NEEDED, CUSTOM_CRON, STEPPED }

/**
 * Agenda de um regime. Dose fracionada semanal (ex.: 2x por semana) é [Weekly] com dois dias;
 * ver ADR 0007. [Custom] usa o mesmo subconjunto de RRULE do calendário. Intervalos que mudam de uma
 * dose para a outra (dose de ataque, depois manutenção) são [Stepped].
 */
public sealed interface Schedule {
    public val type: ScheduleType

    public data class IntervalDays(val days: Int) : Schedule {
        override val type: ScheduleType get() = ScheduleType.INTERVAL_DAYS
    }

    public data class Weekly(val daysOfWeek: Set<DayOfWeek>, val everyWeeks: Int = 1) : Schedule {
        override val type: ScheduleType get() = ScheduleType.WEEKLY
    }

    public data object AsNeeded : Schedule {
        override val type: ScheduleType get() = ScheduleType.AS_NEEDED
    }

    public data class Custom(val rule: RecurrenceRule) : Schedule {
        override val type: ScheduleType get() = ScheduleType.CUSTOM_CRON
    }

    /**
     * Cada item de [steps] é o número de dias desde a dose anterior; o primeiro conta a partir da data
     * de início, e 0 quer dizer uma dose no próprio início. Depois do último passo, [thenEvery] repete
     * a cada tantos dias (uso contínuo); `null` encerra a série. Ex.: primeira dose em 45 dias, a
     * segunda 90 dias depois, e daí a cada 90 dias é `Stepped(listOf(45, 90), thenEvery = 90)`.
     */
    public data class Stepped(val steps: List<Int>, val thenEvery: Int? = null) : Schedule {
        override val type: ScheduleType get() = ScheduleType.STEPPED
    }
}

public data class Regimen(
    val id: Uuid,
    val medicationId: Uuid,
    val dose: Dose,
    val route: Route,
    val schedule: Schedule,
    /** Hora local da dose. `null` = sem hora marcada (sem lembrete). */
    val timeOfDay: LocalTime?,
    val startDate: LocalDate,
    /** Inclusiva. `null` = regime sem fim. */
    val endDate: LocalDate?,
    val isActive: Boolean,
    val notes: String?,
)
