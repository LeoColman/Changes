// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.nav

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import br.com.colman.changes.feature.calendar.CalendarNavigation
import br.com.colman.changes.feature.today.TodayNavigation
import br.com.colman.changes.feature.calendar.CalendarRoute as CalendarDestination
import br.com.colman.changes.feature.calendar.EventEditRoute as EventEditDestination
import br.com.colman.changes.feature.medication.DoseHistoryRoute as DoseHistoryDestination
import br.com.colman.changes.feature.medication.LogDoseRoute as LogDoseDestination
import br.com.colman.changes.feature.medication.RegimenEditRoute as RegimenEditDestination
import br.com.colman.changes.feature.medication.RegimenListRoute as RegimenListDestination
import br.com.colman.changes.feature.mood.MoodCheckInRoute as MoodCheckInDestination
import br.com.colman.changes.feature.mood.MoodHistoryRoute as MoodHistoryDestination
import br.com.colman.changes.feature.mood.SupportRoute as SupportDestination
import br.com.colman.changes.feature.today.TodayRoute as TodayDestination

// Uso diário: a aba Hoje, doses e regimes, calendário, check-in de humor e apoio.

internal fun NavGraphBuilder.todayGraph(navController: NavHostController) {
    composable<TodayRoute> {
        TodayDestination(
            navigation = TodayNavigation(
                onLogDoseWithDetails = { navController.navigate(LogDoseRoute(regimenId = it.toString())) },
                onEditDose = { navController.navigate(LogDoseRoute(doseLogId = it.toString())) },
                onCreateRegimen = { navController.navigate(RegimenEditRoute()) },
                onOpenMoodCheckIn = { navController.navigate(MoodCheckInRoute()) },
                onOpenEvent = { navController.navigate(EventEditRoute(eventId = it.toString())) },
                onOpenCalendar = { navController.navigateToTab(CalendarRoute) },
                onOpenRegimens = { navController.navigate(RegimenListRoute) },
                onOpenMoodHistory = { navController.navigate(MoodHistoryRoute) },
            ),
        )
    }
}

internal fun NavGraphBuilder.medicationGraph(navController: NavHostController) {
    composable<RegimenListRoute> {
        RegimenListDestination(
            onOpenRegimen = { navController.navigate(RegimenEditRoute(regimenId = it)) },
            onCreateRegimen = { navController.navigate(RegimenEditRoute()) },
            onOpenHistory = { navController.navigate(DoseHistoryRoute) },
            onBack = navController::popBackStack,
        )
    }
    composable<RegimenEditRoute> { entry ->
        RegimenEditDestination(
            regimenId = entry.toRoute<RegimenEditRoute>().regimenId,
            onDone = navController::popBackStack,
        )
    }
    composable<LogDoseRoute> { entry ->
        val route = entry.toRoute<LogDoseRoute>()
        LogDoseDestination(
            regimenId = route.regimenId,
            plannedEpochDay = route.plannedEpochDay,
            doseLogId = route.doseLogId,
            onDone = navController::popBackStack,
        )
    }
    composable<DoseHistoryRoute> {
        DoseHistoryDestination(
            onBack = navController::popBackStack,
            onEditLog = { navController.navigate(LogDoseRoute(doseLogId = it)) },
        )
    }
}

internal fun NavGraphBuilder.calendarGraph(navController: NavHostController) {
    composable<CalendarRoute> {
        CalendarDestination(
            navigation = CalendarNavigation(
                onLogDose = { regimenId, epochDay ->
                    navController.navigate(LogDoseRoute(regimenId = regimenId.toString(), plannedEpochDay = epochDay))
                },
                onEditDose = { navController.navigate(LogDoseRoute(doseLogId = it.toString())) },
                onEditEvent = { navController.navigate(EventEditRoute(eventId = it.toString())) },
                onNewEvent = { navController.navigate(EventEditRoute(epochDay = it)) },
                onOpenExpectedChanges = { navController.navigate(ExpectedChangesRoute) },
            ),
        )
    }
    composable<EventEditRoute> { entry ->
        val route = entry.toRoute<EventEditRoute>()
        EventEditDestination(eventId = route.eventId, epochDay = route.epochDay, onBack = navController::popBackStack)
    }
}

internal fun NavGraphBuilder.moodGraph(navController: NavHostController) {
    composable<MoodCheckInRoute> { entry ->
        MoodCheckInDestination(
            epochDay = entry.toRoute<MoodCheckInRoute>().epochDay,
            onBack = navController::popBackStack,
            onOpenSupport = { navController.navigate(SupportRoute) },
        )
    }
    composable<MoodHistoryRoute> {
        MoodHistoryDestination(
            onOpenDay = { navController.navigate(MoodCheckInRoute(epochDay = it)) },
            onOpenSupport = { navController.navigate(SupportRoute) },
            onBack = navController::popBackStack,
        )
    }
    composable<SupportRoute> { SupportDestination(onBack = navController::popBackStack) }
}
