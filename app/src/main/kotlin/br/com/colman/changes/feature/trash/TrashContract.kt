// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.trash

import androidx.compose.runtime.Immutable
import br.com.colman.changes.core.data.TrashItem
import kotlinx.datetime.LocalDate

/** Um item da lixeira já com as datas convertidas para o fuso atual (Seção 9, ADR 0008). */
@Immutable
data class TrashEntryUiState(val item: TrashItem, val deletedDate: LocalDate, val purgeDate: LocalDate)

/** Itens de uma mesma [TrashCategory], na ordem em que chegam do repositório (mais recente primeiro). */
@Immutable
data class TrashGroupUiState(val category: TrashCategory, val entries: List<TrashEntryUiState>)

/**
 * Estado da lixeira (Seção 9): lista agrupada por tipo, mais o item pendente de exclusão definitiva
 * e se a confirmação de esvaziar está aberta.
 */
@Immutable
data class TrashUiState(
    val isLoading: Boolean = true,
    val groups: List<TrashGroupUiState> = emptyList(),
    val pendingPurge: TrashItem? = null,
    val emptyRequested: Boolean = false,
)

sealed interface TrashUiEvent {
    data class Restore(val item: TrashItem) : TrashUiEvent

    data class RequestPurge(val item: TrashItem) : TrashUiEvent

    data object DismissPurge : TrashUiEvent

    data object ConfirmPurge : TrashUiEvent

    data object RequestEmpty : TrashUiEvent

    data object DismissEmpty : TrashUiEvent

    data object ConfirmEmpty : TrashUiEvent
}

/** Efeito de uma vez: confirmação de que o item voltou (Seção 9). */
sealed interface TrashEffect {
    data object ShowRestoredSnackbar : TrashEffect
}
