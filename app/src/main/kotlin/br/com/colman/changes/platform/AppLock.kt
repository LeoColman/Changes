// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.platform

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Bloqueio opcional na abertura e ao voltar do segundo plano (Seção 3.1). É uma barreira contra
 * acesso casual; o dado não é cifrado. Registrado no `ProcessLifecycleOwner` pela Application.
 */
class AppLock(private val settings: SettingsStore) : DefaultLifecycleObserver {
    private val state = MutableStateFlow(settings.settings.value.biometricLock)

    val locked: StateFlow<Boolean> = state.asStateFlow()

    override fun onStop(owner: LifecycleOwner) {
        if (settings.settings.value.biometricLock) state.value = true
    }

    fun unlock() {
        state.value = false
    }
}

/** O aparelho consegue autenticar (biometria ou credencial do aparelho)? Interface para as telas testarem em JVM. */
interface BiometricAvailability {
    fun canAuthenticate(): Boolean
}

class AndroidBiometricAvailability(private val context: Context) : BiometricAvailability {
    override fun canAuthenticate(): Boolean =
        BiometricManager.from(context).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
}

private const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

/**
 * Pede a autenticação. Se o aparelho deixou de ter como autenticar (a pessoa removeu o bloqueio de
 * tela), o bloqueio do app é desligado em vez de trancar a pessoa fora dos próprios dados.
 */
class BiometricUnlocker(
    private val availability: BiometricAvailability,
    private val settings: SettingsStore,
    private val lock: AppLock,
) {
    fun unlock(activity: FragmentActivity, title: String) {
        if (!availability.canAuthenticate()) {
            settings.update { it.copy(biometricLock = false) }
            lock.unlock()
            return
        }
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                lock.unlock()
            }
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), callback).authenticate(info)
    }
}
