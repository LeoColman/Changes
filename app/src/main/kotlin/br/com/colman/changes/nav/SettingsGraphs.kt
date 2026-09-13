// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.nav

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import br.com.colman.changes.feature.settings.SettingsNavigation
import br.com.colman.changes.feature.backup.BackupRoute as BackupDestination
import br.com.colman.changes.feature.backup.ImportPreviewRoute as ImportPreviewDestination
import br.com.colman.changes.feature.settings.AboutRoute as AboutDestination
import br.com.colman.changes.feature.settings.LicensesRoute as LicensesDestination
import br.com.colman.changes.feature.settings.OnboardingRoute as OnboardingDestination
import br.com.colman.changes.feature.settings.ProfileRoute as ProfileDestination
import br.com.colman.changes.feature.settings.SettingsHomeRoute as SettingsHomeDestination
import br.com.colman.changes.feature.settings.VocabularyRoute as VocabularyDestination
import br.com.colman.changes.feature.trash.TrashRoute as TrashDestination

// Ajustes: perfil, vocabulário, sobre, licenças, lixeira, onboarding e backup.

internal fun NavGraphBuilder.settingsGraph(navController: NavHostController) {
    composable<SettingsRoute> {
        SettingsHomeDestination(
            navigation = SettingsNavigation(
                onOpenProfile = { navController.navigate(ProfileRoute) },
                onOpenVocabulary = { navController.navigate(VocabularyRoute) },
                onOpenAbout = { navController.navigate(AboutRoute) },
                onOpenLicenses = { navController.navigate(LicensesRoute) },
                onOpenBackup = { navController.navigate(BackupRoute) },
                onOpenTrash = { navController.navigate(TrashRoute) },
                onOpenSupport = { navController.navigate(SupportRoute) },
            ),
        )
    }
    composable<ProfileRoute> { ProfileDestination(onBack = navController::popBackStack) }
    composable<VocabularyRoute> { VocabularyDestination(onBack = navController::popBackStack) }
    composable<AboutRoute> { AboutDestination(onBack = navController::popBackStack) }
    composable<LicensesRoute> { LicensesDestination(onBack = navController::popBackStack) }
    composable<TrashRoute> { TrashDestination(onBack = navController::popBackStack) }
    composable<OnboardingRoute> {
        // Ao terminar ou pular, o onboarding sai da pilha: voltar não o reabre (Seção 9).
        OnboardingDestination(
            onFinished = {
                navController.navigate(TodayRoute) { popUpTo(OnboardingRoute) { inclusive = true } }
            },
        )
    }
}

internal fun NavGraphBuilder.backupGraph(navController: NavHostController) {
    composable<BackupRoute> {
        BackupDestination(
            onBack = navController::popBackStack,
            onOpenPreview = { navController.navigate(ImportPreviewRoute) },
        )
    }
    composable<ImportPreviewRoute> {
        ImportPreviewDestination(onBack = navController::popBackStack, onDone = navController::popBackStack)
    }
}
