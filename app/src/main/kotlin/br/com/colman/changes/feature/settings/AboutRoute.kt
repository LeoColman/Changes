// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.colman.changes.BuildConfig
import br.com.colman.changes.R
import br.com.colman.changes.ui.components.ChangesScreen

/**
 * Tela Sobre (Seções 13 e 14): identidade do app, versão, o aviso geral (única ocorrência fora do
 * onboarding), licença e a nota de que o app não acessa a internet. Estática, sem ViewModel.
 */
@Composable
fun AboutRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    ChangesScreen(title = stringResource(R.string.settings_about_entry), onBack = onBack) { padding ->
        LazyColumn(
            modifier = modifier.padding(padding).fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall) }
            item { Text(stringResource(R.string.settings_about_version, BuildConfig.VERSION_NAME)) }
            item { Text(stringResource(R.string.sensitive_disclaimer)) }
            item {
                Text(
                    stringResource(R.string.settings_about_license_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            item { Text(stringResource(R.string.settings_about_license_body)) }
            item { Text(stringResource(R.string.settings_about_no_internet)) }
        }
    }
}
