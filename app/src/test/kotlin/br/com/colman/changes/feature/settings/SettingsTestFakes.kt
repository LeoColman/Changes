// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.settings

import br.com.colman.changes.platform.AppSettings
import br.com.colman.changes.platform.BiometricAvailability
import br.com.colman.changes.platform.SettingsStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Fake de [SettingsStore] em memória, para testar ViewModels sem SharedPreferences (Android). */
class FakeSettingsStore(initial: AppSettings = AppSettings()) : SettingsStore {
    private val state = MutableStateFlow(initial)
    override val settings: StateFlow<AppSettings> = state.asStateFlow()

    override fun update(transform: (AppSettings) -> AppSettings) {
        state.value = transform(state.value)
    }
}

/** Fake de [BiometricAvailability] com resposta fixa, controlável pelo teste. */
class FakeBiometricAvailability(var available: Boolean = true) : BiometricAvailability {
    override fun canAuthenticate(): Boolean = available
}
