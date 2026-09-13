// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.platform.reminders

import br.com.colman.changes.core.data.CalendarRepository
import br.com.colman.changes.core.data.RegimenRepository
import br.com.colman.changes.platform.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration.Companion.milliseconds

/**
 * Reagenda os alarmes sempre que regimes ativos, eventos com lembrete ou ajustes mudam, venha a
 * mudança de uma tela ou de um import de backup (Seção 7.9). Nenhuma tela chama [ReminderSync]:
 * basta gravar. Mudanças em sequência viram uma só sincronização.
 */
class ReminderResyncer(
    private val regimens: RegimenRepository,
    private val calendar: CalendarRepository,
    private val settings: SettingsStore,
    private val sync: ReminderSync,
) {
    /** A primeira sincronização acontece logo depois da primeira leitura das três fontes. */
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope): Job =
        combine(regimens.observeActive(), calendar.observeWithReminders(), settings.settings) { _, _, _ -> }
            .debounce(QUIET_PERIOD)
            .onEach { sync.sync() }
            .launchIn(scope)

    private companion object {
        val QUIET_PERIOD = 500.milliseconds
    }
}
