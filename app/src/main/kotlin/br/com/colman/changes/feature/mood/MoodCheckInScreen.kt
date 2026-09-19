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
import br.com.colman.changes.ui.components.SectionHeader
import br.com.colman.changes.ui.components.WithUnsavedChangesGuard

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
    WithUnsavedChangesGuard(hasUnsavedChanges = state.hasUnsavedChanges, onLeave = onBack) { requestLeave ->
        ChangesScreen(title = title, onBack = requestLeave, snackbarHostState = snackbarHostState) { padding ->
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
}

@Composable
private fun MoodCheckInForm(state: MoodCheckInUiState, onEvent: (MoodCheckInUiEvent) -> Unit) {
    FormColumn {
        Text(stringResource(R.string.mood_intro))
        MoodRequiredScales(state, onEvent)
        HorizontalDivider()
        MoodFeelingsSection(state, onEvent)
        HorizontalDivider()
        MoodScaleField(
            label = MoodScaleLabel(stringResource(R.string.mood_dysphoria)),
            value = state.dysphoria,
            onSelect = { onEvent(MoodCheckInUiEvent.DysphoriaChanged(it)) },
            allowClear = true,
        )
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
        label = MoodScaleLabel(stringResource(R.string.mood_mood)),
        value = state.mood,
        onSelect = { onEvent(MoodCheckInUiEvent.MoodChanged(it)) },
        allowClear = false,
        isError = state.moodError,
    )
    MoodScaleField(
        label = MoodScaleLabel(stringResource(R.string.mood_energy)),
        value = state.energy,
        onSelect = { onEvent(MoodCheckInUiEvent.EnergyChanged(it)) },
        allowClear = false,
        isError = state.energyError,
    )
}

/**
 * Sentimentos comuns no início da testosterona (ADR 0012): quatro escalas opcionais, cada uma com o
 * rótulo e a descrição de `strings_sensitive.xml` logo abaixo. Nenhum valor aqui é combinado, comparado
 * ou usado para tirar conclusão (critério 7.7.2).
 */
@Composable
private fun MoodFeelingsSection(state: MoodCheckInUiState, onEvent: (MoodCheckInUiEvent) -> Unit) {
    SectionHeader(stringResource(R.string.mood_feelings_section))
    Text(stringResource(R.string.mood_feelings_intro))
    FeelingScale(R.string.mood_relief, R.string.mood_relief_description, state.relief) {
        onEvent(MoodCheckInUiEvent.ReliefChanged(it))
    }
    FeelingScale(R.string.mood_irritability, R.string.mood_irritability_description, state.irritability) {
        onEvent(MoodCheckInUiEvent.IrritabilityChanged(it))
    }
    FeelingScale(
        labelRes = R.string.mood_emotional_intensity,
        descriptionRes = R.string.mood_emotional_intensity_description,
        value = state.emotionalIntensity,
        onSelect = { onEvent(MoodCheckInUiEvent.EmotionalIntensityChanged(it)) },
    )
    FeelingScale(R.string.mood_anxiety, R.string.mood_anxiety_description, state.anxiety) {
        onEvent(MoodCheckInUiEvent.AnxietyChanged(it))
    }
}

/** Uma escala da seção de sentimentos (Seção 7.7): sempre opcional, com rótulo e descrição. */
@Composable
private fun FeelingScale(labelRes: Int, descriptionRes: Int, value: Int?, onSelect: (Int?) -> Unit) {
    MoodScaleField(
        label = MoodScaleLabel(text = stringResource(labelRes), description = stringResource(descriptionRes)),
        value = value,
        onSelect = onSelect,
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
 * Rótulo de uma escala e sua descrição opcional (Seção 7.7), agrupados para não passar do limite de
 * parâmetros de [MoodScaleField]. A descrição descreve, não interpreta (ADR 0012).
 */
private data class MoodScaleLabel(val text: String, val description: String? = null)

/**
 * Escala de 1 a 5 (Seção 7.7): cada opção é um alvo de toque >= 48dp com rótulo lido pelo TalkBack
 * (`mood_scale_1..5`). Campos opcionais ([allowClear] = true) voltam a ficar em branco ao tocar de
 * novo na opção já selecionada. [label] pode trazer uma descrição curta, mostrada logo abaixo do rótulo.
 */
@Composable
private fun MoodScaleField(
    label: MoodScaleLabel,
    value: Int?,
    onSelect: (Int?) -> Unit,
    allowClear: Boolean,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label.text, style = MaterialTheme.typography.bodyLarge)
        if (label.description != null) {
            Text(
                label.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        MoodScaleOptionsRow(value = value, allowClear = allowClear, onSelect = onSelect)
        if (isError) {
            Text(
                stringResource(R.string.mood_scale_required_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/** Linha com as cinco opções da escala (Seção 7.7), cada uma um alvo de toque >= 48dp. */
@Composable
private fun MoodScaleOptionsRow(value: Int?, allowClear: Boolean, onSelect: (Int?) -> Unit) {
    val optionLabels = listOf(
        stringResource(R.string.mood_scale_1),
        stringResource(R.string.mood_scale_2),
        stringResource(R.string.mood_scale_3),
        stringResource(R.string.mood_scale_4),
        stringResource(R.string.mood_scale_5),
    )
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
