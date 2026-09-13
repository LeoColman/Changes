// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.nav

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.ui.graphics.vector.ImageVector
import br.com.colman.changes.R

/** As cinco abas da Seção 9. */
enum class TopLevelTab(val route: Any, @StringRes val label: Int, val icon: ImageVector) {
    TODAY(TodayRoute, R.string.tab_today, Icons.Outlined.Today),
    BODY(BodyRoute, R.string.tab_body, Icons.Outlined.Person),
    HEALTH(HealthRoute, R.string.tab_health, Icons.Outlined.Favorite),
    CALENDAR(CalendarRoute, R.string.tab_calendar, Icons.Outlined.CalendarMonth),
    SETTINGS(SettingsRoute, R.string.tab_settings, Icons.Outlined.Settings),
}
