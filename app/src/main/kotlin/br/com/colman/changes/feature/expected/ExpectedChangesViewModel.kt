// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.expected

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.changes.core.clinical.BodyVocabularyResolver
import br.com.colman.changes.core.clinical.ClinicalLabels
import br.com.colman.changes.core.clinical.SourceKey
import br.com.colman.changes.core.data.ExpectedChangeItem
import br.com.colman.changes.core.data.ExpectedChangeRepository
import br.com.colman.changes.core.data.ExpectedTimeline
import br.com.colman.changes.core.data.ProfileRepository
import br.com.colman.changes.core.model.BodyVocabulary
import br.com.colman.changes.core.model.Profile
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

private const val STOP_TIMEOUT_MILLIS = 5_000L

/** Mês médio do calendário gregoriano expresso em meses por ano; não é um número clínico. */
private const val MONTHS_PER_YEAR = 12.0

/**
 * Linha do tempo de mudanças esperadas (Seção 7.2). Junta o dataset clínico (via [repository]), o
 * vocabulário corporal do perfil (Seção 7.10) e a primeira observação da pessoa. Nada aqui trata
 * estar fora da janela como atraso ou problema (critério 7.2.2).
 */
class ExpectedChangesViewModel(
    private val repository: ExpectedChangeRepository,
    private val profiles: ProfileRepository,
    private val clinicalLabels: ClinicalLabels,
) : ViewModel() {

    val state: StateFlow<ExpectedChangesUiState> = combine(
        repository.observeTimeline(),
        profiles.observe(),
    ) { timeline, profile -> buildState(timeline, profile) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ExpectedChangesUiState())

    private fun buildState(timeline: ExpectedTimeline, profile: Profile): ExpectedChangesUiState {
        val resolver = BodyVocabularyResolver(clinicalLabels.forLocale(localeOf(profile)))
        val citations = repository.references()
        return ExpectedChangesUiState(
            isLoading = false,
            hrtStart = timeline.hrtStart,
            monthsOnTreatment = timeline.monthsOnTreatment,
            items = timeline.items.map { it.toUiState(resolver, profile.bodyVocabulary) },
            references = SourceKey.entries.map { key -> ExpectedReferenceUiState(key, citations.getValue(key)) },
        )
    }

    private fun localeOf(profile: Profile): String = profile.locale ?: Locale.getDefault().toLanguageTag()
}

private fun ExpectedChangeItem.toUiState(
    resolver: BodyVocabularyResolver,
    vocabulary: BodyVocabulary,
): ExpectedItemUiState {
    val change = timeline.change
    val onsetMax = change.onsetMonthsMax ?: change.onsetMonthsMin
    return ExpectedItemUiState(
        changeTypeCode = change.changeTypeCode,
        name = resolver.builtinLabel(change.changeTypeCode, vocabulary),
        onsetMonthsMin = change.onsetMonthsMin,
        onsetMonthsMax = onsetMax,
        onsetRange = monthRange(change.onsetMonthsMin, onsetMax),
        maxEffectRange = maxEffectRange(change.maxEffectMonthsMin, change.maxEffectMonthsMax),
        permanence = change.permanence,
        permanenceCitation = permanenceCitation,
        sourceCitation = sourceCitation,
        status = timeline.status,
        windowStart = timeline.onsetStart,
        windowEnd = timeline.onsetEnd,
        firstObserved = timeline.firstObserved,
        firstObservedMonths = timeline.observedAtMonths,
    )
}

private fun maxEffectRange(min: Double?, max: Double?): ExpectedRangeUiState? =
    if (min != null && max != null) monthRange(min, max) else null

/** Anos quando o mínimo passa de 12 meses (critério "faixa acima de 12 meses"); meses caso contrário. */
private fun monthRange(min: Double, max: Double): ExpectedRangeUiState = if (min >= MONTHS_PER_YEAR) {
    ExpectedRangeUiState(min / MONTHS_PER_YEAR, max / MONTHS_PER_YEAR, ExpectedRangeUnit.YEARS)
} else {
    ExpectedRangeUiState(min, max, ExpectedRangeUnit.MONTHS)
}
