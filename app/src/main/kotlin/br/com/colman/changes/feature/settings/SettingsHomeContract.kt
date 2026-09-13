// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.settings

import androidx.compose.runtime.Immutable
import br.com.colman.changes.ui.theme.ThemeMode
import kotlinx.datetime.LocalTime

private val DEFAULT_MOOD_REMINDER_TIME = LocalTime(20, 0)

/** Estado do painel de Ajustes (Seções 7.9 e 9): aparência, lembretes e privacidade. */
@Immutable
data class SettingsHomeUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val doseRemindersEnabled: Boolean = true,
    val reminderText: String = "",
    val moodReminderEnabled: Boolean = false,
    val moodReminderTime: LocalTime = DEFAULT_MOOD_REMINDER_TIME,
    val biometricLock: Boolean = false,
    val biometricAvailable: Boolean = true,
)

/** Ações do painel de Ajustes: cada uma grava direto no `SettingsStore`. */
sealed interface SettingsHomeUiEvent {
    data class ThemeModeChanged(val mode: ThemeMode) : SettingsHomeUiEvent

    data class DynamicColorChanged(val enabled: Boolean) : SettingsHomeUiEvent

    data class DoseRemindersEnabledChanged(val enabled: Boolean) : SettingsHomeUiEvent

    data class ReminderTextChanged(val text: String) : SettingsHomeUiEvent

    data class MoodReminderEnabledChanged(val enabled: Boolean) : SettingsHomeUiEvent

    data class MoodReminderTimeChanged(val time: LocalTime) : SettingsHomeUiEvent

    data class BiometricLockChanged(val enabled: Boolean) : SettingsHomeUiEvent
}

/**
 * Callbacks de navegação do painel de Ajustes, agrupados para não violar o limite de parâmetros do
 * Detekt (`LongParameterList`, isento em `data class`). O grafo (`nav/`) os liga.
 */
data class SettingsNavigation(
    val onOpenProfile: () -> Unit,
    val onOpenVocabulary: () -> Unit,
    val onOpenAbout: () -> Unit,
    val onOpenLicenses: () -> Unit,
    val onOpenBackup: () -> Unit,
    val onOpenTrash: () -> Unit,
    val onOpenSupport: () -> Unit,
)
