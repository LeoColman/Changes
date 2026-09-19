// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import br.com.colman.changes.R

/**
 * Saída de uma tela de formulário (Seção 9). Com alteração não salva, o `requestLeave` que [content]
 * recebe pergunta antes de descartar, e o botão voltar do sistema passa pela mesma pergunta; sem
 * alteração, sai direto. Sair nunca grava nada: descartar só fecha a tela.
 */
@Composable
fun WithUnsavedChangesGuard(
    hasUnsavedChanges: Boolean,
    onLeave: () -> Unit,
    content: @Composable (requestLeave: () -> Unit) -> Unit,
) {
    var asking by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = hasUnsavedChanges) { asking = true }
    content { if (hasUnsavedChanges) asking = true else onLeave() }
    if (asking) {
        DiscardChangesDialog(
            onDiscard = {
                asking = false
                onLeave()
            },
            onKeepEditing = { asking = false },
        )
    }
}

/**
 * Pergunta de descartar (Seção 9): diz o que se perde, e a ação em destaque é continuar editando, não
 * a destrutiva.
 */
@Composable
fun DiscardChangesDialog(onDiscard: () -> Unit, onKeepEditing: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text(stringResource(R.string.unsaved_changes_title)) },
        text = { Text(stringResource(R.string.unsaved_changes_body)) },
        confirmButton = {
            TextButton(onClick = onKeepEditing) { Text(stringResource(R.string.action_keep_editing)) }
        },
        dismissButton = { TextButton(onClick = onDiscard) { Text(stringResource(R.string.action_discard)) } },
    )
}
