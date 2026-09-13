// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.settings

import app.cash.turbine.test
import br.com.colman.changes.core.testing.MainDispatcherListener
import br.com.colman.changes.ui.theme.ThemeMode
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalTime

private class SettingsHomeFixture(biometricAvailable: Boolean = true) {
    val settingsStore = FakeSettingsStore()
    val biometric = FakeBiometricAvailability(biometricAvailable)
    val viewModel = SettingsHomeViewModel(settingsStore, biometric)
}

class SettingsHomeViewModelSpec : FunSpec({
    register(MainDispatcherListener())

    test("mudar o tema grava no SettingsStore") {
        val fixture = SettingsHomeFixture()

        fixture.viewModel.onEvent(SettingsHomeUiEvent.ThemeModeChanged(ThemeMode.DARK))

        fixture.settingsStore.settings.value.themeMode shouldBe ThemeMode.DARK
    }

    test("ligar e desligar cor dinâmica grava no SettingsStore") {
        val fixture = SettingsHomeFixture()

        fixture.viewModel.onEvent(SettingsHomeUiEvent.DynamicColorChanged(false))

        fixture.settingsStore.settings.value.dynamicColor shouldBe false
    }

    test("desligar o lembrete de dose grava no SettingsStore") {
        val fixture = SettingsHomeFixture()

        fixture.viewModel.onEvent(SettingsHomeUiEvent.DoseRemindersEnabledChanged(false))

        fixture.settingsStore.settings.value.doseRemindersEnabled shouldBe false
    }

    test("texto de lembrete em branco grava null, o que volta ao padrão neutro") {
        val fixture = SettingsHomeFixture()

        fixture.viewModel.onEvent(SettingsHomeUiEvent.ReminderTextChanged("  "))

        fixture.settingsStore.settings.value.reminderText shouldBe null
    }

    test("texto de lembrete preenchido grava o texto digitado, sem espaços nas pontas") {
        val fixture = SettingsHomeFixture()

        fixture.viewModel.onEvent(SettingsHomeUiEvent.ReminderTextChanged("  Hora do remédio  "))

        fixture.settingsStore.settings.value.reminderText shouldBe "Hora do remédio"
    }

    test("ligar o lembrete diário e mudar o horário grava os minutos desde a meia-noite") {
        val fixture = SettingsHomeFixture()

        fixture.viewModel.onEvent(SettingsHomeUiEvent.MoodReminderEnabledChanged(true))
        fixture.viewModel.onEvent(SettingsHomeUiEvent.MoodReminderTimeChanged(LocalTime(7, 30)))

        fixture.settingsStore.settings.value.moodReminderEnabled shouldBe true
        fixture.settingsStore.settings.value.moodReminderMinutes shouldBe (7 * 60 + 30)
    }

    test("o horário do lembrete no estado reflete os minutos gravados") {
        val fixture = SettingsHomeFixture()
        fixture.viewModel.onEvent(SettingsHomeUiEvent.MoodReminderTimeChanged(LocalTime(7, 30)))

        fixture.viewModel.state.test {
            awaitItem().moodReminderTime shouldBe LocalTime(7, 30)
        }
    }

    test("critério: bloqueio biométrico liga quando o aparelho autentica") {
        val fixture = SettingsHomeFixture(biometricAvailable = true)

        fixture.viewModel.onEvent(SettingsHomeUiEvent.BiometricLockChanged(true))

        fixture.settingsStore.settings.value.biometricLock shouldBe true
    }

    test("critério: bloqueio biométrico indisponível quando o aparelho não autentica") {
        val fixture = SettingsHomeFixture(biometricAvailable = false)

        fixture.viewModel.onEvent(SettingsHomeUiEvent.BiometricLockChanged(true))

        fixture.settingsStore.settings.value.biometricLock shouldBe false
        fixture.viewModel.state.test { awaitItem().biometricAvailable shouldBe false }
    }

    test("desligar o bloqueio biométrico funciona mesmo sem biometria disponível") {
        val fixture = SettingsHomeFixture(biometricAvailable = false)
        fixture.settingsStore.update { it.copy(biometricLock = true) }

        fixture.viewModel.onEvent(SettingsHomeUiEvent.BiometricLockChanged(false))

        fixture.settingsStore.settings.value.biometricLock shouldBe false
    }
})
