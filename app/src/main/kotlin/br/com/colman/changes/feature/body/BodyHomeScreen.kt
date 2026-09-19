// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.colman.changes.R
import br.com.colman.changes.core.model.BodyChangeCategory
import br.com.colman.changes.core.model.BodyMeasurementUnit
import br.com.colman.changes.core.model.MediaAttachment
import br.com.colman.changes.ui.components.ChangesScreen
import br.com.colman.changes.ui.components.DropdownField
import br.com.colman.changes.ui.components.EmptyState
import br.com.colman.changes.ui.components.LoadingState
import br.com.colman.changes.ui.components.SectionHeader
import br.com.colman.changes.ui.format.Formatters

@Composable
fun BodyHomeScreen(
    state: BodyHomeUiState,
    onEvent: (BodyHomeUiEvent) -> Unit,
    onNavigate: (BodyHomeNavigation) -> Unit,
    loadThumbnail: suspend (MediaAttachment, Boolean) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorText = state.errorMessage?.let { errorMessageText(it) }
    LaunchedEffect(errorText) {
        val message = errorText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onEvent(BodyHomeUiEvent.ErrorMessageShown)
    }

    ChangesScreen(
        title = stringResource(R.string.tab_body),
        snackbarHostState = snackbarHostState,
        actions = {
            IconButton(onClick = { onEvent(BodyHomeUiEvent.OpenNewTypeDialog) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.body_new_type_title))
            }
        },
    ) { padding ->
        BodyHomeContent(state, onNavigate, loadThumbnail, modifier.padding(padding))
    }

    state.newTypeDialog?.let { dialog -> NewTypeDialog(dialog, onEvent) }
}

@Composable
private fun BodyHomeContent(
    state: BodyHomeUiState,
    onNavigate: (BodyHomeNavigation) -> Unit,
    loadThumbnail: suspend (MediaAttachment, Boolean) -> ImageBitmap?,
    modifier: Modifier,
) {
    when {
        state.isLoading -> LoadingState(modifier)
        state.sections.isEmpty() -> EmptyState(
            title = stringResource(R.string.body_home_empty_title),
            body = stringResource(R.string.body_home_empty_body),
            modifier = modifier,
        )
        else -> LazyColumn(modifier) {
            item { ShortcutsRow(onNavigate) }
            state.sections.forEach { section ->
                item(key = "header-${section.category}") { SectionHeader(categoryLabel(section.category)) }
                items(section.types, key = { it.typeId }) { type ->
                    BodyTypeRow(type, loadThumbnail) { onNavigate(BodyHomeNavigation.ChangeType(type.typeId)) }
                }
            }
        }
    }
}

@Composable
private fun ShortcutsRow(onNavigate: (BodyHomeNavigation) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(onClick = { onNavigate(BodyHomeNavigation.ExpectedChanges) }) {
            Text(stringResource(R.string.body_home_shortcut_expected_changes))
        }
        OutlinedButton(onClick = { onNavigate(BodyHomeNavigation.Measurements) }) {
            Text(stringResource(R.string.body_home_shortcut_measurements))
        }
    }
}

@Composable
private fun BodyTypeRow(
    type: BodyTypeSummary,
    loadThumbnail: suspend (MediaAttachment, Boolean) -> ImageBitmap?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val photo = type.lastPhoto
        Box(
            Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
        ) {
            if (photo != null) {
                BodyThumbnailImage(
                    key = photo.id to photo.checksumSha256,
                    contentDescription = stringResource(R.string.body_home_thumbnail_description, type.label),
                    modifier = Modifier.aspectRatio(1f),
                ) { fullSize -> loadThumbnail(photo, fullSize) }
            }
        }
        BodyTypeRowText(type)
    }
}

@Composable
private fun BodyTypeRowText(type: BodyTypeSummary) {
    Column {
        Text(type.label, style = MaterialTheme.typography.bodyLarge)
        val subtitle = type.lastObservedAt?.let { observedAt ->
            if (type.lastIntensity != null) {
                stringResource(
                    R.string.body_home_last_entry_with_intensity,
                    Formatters.recorded(observedAt),
                    intensityLabel(type.lastIntensity),
                )
            } else {
                Formatters.recorded(observedAt)
            }
        } ?: stringResource(R.string.body_home_no_entries)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun NewTypeDialog(dialog: NewTypeDialogState, onEvent: (BodyHomeUiEvent) -> Unit) {
    var hasMeasurement by remember(dialog) { mutableStateOf(dialog.measurementUnit != null) }
    AlertDialog(
        onDismissRequest = { onEvent(BodyHomeUiEvent.DismissNewTypeDialog) },
        title = { Text(stringResource(R.string.body_new_type_title)) },
        text = {
            NewTypeDialogFields(
                dialog = dialog,
                hasMeasurement = hasMeasurement,
                onHasMeasurementChange = { checked ->
                    hasMeasurement = checked
                    if (!checked) onEvent(BodyHomeUiEvent.NewTypeMeasurementUnitChanged(null))
                },
                onEvent = onEvent,
            )
        },
        confirmButton = {
            TextButton(onClick = { onEvent(BodyHomeUiEvent.ConfirmNewType) }) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(BodyHomeUiEvent.DismissNewTypeDialog) }) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun NewTypeDialogFields(
    dialog: NewTypeDialogState,
    hasMeasurement: Boolean,
    onHasMeasurementChange: (Boolean) -> Unit,
    onEvent: (BodyHomeUiEvent) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = dialog.name,
            onValueChange = { onEvent(BodyHomeUiEvent.NewTypeNameChanged(it)) },
            label = { Text(stringResource(R.string.body_new_type_name_label)) },
            isError = dialog.nameError,
            supportingText = if (dialog.nameError) {
                { Text(stringResource(R.string.body_new_type_name_required_error)) }
            } else {
                null
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownField(
            options = BodyChangeCategory.entries,
            selected = dialog.category,
            onSelect = { onEvent(BodyHomeUiEvent.NewTypeCategoryChanged(it)) },
            label = stringResource(R.string.body_new_type_category_label),
            optionLabel = { categoryLabel(it) },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = hasMeasurement, onCheckedChange = onHasMeasurementChange)
            Text(stringResource(R.string.body_new_type_measurement_switch))
        }
        if (hasMeasurement) {
            DropdownField(
                options = BodyMeasurementUnit.entries,
                selected = dialog.measurementUnit,
                onSelect = { onEvent(BodyHomeUiEvent.NewTypeMeasurementUnitChanged(it)) },
                label = stringResource(R.string.body_new_type_measurement_unit_label),
                optionLabel = { measurementUnitLabel(it) },
            )
        }
    }
}
