// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.calendar

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import br.com.colman.changes.R
import br.com.colman.changes.core.model.CalendarCategory
import br.com.colman.changes.ui.components.ChangesScreen
import br.com.colman.changes.ui.components.ConfirmDialog
import br.com.colman.changes.ui.components.DropdownField
import br.com.colman.changes.ui.components.FormColumn
import br.com.colman.changes.ui.components.LoadingState
import br.com.colman.changes.ui.components.SectionHeader
import br.com.colman.changes.ui.components.WithUnsavedChangesGuard

/** Ação de campo simples de formulário: aplica uma transformação ao estado atual. */
internal typealias Change = ((EventEditUiState) -> EventEditUiState) -> Unit

/** Criar/editar evento do calendário (Seção 7.9). Stateless: toda mutação sai como [EventEditUiEvent]. */
@Composable
fun EventEditScreen(
    state: EventEditUiState,
    onEvent: (EventEditUiEvent) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val title = stringResource(
        if (state.isNew) R.string.calendar_event_title_new else R.string.calendar_event_title_edit,
    )
    WithUnsavedChangesGuard(
        hasUnsavedChanges = state.hasUnsavedChanges,
        onLeave = { onEvent(EventEditUiEvent.Back) },
    ) { requestLeave ->
        Box(modifier) {
            ChangesScreen(
                title = title,
                onBack = requestLeave,
                snackbarHostState = snackbarHostState,
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { onEvent(EventEditUiEvent.RequestDelete) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                },
            ) { padding ->
                if (state.isLoading) {
                    LoadingState(Modifier.padding(padding))
                } else {
                    EventEditForm(state, onEvent, Modifier.padding(padding).verticalScroll(rememberScrollState()))
                }
            }
        }
        if (state.showDeleteConfirm) {
            ConfirmDialog(
                title = stringResource(R.string.calendar_delete_confirm_title),
                text = stringResource(R.string.calendar_delete_confirm_body),
                confirmLabel = stringResource(R.string.action_delete),
                onConfirm = { onEvent(EventEditUiEvent.ConfirmDelete) },
                onDismiss = { onEvent(EventEditUiEvent.CancelDelete) },
            )
        }
    }
}

@Composable
private fun EventEditForm(state: EventEditUiState, onEvent: (EventEditUiEvent) -> Unit, modifier: Modifier = Modifier) {
    val change: Change = { apply -> onEvent(EventEditUiEvent.FieldChanged(apply)) }

    FormColumn(modifier) {
        TitleDescriptionSection(state, change)
        HorizontalDivider()

        SectionHeader(stringResource(R.string.calendar_section_when))
        CategoryAndAllDaySection(state, change)
        DateTimeSection(state, change)
        ReminderSection(state, change)
        HorizontalDivider()

        SectionHeader(stringResource(R.string.calendar_section_recurrence))
        RecurrenceSection(state, change)
        HorizontalDivider()

        CompletionAndSave(state, onEvent)
    }
}

@Composable
private fun TitleDescriptionSection(state: EventEditUiState, change: Change) {
    OutlinedTextField(
        value = state.title,
        onValueChange = { value -> change { it.copy(title = value) } },
        label = { Text(stringResource(R.string.calendar_field_title)) },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.description,
        onValueChange = { value -> change { it.copy(description = value) } },
        label = { Text(stringResource(R.string.calendar_field_description)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CategoryAndAllDaySection(state: EventEditUiState, change: Change) {
    DropdownField(
        options = CalendarCategory.entries,
        selected = state.category,
        onSelect = { category -> change { it.copy(category = category) } },
        label = stringResource(R.string.calendar_field_category),
        optionLabel = { categoryLabel(it) },
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.calendar_field_all_day), modifier = Modifier.weight(1f))
        Switch(checked = state.isAllDay, onCheckedChange = { allDay -> change { it.copy(isAllDay = allDay) } })
    }
}

@Composable
private fun CompletionAndSave(state: EventEditUiState, onEvent: (EventEditUiEvent) -> Unit) {
    if (!state.isNew) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.calendar_field_completed), modifier = Modifier.weight(1f))
            Switch(checked = state.isCompleted, onCheckedChange = { onEvent(EventEditUiEvent.ToggleCompleted) })
        }
    }
    if (state.error != null) {
        Text(state.error.calendarMessage(), color = MaterialTheme.colorScheme.error)
    }
    Button(onClick = { onEvent(EventEditUiEvent.Save) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_save))
    }
}
