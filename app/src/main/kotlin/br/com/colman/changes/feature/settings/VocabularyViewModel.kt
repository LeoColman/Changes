// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.changes.core.clinical.ClinicalLabels
import br.com.colman.changes.core.data.ProfileRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * Tela de Vocabulário corporal fora do onboarding (Seção 7.10): cada escolha grava na hora, sem
 * botão de salvar.
 */
class VocabularyViewModel(
    private val profiles: ProfileRepository,
    private val clinicalLabels: ClinicalLabels,
) : ViewModel() {

    val state: StateFlow<VocabularyUiState> = profiles.observe().map { profile ->
        val locale = profile.locale ?: Locale.getDefault().toLanguageTag()
        VocabularyUiState(buildVocabularyRegions(clinicalLabels.forLocale(locale), profile.bodyVocabulary))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), VocabularyUiState())

    fun onEvent(event: VocabularyUiEvent) {
        val choice = event.toChoice()
        viewModelScope.launch {
            profiles.update { it.copy(bodyVocabulary = it.bodyVocabulary.with(event.region, choice)) }
        }
    }
}
