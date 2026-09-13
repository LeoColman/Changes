// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.expected

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.colman.changes.R
import br.com.colman.changes.ui.components.ChangesScreen
import br.com.colman.changes.ui.components.LoadingState
import br.com.colman.changes.ui.components.SectionHeader

/**
 * Tela de mudanças esperadas (Seção 7.2), sem estado próprio de negócio. Todo texto de conteúdo vem
 * de `strings_sensitive.xml` (contrato de tela sensível); esta feature só define rótulos de navegação.
 */
@Composable
fun ExpectedChangesScreen(
    state: ExpectedChangesUiState,
    onBack: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ChangesScreen(title = stringResource(R.string.expected_title), onBack = onBack) { padding ->
        ExpectedChangesContent(state = state, onOpenProfile = onOpenProfile, modifier = modifier.padding(padding))
    }
}

@Composable
private fun ExpectedChangesContent(state: ExpectedChangesUiState, onOpenProfile: () -> Unit, modifier: Modifier) {
    if (state.isLoading) {
        LoadingState(modifier = modifier)
        return
    }
    val hrtStart = state.hrtStart
    val monthsOnTreatment = state.monthsOnTreatment
    LazyColumn(modifier = modifier.fillMaxSize()) {
        item { Text(stringResource(R.string.expected_disclaimer), modifier = Modifier.padding(16.dp)) }
        if (hrtStart == null) {
            item { NoStartDateBanner(onOpenProfile) }
        } else if (monthsOnTreatment != null) {
            item { ExpectedTimeline(state.items, monthsOnTreatment) }
        }
        items(state.items, key = { it.changeTypeCode }) { item -> ExpectedItemCard(item) }
        item { ExpectedSourcesSection(state.references) }
    }
}

@Composable
private fun ExpectedTimeline(items: List<ExpectedItemUiState>, monthsOnTreatment: Double) {
    val summary = pluralStringResource(R.plurals.expected_chart_summary, items.size, items.size)
    ExpectedTimelineChart(
        items = items,
        todayMonths = monthsOnTreatment,
        summary = summary,
        modifier = Modifier.padding(16.dp),
    )
}

@Composable
private fun NoStartDateBanner(onOpenProfile: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text(stringResource(R.string.expected_no_start_date))
        TextButton(onClick = onOpenProfile) { Text(stringResource(R.string.expected_set_start_date)) }
    }
}

@Composable
private fun ExpectedSourcesSection(references: List<ExpectedReferenceUiState>) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.expected_sources_title))
        references.forEach { reference ->
            Text(reference.citation, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }
    }
}
