// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.mood

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.colman.changes.R

/**
 * Rodapé discreto e permanente das telas de humor (Seção 7.7): acesso a "Apoio em momentos
 * difíceis" sempre visível, sem depender de rolagem.
 */
@Composable
internal fun MoodSupportFooter(onOpenSupport: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onOpenSupport, modifier = modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(stringResource(R.string.support_entry))
    }
}
