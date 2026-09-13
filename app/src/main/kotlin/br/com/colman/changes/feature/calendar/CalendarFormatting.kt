// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.calendar

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import br.com.colman.changes.R
import br.com.colman.changes.core.model.CalendarCategory
import br.com.colman.changes.core.model.DomainError
import kotlinx.datetime.DayOfWeek

// Texto exibido para os enums da feature (Seção 7.9). Nada de texto fixo em código: tudo sai de
// strings_calendar.xml.

@Composable
fun categoryLabel(category: CalendarCategory): String = stringResource(
    when (category) {
        CalendarCategory.DOSE -> R.string.calendar_category_dose
        CalendarCategory.APPOINTMENT -> R.string.calendar_category_appointment
        CalendarCategory.LAB -> R.string.calendar_category_lab
        CalendarCategory.SURGERY -> R.string.calendar_category_surgery
        CalendarCategory.PERSONAL -> R.string.calendar_category_personal
        CalendarCategory.EXERCISE -> R.string.calendar_category_exercise
        CalendarCategory.OTHER -> R.string.calendar_category_other
    },
)

@Composable
fun reminderLabel(option: ReminderOption): String = stringResource(
    when (option) {
        ReminderOption.NONE -> R.string.calendar_reminder_none
        ReminderOption.AT_TIME -> R.string.calendar_reminder_at_time
        ReminderOption.MINUTES_15 -> R.string.calendar_reminder_15_minutes
        ReminderOption.HOUR_1 -> R.string.calendar_reminder_1_hour
        ReminderOption.DAY_1 -> R.string.calendar_reminder_1_day
    },
)

@Composable
fun recurrenceFrequencyLabel(frequency: EventRecurrenceFrequency): String = stringResource(
    when (frequency) {
        EventRecurrenceFrequency.NONE -> R.string.calendar_recurrence_none
        EventRecurrenceFrequency.DAILY -> R.string.calendar_recurrence_daily
        EventRecurrenceFrequency.WEEKLY -> R.string.calendar_recurrence_weekly
        EventRecurrenceFrequency.MONTHLY -> R.string.calendar_recurrence_monthly
        EventRecurrenceFrequency.YEARLY -> R.string.calendar_recurrence_yearly
    },
)

@Composable
fun recurrenceEndOptionLabel(option: RecurrenceEndOption): String = stringResource(
    when (option) {
        RecurrenceEndOption.NEVER -> R.string.calendar_recurrence_end_never
        RecurrenceEndOption.ON_DATE -> R.string.calendar_recurrence_end_on_date
        RecurrenceEndOption.AFTER_COUNT -> R.string.calendar_recurrence_end_after_count
    },
)

@Composable
fun calendarWeekdayLabel(day: DayOfWeek): String = stringResource(
    when (day) {
        DayOfWeek.MONDAY -> R.string.calendar_weekday_monday
        DayOfWeek.TUESDAY -> R.string.calendar_weekday_tuesday
        DayOfWeek.WEDNESDAY -> R.string.calendar_weekday_wednesday
        DayOfWeek.THURSDAY -> R.string.calendar_weekday_thursday
        DayOfWeek.FRIDAY -> R.string.calendar_weekday_friday
        DayOfWeek.SATURDAY -> R.string.calendar_weekday_saturday
        DayOfWeek.SUNDAY -> R.string.calendar_weekday_sunday
    },
)

@Composable
fun markerLabel(kind: CalendarItemKind): String = stringResource(
    when (kind) {
        CalendarItemKind.PLANNED_DOSE -> R.string.calendar_marker_planned_dose
        CalendarItemKind.LOGGED_DOSE -> R.string.calendar_marker_logged_dose
        CalendarItemKind.EVENT -> R.string.calendar_marker_event
        CalendarItemKind.MILESTONE -> R.string.calendar_marker_milestone
    },
)

/** Texto neutro para um erro de domínio (Seção 9): nenhuma palavra de julgamento ou alarme. */
@Composable
fun DomainError.calendarMessage(): String = when (this) {
    is DomainError.Invalid -> stringResource(reasonMessageRes(reason))
    // NotFound e recusa de backup não vêm do fluxo de edição de evento; o ramo existe porque o tipo é selado.
    is DomainError.NotFound, is DomainError.BackupRejected -> stringResource(R.string.calendar_error_not_found)
}

private fun reasonMessageRes(reason: DomainError.Reason): Int = when (reason) {
    DomainError.Reason.REQUIRED -> R.string.calendar_error_required
    DomainError.Reason.END_BEFORE_START -> R.string.calendar_error_end_before_start
    DomainError.Reason.OUT_OF_RANGE -> R.string.calendar_error_out_of_range
    DomainError.Reason.NOT_FINITE,
    DomainError.Reason.NOT_POSITIVE,
    DomainError.Reason.IN_THE_FUTURE,
    DomainError.Reason.BUILTIN_IMMUTABLE,
    DomainError.Reason.DUPLICATE,
    -> R.string.calendar_error_generic
}
