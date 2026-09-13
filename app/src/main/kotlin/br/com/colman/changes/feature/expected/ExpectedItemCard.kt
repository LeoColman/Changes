// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.expected

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.colman.changes.R
import br.com.colman.changes.core.clinical.ExpectedChangeStatus
import br.com.colman.changes.core.clinical.Permanence
import br.com.colman.changes.ui.components.SectionHeader
import br.com.colman.changes.ui.format.Formatters
import kotlinx.datetime.LocalDate

/**
 * Cartão de uma mudança (Seção 7.2): nome (resolvido pelo vocabulário corporal), início típico,
 * efeito máximo, permanência, estado (só com data de início), janela em datas, primeira observação
 * da pessoa quando existir, e a fonte. Nenhuma cor ou ordenação distingue quem está fora da janela.
 */
@Composable
fun ExpectedItemCard(item: ExpectedItemUiState) {
    val fieldModifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        SectionHeader(item.name)
        Text(
            text = stringResource(R.string.expected_onset, rangeText(item.onsetRange)),
            modifier = fieldModifier,
        )
        Text(
            text = stringResource(R.string.expected_max_effect, maxEffectText(item.maxEffectRange)),
            modifier = fieldModifier,
        )
        Text(text = permanenceText(item.permanence), modifier = fieldModifier)
        item.status?.let { status -> Text(text = statusText(status), modifier = fieldModifier) }
        item.windowStart?.let { start -> Text(text = windowText(start, item.windowEnd), modifier = fieldModifier) }
        item.firstObserved?.let { date ->
            Text(text = firstObservationText(date, item.firstObservedMonths), modifier = fieldModifier)
        }
        Text(text = stringResource(R.string.expected_source, item.sourceCitation), modifier = fieldModifier)
        item.permanenceCitation?.let { citation ->
            Text(text = stringResource(R.string.expected_source, citation), modifier = fieldModifier)
        }
        HorizontalDivider()
    }
}

@Composable
private fun rangeText(range: ExpectedRangeUiState): String {
    val template = if (range.unit == ExpectedRangeUnit.YEARS) {
        R.string.expected_range_years
    } else {
        R.string.expected_range_months
    }
    return stringResource(template, Formatters.number(range.min, 0), Formatters.number(range.max, 0))
}

@Composable
private fun maxEffectText(range: ExpectedRangeUiState?): String =
    if (range != null) rangeText(range) else stringResource(R.string.expected_not_stated)

@Composable
private fun permanenceText(permanence: Permanence): String = stringResource(
    when (permanence) {
        Permanence.PERMANENT -> R.string.expected_permanence_permanent
        Permanence.PARTIALLY_PERMANENT -> R.string.expected_permanence_partial
        Permanence.NOT_PERMANENT -> R.string.expected_permanence_not_permanent
        Permanence.NOT_STATED -> R.string.expected_permanence_not_stated
    },
)

@Composable
private fun statusText(status: ExpectedChangeStatus): String = stringResource(
    when (status) {
        ExpectedChangeStatus.NOT_YET_EXPECTED -> R.string.expected_status_not_yet
        ExpectedChangeStatus.WITHIN_ONSET_WINDOW -> R.string.expected_status_within
        ExpectedChangeStatus.PAST_ONSET_WINDOW -> R.string.expected_status_past
    },
)

@Composable
private fun windowText(start: LocalDate, end: LocalDate?): String = if (end != null) {
    stringResource(R.string.expected_window_dates, Formatters.date(start), Formatters.date(end))
} else {
    stringResource(R.string.expected_window_open, Formatters.date(start))
}

@Composable
private fun firstObservationText(date: LocalDate, months: Double?): String = if (months != null) {
    stringResource(R.string.expected_first_observation_months, Formatters.date(date), Formatters.number(months, 0))
} else {
    stringResource(R.string.expected_first_observation, Formatters.date(date))
}
