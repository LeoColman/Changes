// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.colman.changes.R
import br.com.colman.changes.ui.components.ChangesScreen
import br.com.colman.changes.ui.components.SectionHeader
import org.koin.androidx.compose.koinViewModel

/** Wrapper fino (Seção 5) da tela de Vocabulário corporal (Seção 7.10). */
@Composable
fun VocabularyRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VocabularyViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ChangesScreen(title = stringResource(R.string.vocabulary_title), onBack = onBack) { padding ->
        VocabularyContent(state = state, onEvent = viewModel::onEvent, modifier = modifier.padding(padding))
    }
}

/**
 * Conteúdo sem estado próprio, reaproveitado pelo passo 4 do onboarding (Seção 9): a mesma tela,
 * alimentada ora pelo perfil gravado, ora por um rascunho ainda não gravado.
 */
@Composable
fun VocabularyContent(state: VocabularyUiState, onEvent: (VocabularyUiEvent) -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        item { Text(stringResource(R.string.vocabulary_intro), modifier = Modifier.padding(16.dp)) }
        items(state.regions, key = { it.region.name }) { region -> VocabularyRegionCard(region, onEvent) }
    }
}

@Composable
private fun VocabularyRegionCard(region: VocabularyRegionUiState, onEvent: (VocabularyUiEvent) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).selectableGroup(),
    ) {
        SectionHeader(region.regionName)
        region.options.forEach { option ->
            VocabularyOptionRow(
                label = option.label,
                selected = !region.isCustomSelected && region.selectedOption == option.code,
                onClick = { onEvent(VocabularyUiEvent.OptionSelected(region.region, option.code)) },
            )
        }
        VocabularyOptionRow(
            label = stringResource(R.string.vocabulary_custom),
            selected = region.isCustomSelected,
            onClick = { onEvent(VocabularyUiEvent.CustomSelected(region.region)) },
        )
        if (region.isCustomSelected) {
            OutlinedTextField(
                value = region.customText,
                onValueChange = { onEvent(VocabularyUiEvent.CustomTextChanged(region.region, it)) },
                label = { Text(stringResource(R.string.vocabulary_custom_hint)) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
        Text(
            text = stringResource(R.string.vocabulary_preview, region.previewLabel),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun VocabularyOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
