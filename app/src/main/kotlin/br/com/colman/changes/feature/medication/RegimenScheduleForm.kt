// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.medication

import br.com.colman.changes.core.model.RecurrenceRule
import br.com.colman.changes.core.model.Schedule

// Tradução entre as opções de agenda da tela e os tipos do domínio (ADR 0007). "Diariamente",
// "mensal" e "trimestral" são atalhos: viram INTERVAL_DAYS(1) e CUSTOM_CRON com FREQ=MONTHLY.

// Um valor inválido aqui invalida o schedule mais tarde, pela própria checagem do DoseSchedule.
private const val INVALID_COUNT = -1

/** Meses do atalho "trimestral". */
internal const val QUARTER_MONTHS = 3

/** Intervalo de uma regra "a cada N meses" pura (sem dias, fim ou contagem); `null` nas demais. */
internal fun RecurrenceRule.simpleMonthlyInterval(): Int? = interval.takeIf {
    frequency == RecurrenceRule.Frequency.MONTHLY && byDay.isEmpty() && until == null && count == null
}

/** Agenda do domínio montada a partir do formulário. [loaded] guarda uma regra gravada que a tela não edita. */
internal fun RegimenEditUiState.toSchedule(loaded: Schedule?): Schedule = when (scheduleOption) {
    ScheduleOption.DAILY -> Schedule.IntervalDays(1)
    ScheduleOption.INTERVAL_DAYS -> Schedule.IntervalDays(intervalDays.number())
    ScheduleOption.WEEKLY -> Schedule.Weekly(weeklyDays, everyWeeks.number())
    ScheduleOption.MONTHLY -> monthly(everyMonths.number())
    ScheduleOption.QUARTERLY -> monthly(QUARTER_MONTHS)
    ScheduleOption.STEPPED -> Schedule.Stepped(
        steps = stepDays.map { it.number() },
        thenEvery = continuousEveryDays.number().takeIf { continuous },
    )
    ScheduleOption.AS_NEEDED -> Schedule.AsNeeded
    ScheduleOption.CUSTOM -> loaded ?: Schedule.AsNeeded
}

/** Campos do formulário preenchidos a partir de uma agenda gravada. */
internal fun RegimenEditUiState.withSchedule(schedule: Schedule): RegimenEditUiState = when (schedule) {
    is Schedule.IntervalDays -> if (schedule.days == 1) {
        copy(scheduleOption = ScheduleOption.DAILY)
    } else {
        copy(scheduleOption = ScheduleOption.INTERVAL_DAYS, intervalDays = schedule.days.toString())
    }
    is Schedule.Weekly -> copy(
        scheduleOption = ScheduleOption.WEEKLY,
        weeklyDays = schedule.daysOfWeek,
        everyWeeks = schedule.everyWeeks.toString(),
    )
    is Schedule.Stepped -> copy(
        scheduleOption = ScheduleOption.STEPPED,
        stepDays = schedule.steps.map { it.toString() },
        continuous = schedule.thenEvery != null,
        continuousEveryDays = schedule.thenEvery?.toString().orEmpty(),
    )
    is Schedule.Custom -> withCustom(schedule.rule)
    Schedule.AsNeeded -> copy(scheduleOption = ScheduleOption.AS_NEEDED)
}

private fun RegimenEditUiState.withCustom(rule: RecurrenceRule): RegimenEditUiState {
    val months = rule.simpleMonthlyInterval()
    return when (months) {
        null -> copy(scheduleOption = ScheduleOption.CUSTOM)
        QUARTER_MONTHS -> copy(scheduleOption = ScheduleOption.QUARTERLY)
        else -> copy(scheduleOption = ScheduleOption.MONTHLY, everyMonths = months.toString())
    }
}

private fun monthly(months: Int): Schedule = Schedule.Custom(RecurrenceRule(RecurrenceRule.Frequency.MONTHLY, months))

private fun String.number(): Int = trim().toIntOrNull() ?: INVALID_COUNT
