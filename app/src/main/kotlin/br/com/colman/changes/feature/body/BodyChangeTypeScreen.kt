// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.colman.changes.R
import br.com.colman.changes.core.model.BodyMeasurementUnit
import br.com.colman.changes.core.model.MediaAttachment
import br.com.colman.changes.ui.components.ChangesScreen
import br.com.colman.changes.ui.components.ConfirmDialog
import br.com.colman.changes.ui.components.DropdownField
import br.com.colman.changes.ui.components.EmptyState
import br.com.colman.changes.ui.components.LoadingState
import br.com.colman.changes.ui.components.SectionHeader
import br.com.colman.changes.ui.format.Formatters

@Composable
fun BodyChangeTypeScreen(
    state: BodyChangeTypeUiState,
    onEvent: (BodyChangeTypeUiEvent) -> Unit,
    onNavigate: (BodyChangeTypeNavigation) -> Unit,
    loadThumbnail: suspend (MediaAttachment, Boolean) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    BodyChangeTypeSnackbars(state, onEvent, snackbarHostState)

    ChangesScreen(
        title = state.typeLabel,
        onBack = { onNavigate(BodyChangeTypeNavigation.Back) },
        snackbarHostState = snackbarHostState,
        actions = { TypeActions(state, onEvent) },
        floatingAction = {
            FloatingActionButton(onClick = { onNavigate(BodyChangeTypeNavigation.AddEntry) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.body_type_add_entry))
            }
        },
    ) { padding ->
        BodyChangeTypeContent(state, onEvent, onNavigate, loadThumbnail, modifier.padding(padding))
    }

    if (state.deleteTypeRequested) {
        ConfirmDialog(
            title = stringResource(R.string.body_type_delete_confirm_title),
            text = stringResource(R.string.body_type_delete_confirm_text),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { onEvent(BodyChangeTypeUiEvent.ConfirmDeleteType) },
            onDismiss = { onEvent(BodyChangeTypeUiEvent.DismissDeleteType) },
        )
    }
}

@Composable
private fun BodyChangeTypeSnackbars(
    state: BodyChangeTypeUiState,
    onEvent: (BodyChangeTypeUiEvent) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val errorText = state.errorMessage?.let { errorMessageText(it) }
    LaunchedEffect(errorText) {
        val message = errorText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onEvent(BodyChangeTypeUiEvent.ErrorMessageShown)
    }

    val undoLabel = stringResource(R.string.action_undo)
    val deletedMessage = stringResource(R.string.body_type_entry_deleted_message)
    LaunchedEffect(state.pendingDeletionEntryId) {
        if (state.pendingDeletionEntryId == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(deletedMessage, actionLabel = undoLabel)
        if (result == SnackbarResult.ActionPerformed) onEvent(BodyChangeTypeUiEvent.UndoDeleteEntry)
    }
}

@Composable
private fun BodyChangeTypeContent(
    state: BodyChangeTypeUiState,
    onEvent: (BodyChangeTypeUiEvent) -> Unit,
    onNavigate: (BodyChangeTypeNavigation) -> Unit,
    loadThumbnail: suspend (MediaAttachment, Boolean) -> ImageBitmap?,
    modifier: Modifier,
) {
    when {
        state.isLoading -> LoadingState(modifier)
        state.entries.isEmpty() -> EmptyState(
            title = stringResource(R.string.body_type_empty_title),
            body = stringResource(R.string.body_type_empty_body),
            modifier = modifier,
        )
        else -> LazyColumn(modifier) {
            item { ComparisonSection(state.comparison, onEvent, loadThumbnail) }
            if (state.isVoiceCategory) {
                item { VoiceCompareSection(state.entries, state.playingEntryId, onEvent) }
            }
            item { SectionHeader(stringResource(R.string.body_type_timeline_title)) }
            items(state.entries, key = { it.entryId }) { entry ->
                EntryRow(entry, state.measurementUnit, state.playingEntryId, loadThumbnail, onEvent) {
                    onNavigate(BodyChangeTypeNavigation.EditEntry(entry.entryId))
                }
            }
        }
    }
}

@Composable
private fun TypeActions(state: BodyChangeTypeUiState, onEvent: (BodyChangeTypeUiEvent) -> Unit) {
    IconButton(onClick = { onEvent(BodyChangeTypeUiEvent.ToggleHidden) }) {
        if (state.isHidden) {
            Icon(Icons.Filled.Visibility, contentDescription = stringResource(R.string.body_type_show_action))
        } else {
            Icon(Icons.Filled.VisibilityOff, contentDescription = stringResource(R.string.body_type_hide_action))
        }
    }
    if (!state.isBuiltin) {
        IconButton(onClick = { onEvent(BodyChangeTypeUiEvent.RequestDeleteType) }) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.body_type_delete_action))
        }
    }
}

@Composable
private fun EntryRow(
    entry: BodyEntrySummary,
    unit: BodyMeasurementUnit?,
    playingEntryId: String?,
    loadThumbnail: suspend (MediaAttachment, Boolean) -> ImageBitmap?,
    onEvent: (BodyChangeTypeUiEvent) -> Unit,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val photo = entry.photo
        if (photo != null) {
            BodyThumbnailImage(
                key = photo.id to photo.checksumSha256,
                contentDescription = stringResource(
                    R.string.body_type_entry_photo_description,
                    Formatters.recorded(entry.observedAt),
                ),
                modifier = Modifier.size(56.dp).aspectRatio(1f),
            ) { fullSize -> loadThumbnail(photo, fullSize) }
        }
        EntryRowText(entry, unit, Modifier.weight(1f))
        if (entry.voice != null) {
            EntryVoiceButton(entry, playingEntryId == entry.entryId, onEvent)
        }
        IconButton(onClick = { onEvent(BodyChangeTypeUiEvent.DeleteEntry(entry.entryId)) }) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.action_delete))
        }
    }
}

@Composable
private fun EntryRowText(entry: BodyEntrySummary, unit: BodyMeasurementUnit?, modifier: Modifier) {
    Column(modifier) {
        Text(Formatters.recorded(entry.observedAt), style = MaterialTheme.typography.bodyLarge)
        if (entry.intensity != null) {
            Text(intensityLabel(entry.intensity), style = MaterialTheme.typography.bodyMedium)
        }
        if (entry.measurementValue != null && unit != null) {
            Text(
                "${Formatters.number(entry.measurementValue)} ${measurementUnitSymbol(unit)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun ComparisonSection(
    comparison: ComparisonState,
    onEvent: (BodyChangeTypeUiEvent) -> Unit,
    loadThumbnail: suspend (MediaAttachment, Boolean) -> ImageBitmap?,
) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.body_type_comparison_title))
        if (comparison.candidates.size < 2) {
            Text(stringResource(R.string.body_type_comparison_empty), style = MaterialTheme.typography.bodyMedium)
        } else {
            ComparisonPickers(comparison, onEvent, loadThumbnail)
        }
    }
}

@Composable
private fun ComparisonPickers(
    comparison: ComparisonState,
    onEvent: (BodyChangeTypeUiEvent) -> Unit,
    loadThumbnail: suspend (MediaAttachment, Boolean) -> ImageBitmap?,
) {
    val left = comparison.candidates.find { it.entryId == comparison.leftEntryId }
    val right = comparison.candidates.find { it.entryId == comparison.rightEntryId }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DropdownField(
            options = comparison.candidates,
            selected = left,
            onSelect = { onEvent(BodyChangeTypeUiEvent.SelectComparisonLeft(it.entryId)) },
            label = stringResource(R.string.body_type_comparison_pick_first),
            optionLabel = { Formatters.recorded(it.observedAt) },
            modifier = Modifier.weight(1f),
        )
        DropdownField(
            options = comparison.candidates,
            selected = right,
            onSelect = { onEvent(BodyChangeTypeUiEvent.SelectComparisonRight(it.entryId)) },
            label = stringResource(R.string.body_type_comparison_pick_second),
            optionLabel = { Formatters.recorded(it.observedAt) },
            modifier = Modifier.weight(1f),
        )
    }
    if (left?.photo != null && right?.photo != null) {
        ComparisonSlider(left, right, comparison.sliderPosition, loadThumbnail) {
            onEvent(BodyChangeTypeUiEvent.SetComparisonSlider(it))
        }
    }
}

/**
 * Slider de comparação com um único olho para as duas fotos (ADR 0013): a revelação vale para o par
 * escolhido, e reseta se a pessoa trocar uma das duas entradas comparadas.
 */
@Composable
private fun ComparisonSlider(
    left: BodyEntrySummary,
    right: BodyEntrySummary,
    position: Float,
    loadThumbnail: suspend (MediaAttachment, Boolean) -> ImageBitmap?,
    onPositionChange: (Float) -> Unit,
) {
    var revealed by rememberSaveable(left.entryId, right.entryId) { mutableStateOf(false) }
    val description = stringResource(
        R.string.body_type_comparison_slider_description,
        Formatters.recorded(left.observedAt),
        Formatters.recorded(right.observedAt),
    )
    Column {
        Box {
            ComparisonPhotos(left, right, position, revealed, description, loadThumbnail)
            RevealButton(
                revealed = revealed,
                showLabel = stringResource(R.string.body_photos_show),
                hideLabel = stringResource(R.string.body_photo_hide),
                onToggle = { revealed = !revealed },
                modifier = Modifier.align(Alignment.TopEnd),
            )
        }
        Slider(
            value = position,
            onValueChange = onPositionChange,
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}

@Composable
private fun ComparisonPhotos(
    left: BodyEntrySummary,
    right: BodyEntrySummary,
    position: Float,
    revealed: Boolean,
    description: String,
    loadThumbnail: suspend (MediaAttachment, Boolean) -> ImageBitmap?,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .semantics { contentDescription = description },
    ) {
        val leftPhoto = requireNotNull(left.photo)
        val rightPhoto = requireNotNull(right.photo)
        CensoredImage(
            key = leftPhoto.id to leftPhoto.checksumSha256,
            revealed = revealed,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
        ) { loadThumbnail(leftPhoto, false) }
        CensoredImage(
            key = rightPhoto.id to rightPhoto.checksumSha256,
            revealed = revealed,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().drawWithContent {
                clipRect(left = 0f, top = 0f, right = size.width * position, bottom = size.height) {
                    this@drawWithContent.drawContent()
                }
            },
        ) { loadThumbnail(rightPhoto, false) }
    }
}
