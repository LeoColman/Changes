// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.colman.changes.R
import br.com.colman.changes.ui.components.ChangesScreen
import br.com.colman.changes.ui.components.DropdownField
import br.com.colman.changes.ui.components.SectionHeader
import br.com.colman.changes.ui.components.TimeField
import br.com.colman.changes.ui.theme.ThemeMode

/** Painel de Ajustes, sem estado próprio de negócio (Seção 5): perfil, vocabulário, aparência,
 * lembretes, privacidade, backup, lixeira, sobre e licenças. */
@Composable
fun SettingsHomeScreen(
    state: SettingsHomeUiState,
    onEvent: (SettingsHomeUiEvent) -> Unit,
    navigation: SettingsNavigation,
    modifier: Modifier = Modifier,
) {
    ChangesScreen(title = stringResource(R.string.tab_settings)) { padding ->
        LazyColumn(modifier = modifier.padding(padding).fillMaxSize()) {
            item { NavEntry(stringResource(R.string.settings_section_profile), navigation.onOpenProfile) }
            item { NavEntry(stringResource(R.string.settings_section_vocabulary), navigation.onOpenVocabulary) }

            item { SectionHeader(stringResource(R.string.settings_section_appearance)) }
            item { AppearanceSection(state, onEvent) }

            item { SectionHeader(stringResource(R.string.settings_section_reminders)) }
            item { RemindersSection(state, onEvent) }

            item { SectionHeader(stringResource(R.string.settings_section_privacy)) }
            item { PrivacySection(state, onEvent) }
            item { NavEntry(stringResource(R.string.settings_backup_entry), navigation.onOpenBackup) }
            item { NavEntry(stringResource(R.string.settings_trash_entry), navigation.onOpenTrash) }

            item { SectionHeader(stringResource(R.string.settings_section_about)) }
            item { NavEntry(stringResource(R.string.settings_about_entry), navigation.onOpenAbout) }
            item { NavEntry(stringResource(R.string.settings_licenses_entry), navigation.onOpenLicenses) }

            item { SupportFooter(navigation.onOpenSupport) }
        }
    }
}

@Composable
private fun NavEntry(label: String, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(label) }, modifier = Modifier.fillMaxWidth().clickable(onClick = onClick))
}

@Composable
private fun AppearanceSection(state: SettingsHomeUiState, onEvent: (SettingsHomeUiEvent) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        DropdownField(
            options = ThemeMode.entries,
            selected = state.themeMode,
            onSelect = { onEvent(SettingsHomeUiEvent.ThemeModeChanged(it)) },
            label = stringResource(R.string.settings_appearance_theme),
            optionLabel = { stringResource(themeModeLabelRes(it)) },
        )
        ToggleRow(
            label = stringResource(R.string.settings_appearance_dynamic_color),
            checked = state.dynamicColor,
            onCheckedChange = { onEvent(SettingsHomeUiEvent.DynamicColorChanged(it)) },
        )
    }
}

@Composable
private fun RemindersSection(state: SettingsHomeUiState, onEvent: (SettingsHomeUiEvent) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        ToggleRow(
            label = stringResource(R.string.settings_dose_reminders),
            checked = state.doseRemindersEnabled,
            onCheckedChange = { onEvent(SettingsHomeUiEvent.DoseRemindersEnabledChanged(it)) },
        )
        OutlinedTextField(
            value = state.reminderText,
            onValueChange = { onEvent(SettingsHomeUiEvent.ReminderTextChanged(it)) },
            label = { Text(stringResource(R.string.settings_reminder_text_label)) },
            placeholder = { Text(stringResource(R.string.mood_reminder_default_text)) },
            modifier = Modifier.fillMaxWidth(),
        )
        ToggleRow(
            label = stringResource(R.string.settings_mood_reminder),
            checked = state.moodReminderEnabled,
            onCheckedChange = { onEvent(SettingsHomeUiEvent.MoodReminderEnabledChanged(it)) },
        )
        if (state.moodReminderEnabled) {
            TimeField(
                time = state.moodReminderTime,
                onTimeChange = { onEvent(SettingsHomeUiEvent.MoodReminderTimeChanged(it)) },
                label = stringResource(R.string.settings_mood_reminder_time),
            )
        }
    }
}

@Composable
private fun PrivacySection(state: SettingsHomeUiState, onEvent: (SettingsHomeUiEvent) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        ToggleRow(
            label = stringResource(R.string.settings_biometric_lock),
            checked = state.biometricLock,
            enabled = state.biometricAvailable,
            onCheckedChange = { onEvent(SettingsHomeUiEvent.BiometricLockChanged(it)) },
        )
        if (!state.biometricAvailable) {
            Text(
                text = stringResource(R.string.settings_biometric_unavailable),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun SupportFooter(onOpenSupport: () -> Unit) {
    TextButton(onClick = onOpenSupport, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Text(stringResource(R.string.support_entry))
    }
}

private fun themeModeLabelRes(mode: ThemeMode): Int = when (mode) {
    ThemeMode.SYSTEM -> R.string.settings_theme_system
    ThemeMode.LIGHT -> R.string.settings_theme_light
    ThemeMode.DARK -> R.string.settings_theme_dark
}
