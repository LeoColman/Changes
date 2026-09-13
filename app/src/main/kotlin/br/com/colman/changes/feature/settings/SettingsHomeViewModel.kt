// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.changes.platform.AppSettings
import br.com.colman.changes.platform.BiometricAvailability
import br.com.colman.changes.platform.SettingsStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalTime

private const val STOP_TIMEOUT_MILLIS = 5_000L
private const val MINUTES_PER_HOUR = 60

/**
 * Painel de Ajustes (Seções 7.9 e 9): aparência, lembretes e privacidade gravados direto no
 * [SettingsStore]. O `ReminderResyncer` reagenda lembretes quando as preferências mudam; esta tela não
 * chama `ReminderSync`.
 */
class SettingsHomeViewModel(
    private val settings: SettingsStore,
    private val biometricAvailability: BiometricAvailability,
) : ViewModel() {

    val state: StateFlow<SettingsHomeUiState> = settings.settings.map { it.toUiState() }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            settings.settings.value.toUiState()
        )

    fun onEvent(event: SettingsHomeUiEvent) {
        when (event) {
            is SettingsHomeUiEvent.ThemeModeChanged -> settings.update { it.copy(themeMode = event.mode) }
            is SettingsHomeUiEvent.DynamicColorChanged -> settings.update { it.copy(dynamicColor = event.enabled) }
            is SettingsHomeUiEvent.DoseRemindersEnabledChanged ->
                settings.update { it.copy(doseRemindersEnabled = event.enabled) }
            is SettingsHomeUiEvent.ReminderTextChanged ->
                settings.update { it.copy(reminderText = event.text.trim().ifBlank { null }) }
            is SettingsHomeUiEvent.MoodReminderEnabledChanged ->
                settings.update { it.copy(moodReminderEnabled = event.enabled) }
            is SettingsHomeUiEvent.MoodReminderTimeChanged -> changeMoodReminderTime(event.time)
            is SettingsHomeUiEvent.BiometricLockChanged -> changeBiometricLock(event.enabled)
        }
    }

    private fun changeMoodReminderTime(time: LocalTime) {
        val minutes = time.hour * MINUTES_PER_HOUR + time.minute
        settings.update { it.copy(moodReminderMinutes = minutes) }
    }

    /** Não liga se o aparelho não tem como autenticar (biometria ou credencial de tela). */
    private fun changeBiometricLock(enabled: Boolean) {
        if (enabled && !biometricAvailability.canAuthenticate()) return
        settings.update { it.copy(biometricLock = enabled) }
    }

    private fun AppSettings.toUiState() = SettingsHomeUiState(
        themeMode = themeMode,
        dynamicColor = dynamicColor,
        doseRemindersEnabled = doseRemindersEnabled,
        reminderText = reminderText.orEmpty(),
        moodReminderEnabled = moodReminderEnabled,
        moodReminderTime = LocalTime(moodReminderMinutes / MINUTES_PER_HOUR, moodReminderMinutes % MINUTES_PER_HOUR),
        biometricLock = biometricLock,
        biometricAvailable = biometricAvailability.canAuthenticate(),
    )
}
