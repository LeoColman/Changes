// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.colman.changes.nav.ChangesApp
import br.com.colman.changes.nav.OnboardingRoute
import br.com.colman.changes.nav.TodayRoute
import br.com.colman.changes.platform.AppLock
import br.com.colman.changes.platform.BiometricUnlocker
import br.com.colman.changes.platform.SettingsStore
import br.com.colman.changes.ui.components.LockScreen
import br.com.colman.changes.ui.theme.ChangesTheme
import org.koin.android.ext.android.inject
import org.koin.compose.koinInject

/**
 * FragmentActivity porque o BiometricPrompt exige. FLAG_SECURE vale para a janela inteira
 * (Seção 3.1). Com o bloqueio ativo, nenhuma tela do app é composta.
 */
class MainActivity : FragmentActivity() {
    private val lock: AppLock by inject()
    private val unlocker: BiometricUnlocker by inject()

    /** Evita pedir de novo em loop se a pessoa cancelar: um pedido automático por ciclo de bloqueio. */
    private var autoPrompted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent {
            val settings by koinInject<SettingsStore>().settings.collectAsStateWithLifecycle()
            val locked by lock.locked.collectAsStateWithLifecycle()
            // Decidido uma vez: terminar o onboarding navega para Hoje, sem recriar o grafo.
            val startDestination = remember { if (settings.onboardingDone) TodayRoute else OnboardingRoute }
            ChangesTheme(themeMode = settings.themeMode, dynamicColor = settings.dynamicColor) {
                if (locked) LockScreen(onUnlock = ::requestUnlock) else ChangesApp(startDestination)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (lock.locked.value && !autoPrompted) {
            autoPrompted = true
            requestUnlock()
        }
    }

    override fun onStop() {
        super.onStop()
        autoPrompted = false
    }

    private fun requestUnlock() {
        unlocker.unlock(this, getString(R.string.lock_prompt_title))
    }
}
