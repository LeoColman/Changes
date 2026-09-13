// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.changes.core.data.TrashItem
import br.com.colman.changes.core.data.TrashRepository
import br.com.colman.changes.core.model.TimeZoneProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime

/**
 * Lixeira de 30 dias (Seção 9, ADR 0008). A regra de exclusão, restauração e purga é do
 * [TrashRepository]; este ViewModel só monta o estado da tela e guarda qual confirmação está aberta.
 */
class TrashViewModel(
    private val trashRepository: TrashRepository,
    private val timeZones: TimeZoneProvider,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())

    private val effectsChannel = Channel<TrashEffect>(Channel.BUFFERED)
    val effects: Flow<TrashEffect> = effectsChannel.receiveAsFlow()

    val state: StateFlow<TrashUiState> = combine(trashRepository.observe(), local) { items, localState ->
        buildState(items, localState)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), TrashUiState())

    fun onEvent(event: TrashUiEvent) {
        when (event) {
            is TrashUiEvent.Restore -> restore(event.item)
            is TrashUiEvent.RequestPurge -> local.update { it.copy(pendingPurge = event.item) }
            TrashUiEvent.DismissPurge -> local.update { it.copy(pendingPurge = null) }
            TrashUiEvent.ConfirmPurge -> confirmPurge()
            TrashUiEvent.RequestEmpty -> local.update { it.copy(emptyRequested = true) }
            TrashUiEvent.DismissEmpty -> local.update { it.copy(emptyRequested = false) }
            TrashUiEvent.ConfirmEmpty -> confirmEmpty()
        }
    }

    private fun restore(item: TrashItem) = viewModelScope.launch {
        trashRepository.restore(item)
        effectsChannel.send(TrashEffect.ShowRestoredSnackbar)
    }

    private fun confirmPurge() {
        val item = local.value.pendingPurge ?: return
        viewModelScope.launch {
            trashRepository.purge(item)
            local.update { it.copy(pendingPurge = null) }
        }
    }

    private fun confirmEmpty() = viewModelScope.launch {
        trashRepository.empty()
        local.update { it.copy(emptyRequested = false) }
    }

    private fun buildState(items: List<TrashItem>, localState: LocalState): TrashUiState {
        val zone = timeZones.current()
        val entries = items.map { item ->
            TrashEntryUiState(item, item.deletedAt.toLocalDateTime(zone).date, item.purgeAt.toLocalDateTime(zone).date)
        }
        val groups = entries.groupBy { trashCategoryOf(it.item.kind) }
            .map { (category, categoryEntries) -> TrashGroupUiState(category, categoryEntries) }
        return TrashUiState(
            isLoading = false,
            groups = groups,
            pendingPurge = localState.pendingPurge,
            emptyRequested = localState.emptyRequested,
        )
    }

    private data class LocalState(val pendingPurge: TrashItem? = null, val emptyRequested: Boolean = false)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
