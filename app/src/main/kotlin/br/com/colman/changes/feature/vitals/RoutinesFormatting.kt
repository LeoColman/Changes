// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.vitals

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import br.com.colman.changes.R
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber

// Texto exibido para os enums do formulário de rotina (ADR 0011). Nada de texto fixo em código: tudo
// sai de strings_vitals.xml. Cópia própria dos rótulos de dia da semana e de lembrete: features não
// importam umas das outras, então não dá para reaproveitar os de `feature.calendar`.

@Composable
fun routineFrequencyLabel(frequency: RoutineFrequency): String = stringResource(
    when (frequency) {
        RoutineFrequency.DAILY -> R.string.vitals_frequency_daily
        RoutineFrequency.WEEKLY -> R.string.vitals_frequency_weekly
    },
)

@Composable
fun routineReminderLabel(option: RoutineReminderOption): String = stringResource(
    when (option) {
        RoutineReminderOption.NONE -> R.string.vitals_reminder_none
        RoutineReminderOption.AT_TIME -> R.string.vitals_reminder_at_time
        RoutineReminderOption.MINUTES_15 -> R.string.vitals_reminder_15_minutes
        RoutineReminderOption.HOUR_1 -> R.string.vitals_reminder_1_hour
    },
)

@Composable
fun routineWeekdayLabel(day: DayOfWeek): String = stringResource(
    when (day) {
        DayOfWeek.MONDAY -> R.string.vitals_weekday_monday
        DayOfWeek.TUESDAY -> R.string.vitals_weekday_tuesday
        DayOfWeek.WEDNESDAY -> R.string.vitals_weekday_wednesday
        DayOfWeek.THURSDAY -> R.string.vitals_weekday_thursday
        DayOfWeek.FRIDAY -> R.string.vitals_weekday_friday
        DayOfWeek.SATURDAY -> R.string.vitals_weekday_saturday
        DayOfWeek.SUNDAY -> R.string.vitals_weekday_sunday
    },
)

/**
 * Resumo de frequência de uma rotina (ADR 0011): "Diariamente", "Toda semana: Quarta",
 * "A cada 2 semanas: Segunda, Quinta".
 */
@Composable
fun routineFrequencySummary(routine: RoutineUiState): String = when (routine.frequency) {
    RoutineFrequency.DAILY -> stringResource(R.string.vitals_frequency_daily)
    RoutineFrequency.WEEKLY -> weeklyFrequencySummary(routine.interval, routine.weeklyDays)
}

@Composable
private fun weeklyFrequencySummary(interval: Int, days: Set<DayOfWeek>): String {
    // `joinToString` não é inline com `transform` (parâmetro nulável): mapear antes evita chamar um
    // Composable fora de contexto de composição.
    val dayLabels = days.sortedBy { it.isoDayNumber }.map { routineWeekdayLabel(it) }
    val daysLabel = dayLabels.joinToString(", ")
    return if (interval <= 1) {
        stringResource(R.string.vitals_routine_frequency_weekly_once, daysLabel)
    } else {
        stringResource(R.string.vitals_routine_frequency_weekly_interval, interval, daysLabel)
    }
}
