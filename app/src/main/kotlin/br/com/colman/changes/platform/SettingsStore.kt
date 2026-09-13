// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.platform

import android.content.Context
import android.content.SharedPreferences
import br.com.colman.changes.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Escolhas do aparelho, fora do banco e do backup (ADR 0006, item 14). Nenhum dado de saúde mora
 * aqui.
 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val biometricLock: Boolean = false,
    /** Texto do lembrete de dose. `null` = texto neutro padrão ("Lembrete"), Seção 7.9. */
    val reminderText: String? = null,
    val doseRemindersEnabled: Boolean = true,
    val moodReminderEnabled: Boolean = false,
    /** Minutos desde a meia-noite local. */
    val moodReminderMinutes: Int = DEFAULT_MOOD_REMINDER,
    val showMilestones: Boolean = true,
    val onboardingDone: Boolean = false,
) {
    companion object {
        const val DEFAULT_MOOD_REMINDER: Int = 20 * 60
    }
}

/** Features dependem desta interface; a implementação usa SharedPreferences privadas. */
interface SettingsStore {
    val settings: StateFlow<AppSettings>

    fun update(transform: (AppSettings) -> AppSettings)
}

class SharedPreferencesSettingsStore(context: Context) : SettingsStore {
    private val prefs: SharedPreferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    private val state = MutableStateFlow(read())

    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    override fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(state.value)
        prefs.edit()
            .putString(THEME, next.themeMode.name)
            .putBoolean(DYNAMIC, next.dynamicColor)
            .putBoolean(BIOMETRIC, next.biometricLock)
            .putString(REMINDER_TEXT, next.reminderText)
            .putBoolean(DOSE_REMINDERS, next.doseRemindersEnabled)
            .putBoolean(MOOD_REMINDER, next.moodReminderEnabled)
            .putInt(MOOD_REMINDER_MINUTES, next.moodReminderMinutes)
            .putBoolean(MILESTONES, next.showMilestones)
            .putBoolean(ONBOARDING, next.onboardingDone)
            .apply()
        state.value = next
    }

    private fun read(): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            themeMode = prefs.getString(THEME, null)?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                ?: defaults.themeMode,
            dynamicColor = prefs.getBoolean(DYNAMIC, defaults.dynamicColor),
            biometricLock = prefs.getBoolean(BIOMETRIC, defaults.biometricLock),
            reminderText = prefs.getString(REMINDER_TEXT, null),
            doseRemindersEnabled = prefs.getBoolean(DOSE_REMINDERS, defaults.doseRemindersEnabled),
            moodReminderEnabled = prefs.getBoolean(MOOD_REMINDER, defaults.moodReminderEnabled),
            moodReminderMinutes = prefs.getInt(MOOD_REMINDER_MINUTES, defaults.moodReminderMinutes),
            showMilestones = prefs.getBoolean(MILESTONES, defaults.showMilestones),
            onboardingDone = prefs.getBoolean(ONBOARDING, defaults.onboardingDone),
        )
    }

    private companion object {
        const val FILE = "device_settings"
        const val THEME = "theme_mode"
        const val DYNAMIC = "dynamic_color"
        const val BIOMETRIC = "biometric_lock"
        const val REMINDER_TEXT = "reminder_text"
        const val DOSE_REMINDERS = "dose_reminders"
        const val MOOD_REMINDER = "mood_reminder"
        const val MOOD_REMINDER_MINUTES = "mood_reminder_minutes"
        const val MILESTONES = "show_milestones"
        const val ONBOARDING = "onboarding_done"
    }
}
