// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.colman.changes.R
import br.com.colman.changes.ui.format.Formatters

/**
 * Reprodução das gravações de voz de um tipo (ADR 0013): botão Ouvir por entrada, e "Ouvir a
 * primeira"/"Ouvir a mais recente" no topo para comparar. Um áudio por vez (`playingEntryId`).
 */

/** Botão Ouvir de uma entrada com gravação de voz; vira Parar enquanto ela toca. */
@Composable
fun EntryVoiceButton(entry: BodyEntrySummary, playing: Boolean, onEvent: (BodyChangeTypeUiEvent) -> Unit) {
    val description = stringResource(R.string.body_voice_play_entry, Formatters.recorded(entry.observedAt))
    IconButton(onClick = {
        val event = if (playing) BodyChangeTypeUiEvent.StopVoice else BodyChangeTypeUiEvent.PlayVoice(entry.entryId)
        onEvent(event)
    }) {
        Icon(if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = description)
    }
}

/** Ouvir a primeira e a mais recente gravação de voz do tipo, para comparar. */
@Composable
fun VoiceCompareSection(
    entries: List<BodyEntrySummary>,
    playingEntryId: String?,
    onEvent: (BodyChangeTypeUiEvent) -> Unit,
) {
    val withVoice = entries.filter { it.voice != null }
    if (withVoice.size < 2) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        VoicePlayButton(
            label = stringResource(R.string.body_voice_play_first),
            entryId = withVoice.first().entryId,
            playingEntryId = playingEntryId,
            onEvent = onEvent,
        )
        VoicePlayButton(
            label = stringResource(R.string.body_voice_play_latest),
            entryId = withVoice.last().entryId,
            playingEntryId = playingEntryId,
            onEvent = onEvent,
        )
    }
}

@Composable
private fun VoicePlayButton(
    label: String,
    entryId: String,
    playingEntryId: String?,
    onEvent: (BodyChangeTypeUiEvent) -> Unit,
) {
    val playing = playingEntryId == entryId
    OutlinedButton(onClick = {
        onEvent(if (playing) BodyChangeTypeUiEvent.StopVoice else BodyChangeTypeUiEvent.PlayVoice(entryId))
    }) {
        Text(if (playing) stringResource(R.string.body_voice_stop) else label)
    }
}
