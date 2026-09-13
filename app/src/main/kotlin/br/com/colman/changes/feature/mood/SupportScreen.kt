// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.mood

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.colman.changes.R
import br.com.colman.changes.ui.components.ChangesScreen

private const val CVV_TEL_URI = "tel:188"

/**
 * Apoio em momentos difíceis (Seção 7.7): texto estático, sem detecção automática de crise e sem
 * análise de nota. O botão do CVV abre o discador já preenchido com `tel:188` (não faz a ligação
 * sozinho e não pede permissão) via [LocalUriHandler], em vez de `Intent.ACTION_DIAL` (Seção 4.2).
 */
@Composable
fun SupportScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    ChangesScreen(title = stringResource(R.string.support_title), onBack = onBack) { padding ->
        Column(
            modifier = modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.support_body))
            SupportOrgCard(
                title = stringResource(R.string.support_cvv_title),
                body = stringResource(R.string.support_cvv_body),
            ) {
                Button(onClick = { uriHandler.openUri(CVV_TEL_URI) }) {
                    Text(stringResource(R.string.support_call_cvv))
                }
            }
            SupportOrgCard(
                title = stringResource(R.string.support_caps_title),
                body = stringResource(R.string.support_caps_body),
            )
        }
    }
}

@Composable
private fun SupportOrgCard(title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Card {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            action?.invoke()
        }
    }
}
