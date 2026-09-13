// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.health

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import br.com.colman.changes.R
import br.com.colman.changes.ui.components.ChangesScreen

/**
 * Aba Saúde (Seção 9): entradas para Condições e Exames (desta feature) e atalhos por callback para
 * Regimes, Medidas e Exercício, que pertencem a outras features. Sem estado próprio: é só um menu.
 */
@Composable
fun HealthHomeRoute(
    onOpenConditions: () -> Unit,
    onOpenLabs: () -> Unit,
    onOpenRegimens: () -> Unit,
    onOpenMeasurements: () -> Unit,
    onOpenExercise: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ChangesScreen(title = stringResource(R.string.tab_health)) { padding ->
        LazyColumn(modifier = modifier.fillMaxSize().padding(padding)) {
            item { HealthHomeEntry(stringResource(R.string.health_entry_conditions), onOpenConditions) }
            item { HealthHomeEntry(stringResource(R.string.health_entry_labs), onOpenLabs) }
            item { HealthHomeEntry(stringResource(R.string.health_entry_regimens), onOpenRegimens) }
            item { HealthHomeEntry(stringResource(R.string.health_entry_measurements), onOpenMeasurements) }
            item { HealthHomeEntry(stringResource(R.string.health_entry_exercise), onOpenExercise) }
        }
    }
}

@Composable
private fun HealthHomeEntry(label: String, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(label) }, modifier = Modifier.clickable(onClick = onClick))
}
