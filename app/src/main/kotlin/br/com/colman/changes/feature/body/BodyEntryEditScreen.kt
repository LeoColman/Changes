// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.colman.changes.R
import br.com.colman.changes.core.model.Intensity
import br.com.colman.changes.ui.components.ChangesScreen
import br.com.colman.changes.ui.components.ConfirmDialog
import br.com.colman.changes.ui.components.DateField
import br.com.colman.changes.ui.components.DropdownField
import br.com.colman.changes.ui.components.FormColumn
import br.com.colman.changes.ui.components.LoadingState
import br.com.colman.changes.ui.components.NumberField
import br.com.colman.changes.ui.components.SectionHeader
import br.com.colman.changes.ui.components.TimeField
import br.com.colman.changes.ui.components.WithUnsavedChangesGuard
import br.com.colman.changes.ui.format.Formatters

@Composable
fun BodyEntryEditScreen(
    state: BodyEntryEditUiState,
    onEvent: (BodyEntryEditUiEvent) -> Unit,
    actions: BodyEntryEditActions,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorText = state.errorMessage?.let { errorMessageText(it) }
    LaunchedEffect(errorText) {
        val message = errorText ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onEvent(BodyEntryEditUiEvent.ErrorMessageShown)
    }

    WithUnsavedChangesGuard(hasUnsavedChanges = state.hasUnsavedChanges, onLeave = actions.onBack) { requestLeave ->
        ChangesScreen(
            title = stringResource(
                if (state.isNew) R.string.body_entry_edit_new_title else R.string.body_entry_edit_edit_title,
            ),
            onBack = requestLeave,
            snackbarHostState = snackbarHostState,
            actions = {
                TextButton(onClick = { onEvent(BodyEntryEditUiEvent.Save) }) {
                    Text(stringResource(R.string.action_save))
                }
            },
        ) { padding ->
            BodyEntryEditContent(state, onEvent, actions, modifier.padding(padding))
        }
    }

    if (state.photoPendingRemoval != null) {
        ConfirmDialog(
            title = stringResource(R.string.body_photo_remove_confirm_title),
            text = stringResource(R.string.body_photo_remove_confirm_body),
            confirmLabel = stringResource(R.string.body_photo_remove_confirm_action),
            onConfirm = { onEvent(BodyEntryEditUiEvent.ConfirmRemovePhoto) },
            onDismiss = { onEvent(BodyEntryEditUiEvent.CancelRemovePhoto) },
        )
    }
}

@Composable
private fun BodyEntryEditContent(
    state: BodyEntryEditUiState,
    onEvent: (BodyEntryEditUiEvent) -> Unit,
    actions: BodyEntryEditActions,
    modifier: Modifier,
) {
    when {
        state.isLoading -> LoadingState(modifier)
        state.typeMissing -> Text(stringResource(R.string.body_entry_edit_type_missing), modifier = modifier)
        else -> FormColumn(modifier.verticalScroll(rememberScrollState())) {
            Text(state.typeLabel)
            BodyEntryEditFields(state, onEvent)
            SectionHeader(stringResource(R.string.body_entry_edit_photos_label))
            PhotoPicker(state.photos, actions.loadPhoto, actions.onPickPhoto, onEvent)
            if (state.supportsVoiceRecording) {
                VoiceRecordingSection(state, onEvent, actions.onRecordRequested)
            }
        }
    }
}

@Composable
private fun BodyEntryEditFields(state: BodyEntryEditUiState, onEvent: (BodyEntryEditUiEvent) -> Unit) {
    DateField(
        date = state.date,
        onDateChange = { onEvent(BodyEntryEditUiEvent.DateChanged(it)) },
        label = stringResource(R.string.body_entry_edit_date_label),
    )
    TimeField(
        time = state.time,
        onTimeChange = { onEvent(BodyEntryEditUiEvent.TimeChanged(it)) },
        label = stringResource(R.string.body_entry_edit_time_label),
    )
    DropdownField(
        options = listOf<Intensity?>(null) + Intensity.entries,
        selected = state.intensity,
        onSelect = { onEvent(BodyEntryEditUiEvent.IntensityChanged(it)) },
        label = stringResource(R.string.body_intensity_label),
        optionLabel = { intensityLabel(it) },
    )
    val measurementUnit = state.measurementUnit
    if (state.supportsMeasurement && measurementUnit != null) {
        NumberField(
            value = state.measurementText,
            onValueChange = { onEvent(BodyEntryEditUiEvent.MeasurementChanged(it)) },
            label = stringResource(R.string.body_entry_edit_measurement_label, measurementUnitSymbol(measurementUnit)),
        )
    }
    OutlinedTextField(
        value = state.notes,
        onValueChange = { onEvent(BodyEntryEditUiEvent.NotesChanged(it)) },
        label = { Text(stringResource(R.string.body_entry_edit_notes_label)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun PhotoPicker(
    photos: List<EntryPhoto>,
    loadPhoto: suspend (EntryPhoto, Boolean) -> ImageBitmap?,
    onPickPhoto: (BodyPhotoPickSource) -> Unit,
    onEvent: (BodyEntryEditUiEvent) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onPickPhoto(BodyPhotoPickSource.Camera) }) {
            Icon(Icons.Filled.CameraAlt, contentDescription = null)
            Text(stringResource(R.string.body_entry_edit_add_camera_action))
        }
        OutlinedButton(onClick = { onPickPhoto(BodyPhotoPickSource.Gallery) }) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
            Text(stringResource(R.string.body_entry_edit_add_gallery_action))
        }
    }
    if (photos.isNotEmpty()) {
        PhotoStrip(photos, loadPhoto, onEvent)
    }
}

@Composable
private fun PhotoStrip(
    photos: List<EntryPhoto>,
    loadPhoto: suspend (EntryPhoto, Boolean) -> ImageBitmap?,
    onEvent: (BodyEntryEditUiEvent) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(photos, key = { it.key }) { photo ->
            Box(Modifier.size(96.dp), contentAlignment = Alignment.TopEnd) {
                BodyThumbnailImage(
                    key = photo.key,
                    contentDescription = photoDescription(photo),
                    modifier = Modifier.aspectRatio(1f),
                ) { fullSize -> loadPhoto(photo, fullSize) }
                IconButton(onClick = { onEvent(BodyEntryEditUiEvent.RequestRemovePhoto(photo.key)) }) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.body_entry_edit_remove_photo_action),
                    )
                }
            }
        }
    }
}

@Composable
private fun photoDescription(photo: EntryPhoto): String {
    val capturedAt = (photo as? EntryPhoto.Attached)?.media?.capturedAt
    return if (capturedAt != null) {
        stringResource(R.string.body_type_entry_photo_description, Formatters.recorded(capturedAt))
    } else {
        stringResource(R.string.body_entry_edit_photos_label)
    }
}

/** Seção "Gravação de voz" (ADR 0013): frase fixa, controles de gravar/ouvir/apagar, e o aviso de microfone. */
@Composable
private fun VoiceRecordingSection(
    state: BodyEntryEditUiState,
    onEvent: (BodyEntryEditUiEvent) -> Unit,
    onRecordRequested: () -> Unit,
) {
    SectionHeader(stringResource(R.string.body_voice_section))
    Text(stringResource(R.string.body_voice_instructions))
    Text(stringResource(R.string.body_voice_phrase), style = MaterialTheme.typography.bodyLarge)
    if (state.microphoneUnavailable) {
        Text(stringResource(R.string.body_voice_permission_denied), color = MaterialTheme.colorScheme.error)
    }
    VoiceRecordingControls(state.voiceState, onEvent, onRecordRequested)
}

@Composable
private fun VoiceRecordingControls(
    voiceState: VoiceRecordingUiState,
    onEvent: (BodyEntryEditUiEvent) -> Unit,
    onRecordRequested: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (voiceState) {
            VoiceRecordingUiState.None -> OutlinedButton(onClick = onRecordRequested) {
                Text(stringResource(R.string.body_voice_record))
            }

            is VoiceRecordingUiState.Recording -> RecordingControls(voiceState.elapsedSeconds, onEvent)

            is VoiceRecordingUiState.Recorded -> RecordedControls(voiceState.isPlaying, onEvent)
        }
    }
}

@Composable
private fun RowScope.RecordingControls(elapsedSeconds: Int, onEvent: (BodyEntryEditUiEvent) -> Unit) {
    Text(
        stringResource(R.string.body_voice_recording, elapsedSeconds),
        modifier = Modifier.align(Alignment.CenterVertically),
    )
    OutlinedButton(onClick = { onEvent(BodyEntryEditUiEvent.StopRecordingVoice) }) {
        Text(stringResource(R.string.body_voice_stop))
    }
}

@Composable
private fun RecordedControls(isPlaying: Boolean, onEvent: (BodyEntryEditUiEvent) -> Unit) {
    OutlinedButton(onClick = {
        onEvent(if (isPlaying) BodyEntryEditUiEvent.StopVoice else BodyEntryEditUiEvent.PlayVoice)
    }) {
        Text(stringResource(if (isPlaying) R.string.body_voice_stop else R.string.body_voice_play))
    }
    TextButton(onClick = { onEvent(BodyEntryEditUiEvent.DeleteVoice) }) {
        Text(stringResource(R.string.body_voice_delete))
    }
}
