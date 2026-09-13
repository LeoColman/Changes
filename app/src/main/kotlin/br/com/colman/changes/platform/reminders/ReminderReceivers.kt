// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.platform.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.colman.changes.di.ReceiverDependencies
import br.com.colman.changes.platform.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Dispara a notificação e reagenda os próximos lembretes. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val dependencies = ReceiverDependencies
        dependencies.notifier.notify(intent.getIntExtra(EXTRA_CODE, 0))
        resync(goAsync(), dependencies)
    }

    companion object {
        const val EXTRA_KIND: String = "kind"
        const val EXTRA_CODE: String = "code"
    }
}

/**
 * Reagenda depois de reinício do aparelho (critério 7.9.3), mudança de hora ou de fuso e atualização
 * do app. Não é exportado: esses broadcasts vêm do sistema.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in HANDLED) resync(goAsync(), ReceiverDependencies)
    }

    private companion object {
        val HANDLED = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
        )
    }
}

private fun resync(pending: BroadcastReceiver.PendingResult, dependencies: ReceiverDependencies) {
    CoroutineScope(SupervisorJob() + dependencies.io).launch {
        try {
            dependencies.reminderSync.sync()
        } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
            // Falha de reagendamento não pode derrubar o receiver; a próxima abertura do app tenta de novo.
            AppLogger.warn("Reminders", error) { "resync failed" }
        } finally {
            pending.finish()
        }
    }
}
