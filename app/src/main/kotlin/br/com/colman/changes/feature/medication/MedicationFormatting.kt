// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.medication

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import br.com.colman.changes.R
import br.com.colman.changes.core.model.DoseUnit
import br.com.colman.changes.core.model.InjectionSite
import br.com.colman.changes.core.model.RecurrenceRule
import br.com.colman.changes.core.model.Route
import br.com.colman.changes.core.model.Schedule
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.isoDayNumber

// Texto exibido para os enums do domínio (Seção 7.1). Nada de texto fixo em código: tudo sai de
// strings_medication.xml.

@Composable
fun routeLabel(route: Route): String = stringResource(
    when (route) {
        Route.INTRAMUSCULAR -> R.string.medication_route_intramuscular
        Route.SUBCUTANEOUS -> R.string.medication_route_subcutaneous
        Route.TRANSDERMAL -> R.string.medication_route_transdermal
        Route.ORAL -> R.string.medication_route_oral
        Route.SUBLINGUAL -> R.string.medication_route_sublingual
        Route.TOPICAL -> R.string.medication_route_topical
        Route.NASAL -> R.string.medication_route_nasal
        Route.OTHER -> R.string.medication_route_other
    },
)

@Composable
fun doseUnitLabel(unit: DoseUnit): String = stringResource(
    when (unit) {
        DoseUnit.MG -> R.string.medication_dose_unit_mg
        DoseUnit.ML -> R.string.medication_dose_unit_ml
        DoseUnit.IU -> R.string.medication_dose_unit_iu
        DoseUnit.G -> R.string.medication_dose_unit_g
        DoseUnit.PUFF -> R.string.medication_dose_unit_puff
        DoseUnit.PATCH -> R.string.medication_dose_unit_patch
    },
)

@Composable
fun injectionSiteLabel(site: InjectionSite): String = stringResource(
    when (site) {
        InjectionSite.THIGH_LEFT -> R.string.medication_site_thigh_left
        InjectionSite.THIGH_RIGHT -> R.string.medication_site_thigh_right
        InjectionSite.GLUTE_LEFT -> R.string.medication_site_glute_left
        InjectionSite.GLUTE_RIGHT -> R.string.medication_site_glute_right
        InjectionSite.DELTOID_LEFT -> R.string.medication_site_deltoid_left
        InjectionSite.DELTOID_RIGHT -> R.string.medication_site_deltoid_right
        InjectionSite.ABDOMEN_LEFT -> R.string.medication_site_abdomen_left
        InjectionSite.ABDOMEN_RIGHT -> R.string.medication_site_abdomen_right
        InjectionSite.OTHER -> R.string.medication_site_other
    },
)

@Composable
fun weekdayLabel(day: DayOfWeek): String = stringResource(
    when (day) {
        DayOfWeek.MONDAY -> R.string.medication_weekday_monday
        DayOfWeek.TUESDAY -> R.string.medication_weekday_tuesday
        DayOfWeek.WEDNESDAY -> R.string.medication_weekday_wednesday
        DayOfWeek.THURSDAY -> R.string.medication_weekday_thursday
        DayOfWeek.FRIDAY -> R.string.medication_weekday_friday
        DayOfWeek.SATURDAY -> R.string.medication_weekday_saturday
        DayOfWeek.SUNDAY -> R.string.medication_weekday_sunday
    },
)

@Composable
fun scheduleOptionLabel(option: ScheduleOption): String = stringResource(
    when (option) {
        ScheduleOption.DAILY -> R.string.medication_schedule_type_daily
        ScheduleOption.INTERVAL_DAYS -> R.string.medication_schedule_type_interval
        ScheduleOption.WEEKLY -> R.string.medication_schedule_type_weekly
        ScheduleOption.MONTHLY -> R.string.medication_schedule_type_monthly
        ScheduleOption.QUARTERLY -> R.string.medication_schedule_type_quarterly
        ScheduleOption.STEPPED -> R.string.medication_schedule_type_stepped
        ScheduleOption.AS_NEEDED -> R.string.medication_schedule_type_as_needed
        ScheduleOption.CUSTOM -> R.string.medication_schedule_type_custom
    },
)

/** Resumo legível de uma agenda (Seção 7.1), usado na lista de regimes e na edição. */
@Composable
fun scheduleSummary(schedule: Schedule): String = when (schedule) {
    is Schedule.IntervalDays -> intervalScheduleSummary(schedule.days)
    is Schedule.Weekly -> weeklyScheduleSummary(schedule)
    is Schedule.AsNeeded -> stringResource(R.string.medication_schedule_as_needed)
    is Schedule.Custom -> customScheduleSummary(schedule.rule)
    is Schedule.Stepped -> steppedScheduleSummary(schedule)
}

@Composable
private fun intervalScheduleSummary(days: Int): String = if (days == 1) {
    stringResource(R.string.medication_schedule_daily)
} else {
    pluralStringResource(R.plurals.medication_schedule_interval_days, days, days)
}

/** "A cada N meses" puro ganha nome; qualquer outra regra continua "agenda personalizada". */
@Composable
private fun customScheduleSummary(rule: RecurrenceRule): String {
    val months = rule.simpleMonthlyInterval()
    return when (months) {
        null -> stringResource(R.string.medication_schedule_custom)
        1 -> stringResource(R.string.medication_schedule_monthly)
        QUARTER_MONTHS -> stringResource(R.string.medication_schedule_quarterly)
        else -> pluralStringResource(R.plurals.medication_schedule_every_months, months, months)
    }
}

@Composable
private fun steppedScheduleSummary(schedule: Schedule.Stepped): String {
    val steps = schedule.steps.joinToString(", ")
    val every = schedule.thenEvery
    return if (every == null) {
        stringResource(R.string.medication_schedule_stepped, steps)
    } else {
        pluralStringResource(R.plurals.medication_schedule_stepped_then, every, steps, every)
    }
}

@Composable
private fun weeklyScheduleSummary(schedule: Schedule.Weekly): String {
    val labels = schedule.daysOfWeek.sortedBy { it.isoDayNumber }.map { weekdayLabel(it) }
    val days = labels.joinToString(", ")
    return if (schedule.everyWeeks <= 1) {
        stringResource(R.string.medication_schedule_weekly, days)
    } else {
        stringResource(R.string.medication_schedule_weekly_every, schedule.everyWeeks, days)
    }
}
