// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.mood

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.colman.changes.R
import br.com.colman.changes.ui.components.ChangesScreen
import br.com.colman.changes.ui.components.FormColumn
import br.com.colman.changes.ui.components.LoadingState
import br.com.colman.changes.ui.components.NumberField

private const val SCALE_OPTIONS = 5
private val SCALE_TARGET_SIZE = 48.dp

/**
 * Check-in diário de saúde mental, sem estado próprio de negócio (Seção 5). Sem escala clínica, sem
 * gamificação: cada campo é um fato do dia, guardado como veio (Seção 7.7).
 */
@Composable
fun MoodCheckInScreen(
    state: MoodCheckInUiState,
    onEvent: (MoodCheckInUiEvent) -> Unit,
    onOpenSupport: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val title = stringResource(R.string.mood_title)
    ChangesScreen(title = title, onBack = onBack, snackbarHostState = snackbarHostState) { padding ->
        if (state.isLoading) {
            LoadingState(modifier = modifier.padding(padding))
        } else {
            Column(modifier = modifier.padding(padding).fillMaxSize()) {
                Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    MoodCheckInForm(state, onEvent)
                }
                HorizontalDivider()
                MoodSupportFooter(onOpenSupport)
            }
        }
    }
}

@Composable
private fun MoodCheckInForm(state: MoodCheckInUiState, onEvent: (MoodCheckInUiEvent) -> Unit) {
    FormColumn {
        Text(stringResource(R.string.mood_intro))
        MoodRequiredScales(state, onEvent)
        MoodOptionalScales(state, onEvent)
        NumberField(
            value = state.sleepHoursText,
            onValueChange = { onEvent(MoodCheckInUiEvent.SleepHoursChanged(it)) },
            label = stringResource(R.string.mood_sleep),
            isError = state.sleepHoursError,
            supportingText = if (state.sleepHoursError) stringResource(R.string.mood_sleep_hours_error) else null,
        )
        MoodNoteField(state.note, onEvent)
        OutlinedTextField(
            value = state.tagsText,
            onValueChange = { onEvent(MoodCheckInUiEvent.TagsChanged(it)) },
            label = { Text(stringResource(R.string.mood_tags)) },
            placeholder = { Text(stringResource(R.string.mood_tags_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { onEvent(MoodCheckInUiEvent.Save) }) {
            Text(stringResource(R.string.action_save))
        }
    }
}

@Composable
private fun MoodRequiredScales(state: MoodCheckInUiState, onEvent: (MoodCheckInUiEvent) -> Unit) {
    MoodScaleField(
        label = stringResource(R.string.mood_mood),
        value = state.mood,
        onSelect = { onEvent(MoodCheckInUiEvent.MoodChanged(it)) },
        allowClear = false,
        isError = state.moodError,
    )
    MoodScaleField(
        label = stringResource(R.string.mood_energy),
        value = state.energy,
        onSelect = { onEvent(MoodCheckInUiEvent.EnergyChanged(it)) },
        allowClear = false,
        isError = state.energyError,
    )
}

@Composable
private fun MoodOptionalScales(state: MoodCheckInUiState, onEvent: (MoodCheckInUiEvent) -> Unit) {
    MoodScaleField(
        label = stringResource(R.string.mood_anxiety),
        value = state.anxiety,
        onSelect = { onEvent(MoodCheckInUiEvent.AnxietyChanged(it)) },
        allowClear = true,
    )
    MoodScaleField(
        label = stringResource(R.string.mood_dysphoria),
        value = state.dysphoria,
        onSelect = { onEvent(MoodCheckInUiEvent.DysphoriaChanged(it)) },
        allowClear = true,
    )
}

@Composable
private fun MoodNoteField(note: String, onEvent: (MoodCheckInUiEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = note,
            onValueChange = { onEvent(MoodCheckInUiEvent.NoteChanged(it)) },
            label = { Text(stringResource(R.string.mood_note)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.mood_note_private), style = MaterialTheme.typography.bodySmall)
    }
}

/**
 * Escala de 1 a 5 (Seção 7.7): cada opção é um alvo de toque >= 48dp com rótulo lido pelo TalkBack
 * (`mood_scale_1..5`). Campos opcionais ([allowClear] = true) voltam a ficar em branco ao tocar de
 * novo na opção já selecionada.
 */
@Composable
private fun MoodScaleField(
    label: String,
    value: Int?,
    onSelect: (Int?) -> Unit,
    allowClear: Boolean,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val optionLabels = listOf(
        stringResource(R.string.mood_scale_1),
        stringResource(R.string.mood_scale_2),
        stringResource(R.string.mood_scale_3),
        stringResource(R.string.mood_scale_4),
        stringResource(R.string.mood_scale_5),
    )
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().selectableGroup(),
        ) {
            for (index in 0 until SCALE_OPTIONS) {
                val scaleValue = index + 1
                MoodScaleOption(
                    scaleValue = scaleValue,
                    label = optionLabels[index],
                    selected = value == scaleValue,
                    onClick = { onSelect(if (allowClear && value == scaleValue) null else scaleValue) },
                )
            }
        }
        if (isError) {
            Text(
                stringResource(R.string.mood_scale_required_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun MoodScaleOption(scaleValue: Int, label: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier = Modifier
            .size(SCALE_TARGET_SIZE)
            .clip(CircleShape)
            .background(background)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .semantics { contentDescription = label },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "$scaleValue", color = contentColor, modifier = Modifier.clearAndSetSemantics {})
    }
}
