// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.settings

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import br.com.colman.changes.R
import br.com.colman.changes.ui.components.ChangesScreen
import br.com.colman.changes.ui.components.LoadingState
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer

/**
 * Licenças de terceiros (Seção 14), obrigatórias: dados gerados no build pelo plugin aboutlibraries
 * a partir de `R.raw.aboutlibraries`, nunca uma lista escrita à mão. Sem ViewModel.
 */
@Composable
fun LicensesRoute(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val libs by produceLibraries(R.raw.aboutlibraries)
    ChangesScreen(title = stringResource(R.string.settings_licenses_entry), onBack = onBack) { padding ->
        val current = libs
        if (current == null) {
            LoadingState(modifier = modifier.padding(padding))
        } else {
            LibrariesContainer(libraries = current, modifier = modifier.padding(padding).fillMaxSize())
        }
    }
}
