// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.di

import br.com.colman.changes.core.di.IoDispatcher
import br.com.colman.changes.platform.reminders.ReminderNotifier
import br.com.colman.changes.platform.reminders.ReminderSync
import kotlinx.coroutines.CoroutineDispatcher
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Ponte para BroadcastReceivers, que o sistema instancia sem injeção por construtor. É o único uso de
 * `KoinComponent` fora da Application, e mora em `app.di` como exige a Seção 5.
 */
object ReceiverDependencies : KoinComponent {
    val reminderSync: ReminderSync by inject()
    val notifier: ReminderNotifier by inject()
    val io: CoroutineDispatcher by inject(IoDispatcher)
}
