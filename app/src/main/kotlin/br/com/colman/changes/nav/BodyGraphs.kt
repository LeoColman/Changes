// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.nav

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import kotlin.uuid.Uuid
import br.com.colman.changes.feature.body.BodyChangeTypeRoute as BodyChangeTypeDestination
import br.com.colman.changes.feature.body.BodyEntryEditRoute as BodyEntryEditDestination
import br.com.colman.changes.feature.body.BodyHomeRoute as BodyHomeDestination
import br.com.colman.changes.feature.expected.ExpectedChangesRoute as ExpectedChangesDestination
import br.com.colman.changes.feature.health.ConditionsRoute as ConditionsDestination
import br.com.colman.changes.feature.health.HealthHomeRoute as HealthHomeDestination
import br.com.colman.changes.feature.health.LabResultsRoute as LabResultsDestination
import br.com.colman.changes.feature.vitals.ExerciseRoute as ExerciseDestination
import br.com.colman.changes.feature.vitals.MeasurementsRoute as MeasurementsDestination

// Corpo e saúde: mudanças corporais, medidas e exercício, condições e exames.

internal fun NavGraphBuilder.bodyGraph(navController: NavHostController) {
    composable<BodyRoute> {
        BodyHomeDestination(
            onOpenExpectedChanges = { navController.navigate(ExpectedChangesRoute) },
            onOpenMeasurements = { navController.navigate(MeasurementsRoute) },
            onOpenChangeType = { navController.navigate(BodyChangeTypeRoute(it)) },
        )
    }
    composable<BodyChangeTypeRoute> { entry ->
        BodyChangeTypeDestination(
            typeId = entry.toRoute<BodyChangeTypeRoute>().typeId,
            onBack = navController::popBackStack,
            onAddEntry = { navController.navigate(BodyEntryEditRoute(typeId = it)) },
            onEditEntry = { typeId, entryId -> navController.navigate(BodyEntryEditRoute(typeId, entryId)) },
        )
    }
    composable<BodyEntryEditRoute> { entry ->
        val route = entry.toRoute<BodyEntryEditRoute>()
        BodyEntryEditDestination(
            typeId = route.typeId,
            entryId = route.entryId,
            onSaved = navController::popBackStack,
            onBack = navController::popBackStack,
        )
    }
    composable<ExpectedChangesRoute> {
        ExpectedChangesDestination(
            onBack = navController::popBackStack,
            onOpenProfile = { navController.navigate(ProfileRoute) },
        )
    }
}

internal fun NavGraphBuilder.vitalsGraph(navController: NavHostController) {
    composable<MeasurementsRoute> {
        MeasurementsDestination(
            onBack = navController::popBackStack,
            onOpenSettings = { navController.navigateToTab(SettingsRoute) },
        )
    }
    composable<ExerciseRoute> { ExerciseDestination(onBack = navController::popBackStack) }
}

internal fun NavGraphBuilder.healthGraph(navController: NavHostController) {
    composable<HealthRoute> {
        HealthHomeDestination(
            onOpenConditions = { navController.navigate(ConditionsRoute) },
            onOpenLabs = { navController.navigate(LabResultsRoute()) },
            onOpenRegimens = { navController.navigate(RegimenListRoute) },
            onOpenMeasurements = { navController.navigate(MeasurementsRoute) },
            onOpenExercise = { navController.navigate(ExerciseRoute) },
        )
    }
    composable<ConditionsRoute> { ConditionsDestination(onBack = navController::popBackStack) }
    composable<LabResultsRoute> { entry ->
        LabResultsDestination(
            analyteId = entry.toRoute<LabResultsRoute>().analyteId?.let(Uuid::parse),
            onBack = navController::popBackStack,
        )
    }
}
