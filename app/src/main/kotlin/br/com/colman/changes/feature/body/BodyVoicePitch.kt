// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.colman.changes.R
import br.com.colman.changes.ui.chart.ChartBand
import br.com.colman.changes.ui.chart.ChartPoint
import br.com.colman.changes.ui.chart.ChartSeries
import br.com.colman.changes.ui.chart.LineChart
import br.com.colman.changes.ui.components.SectionHeader
import br.com.colman.changes.ui.format.Formatters

/**
 * Frequência da voz (ADR 0014): o status da estimativa no formulário da entrada e o gráfico na tela do
 * tipo.
 */

/**
 * Faixa de referência do gráfico (ADR 0014): 90 a 155 Hz, frequência fundamental típica da fala de homens
 * adultos jovens em Fitch e Holbrook (1970), como citada em Baken e Orlikoff (2000). Cor neutra, sem meta.
 */
private val REFERENCE_BAND = ChartBand(low = 90.0, high = 155.0)

/** No formulário: a medida em Hz sai da gravação; o que a estimativa está fazendo e o botão de estimar de novo. */
@Composable
fun PitchEstimateStatus(state: BodyEntryEditUiState, onEvent: (BodyEntryEditUiEvent) -> Unit) {
    Text(stringResource(R.string.body_voice_pitch_explanation), style = MaterialTheme.typography.bodyMedium)
    when {
        state.isEstimatingPitch -> Text(stringResource(R.string.body_voice_pitch_estimating))
        state.voiceState is VoiceRecordingUiState.Recorded -> {
            if (state.pitchUnavailable) Text(stringResource(R.string.body_voice_pitch_unavailable))
            TextButton(onClick = { onEvent(BodyEntryEditUiEvent.EstimatePitch) }) {
                Text(stringResource(R.string.body_voice_pitch_estimate))
            }
        }
    }
}

/**
 * Na tela do tipo: a frequência da voz ao longo do tempo, uma linha com a frequência em Hz de cada entrada
 * sobre a faixa de referência. Quanto mais baixa a linha, mais grave a voz.
 */
@Composable
fun VoicePitchSection(points: List<VoicePitchPoint>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(stringResource(R.string.body_voice_chart_title))
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (points.isEmpty()) {
                Text(stringResource(R.string.body_voice_chart_empty), style = MaterialTheme.typography.bodyMedium)
            } else {
                VoicePitchChart(points)
            }
        }
    }
}

@Composable
private fun VoicePitchChart(points: List<VoicePitchPoint>) {
    val first = points.first()
    val latest = points.last()
    Text(stringResource(R.string.body_voice_chart_explanation), style = MaterialTheme.typography.bodyMedium)
    LineChart(
        series = listOf(ChartSeries(points.map { ChartPoint(it.observedAt.epochMillis.toDouble(), it.hz) })),
        summary = pluralStringResource(
            R.plurals.body_voice_chart_summary,
            points.size,
            points.size,
            Formatters.shortDate(first.observedAt.localDate),
            Formatters.shortDate(latest.observedAt.localDate),
            Formatters.number(first.hz),
            Formatters.number(latest.hz),
        ),
        band = REFERENCE_BAND,
    )
    PitchValue(R.string.body_voice_chart_first, first)
    if (points.size > 1) PitchValue(R.string.body_voice_chart_latest, latest)
    Text(stringResource(R.string.voice_reference_band), style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.voice_reference_source), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun PitchValue(@StringRes label: Int, point: VoicePitchPoint) {
    Text(
        stringResource(label, Formatters.number(point.hz), Formatters.shortDate(point.observedAt.localDate)),
        style = MaterialTheme.typography.bodyMedium,
    )
}
