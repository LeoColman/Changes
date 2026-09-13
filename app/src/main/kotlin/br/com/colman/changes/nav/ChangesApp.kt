// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.nav

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

/** [startDestination] é o onboarding na primeira abertura e a aba Hoje depois (Seção 9). */
@Composable
fun ChangesApp(startDestination: Any, navController: NavHostController = rememberNavController()) {
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val onTopLevel = TopLevelTab.entries.any { destination?.hasRoute(it.route::class) == true }
    Scaffold(
        bottomBar = { if (onTopLevel) ChangesNavigationBar(destination, navController) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        ChangesNavHost(navController, startDestination, Modifier.padding(padding).consumeWindowInsets(padding))
    }
}

@Composable
private fun ChangesNavigationBar(destination: NavDestination?, navController: NavHostController) {
    NavigationBar {
        TopLevelTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = destination?.hierarchy?.any { it.hasRoute(tab.route::class) } == true,
                onClick = { navController.navigateToTab(tab.route) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
            )
        }
    }
}

/** Troca de aba como a barra inferior: uma instância por aba, com estado salvo e restaurado. */
internal fun NavHostController.navigateToTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
