// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.colman.changes.R
import br.com.colman.changes.ui.components.ChangesScreen
import br.com.colman.changes.ui.components.ConfirmDialog
import br.com.colman.changes.ui.components.EmptyState
import br.com.colman.changes.ui.components.LoadingState
import br.com.colman.changes.ui.components.SectionHeader
import br.com.colman.changes.ui.components.TypedConfirmDialog
import br.com.colman.changes.ui.format.Formatters

/**
 * Lixeira de 30 dias (Seção 9), sem estado próprio de negócio: toda mutação sai como [TrashUiEvent].
 * Explica no topo o que a tela faz; a lista é agrupada por tipo, com restaurar e excluir definitivo
 * por item, e esvaziar a lixeira inteira ao final.
 */
@Composable
fun TrashScreen(
    state: TrashUiState,
    onEvent: (TrashUiEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Box(modifier) {
        ChangesScreen(
            title = stringResource(R.string.trash_title),
            onBack = onBack,
            snackbarHostState = snackbarHostState,
        ) { padding ->
            when {
                state.isLoading -> LoadingState(Modifier.padding(padding))
                state.groups.isEmpty() -> EmptyState(
                    title = stringResource(R.string.trash_title),
                    body = stringResource(R.string.trash_empty_body),
                    modifier = Modifier.padding(padding),
                )
                else -> TrashContent(state, onEvent, Modifier.padding(padding))
            }
        }
    }

    TrashDialogs(state, onEvent)
}

@Composable
private fun TrashDialogs(state: TrashUiState, onEvent: (TrashUiEvent) -> Unit) {
    val pending = state.pendingPurge
    if (pending != null) {
        ConfirmDialog(
            title = stringResource(R.string.trash_confirm_delete_title),
            text = stringResource(R.string.trash_confirm_delete_text),
            confirmLabel = stringResource(R.string.trash_action_delete_forever),
            onConfirm = { onEvent(TrashUiEvent.ConfirmPurge) },
            onDismiss = { onEvent(TrashUiEvent.DismissPurge) },
        )
    }

    if (state.emptyRequested) {
        TypedConfirmDialog(
            title = stringResource(R.string.trash_confirm_empty_title),
            text = stringResource(R.string.trash_confirm_empty_text),
            requiredText = stringResource(R.string.trash_confirm_empty_word),
            confirmLabel = stringResource(R.string.trash_action_empty),
            onConfirm = { onEvent(TrashUiEvent.ConfirmEmpty) },
            onDismiss = { onEvent(TrashUiEvent.DismissEmpty) },
        )
    }
}

@Composable
private fun TrashContent(state: TrashUiState, onEvent: (TrashUiEvent) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item { Text(stringResource(R.string.trash_explanation), modifier = Modifier.padding(16.dp)) }
        state.groups.forEach { group ->
            val headerKey = "header-${group.category}"
            item(key = headerKey) { SectionHeader(stringResource(trashCategoryLabelRes(group.category))) }
            items(group.entries, key = { it.item.id.toString() }) { entry -> TrashRow(entry, onEvent) }
        }
        item { EmptyTrashRow(onEvent) }
    }
}

@Composable
private fun EmptyTrashRow(onEvent: (TrashUiEvent) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
        OutlinedButton(onClick = { onEvent(TrashUiEvent.RequestEmpty) }) {
            Text(stringResource(R.string.trash_action_empty))
        }
    }
}

@Composable
private fun TrashRow(entry: TrashEntryUiState, onEvent: (TrashUiEvent) -> Unit) {
    val deletedText = stringResource(R.string.trash_deleted_at, Formatters.date(entry.deletedDate))
    val purgeText = stringResource(R.string.trash_purge_at, Formatters.date(entry.purgeDate))
    val label = entry.item.label
    ListItem(
        headlineContent = { Text(label.ifBlank { deletedText }) },
        supportingContent = { Text(if (label.isBlank()) purgeText else "$deletedText $purgeText") },
        trailingContent = { TrashRowActions(entry, onEvent) },
    )
}

@Composable
private fun TrashRowActions(entry: TrashEntryUiState, onEvent: (TrashUiEvent) -> Unit) {
    Row {
        IconButton(onClick = { onEvent(TrashUiEvent.Restore(entry.item)) }) {
            Icon(Icons.Filled.Restore, contentDescription = stringResource(R.string.trash_action_restore))
        }
        IconButton(onClick = { onEvent(TrashUiEvent.RequestPurge(entry.item)) }) {
            Icon(Icons.Filled.DeleteForever, contentDescription = stringResource(R.string.trash_action_delete_forever))
        }
    }
}

/** Nome amigável por categoria (nunca o nome do enum). `when` sem `else`: uma categoria nova quebra o build. */
private fun trashCategoryLabelRes(category: TrashCategory): Int = when (category) {
    TrashCategory.MEDICATION -> R.string.trash_kind_medication
    TrashCategory.REGIMEN -> R.string.trash_kind_regimen
    TrashCategory.DOSE_LOG -> R.string.trash_kind_dose_log
    TrashCategory.BODY_CHANGE -> R.string.trash_kind_body_change
    TrashCategory.MEASUREMENT -> R.string.trash_kind_measurement
    TrashCategory.EXERCISE -> R.string.trash_kind_exercise
    TrashCategory.HEALTH_CONDITION -> R.string.trash_kind_health_condition
    TrashCategory.LAB -> R.string.trash_kind_lab
    TrashCategory.MOOD -> R.string.trash_kind_mood
    TrashCategory.CALENDAR_EVENT -> R.string.trash_kind_calendar_event
}
