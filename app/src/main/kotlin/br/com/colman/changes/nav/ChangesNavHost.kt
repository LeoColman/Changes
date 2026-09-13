// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost

/**
 * Grafo de navegação. Cada feature entra com o seu `XRoute` e recebe callbacks de navegação; nenhuma
 * feature conhece rota de outra (Seção 4.2). Os grafos de cada área ficam em `DailyGraphs.kt`,
 * `BodyGraphs.kt` e `SettingsGraphs.kt`. O composable de uma feature tem o mesmo nome da rota, então
 * é importado com alias `XDestination`.
 */
@Composable
fun ChangesNavHost(navController: NavHostController, startDestination: Any, modifier: Modifier = Modifier) {
    NavHost(navController = navController, startDestination = startDestination, modifier = modifier) {
        todayGraph(navController)
        medicationGraph(navController)
        calendarGraph(navController)
        moodGraph(navController)
        bodyGraph(navController)
        vitalsGraph(navController)
        healthGraph(navController)
        settingsGraph(navController)
        backupGraph(navController)
    }
}
