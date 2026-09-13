// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.changes.core.clinical.ClinicalLabels
import br.com.colman.changes.core.data.MeasurementRepository
import br.com.colman.changes.core.data.ProfileRepository
import br.com.colman.changes.core.model.BodyVocabulary
import br.com.colman.changes.core.model.MeasurementType
import br.com.colman.changes.core.model.MeasurementUnit
import br.com.colman.changes.platform.SettingsStore
import br.com.colman.changes.ui.format.Formatters
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import java.util.Locale
import kotlin.time.Clock

private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * Onboarding (Seção 9): no máximo 4 passos, todos puláveis, sem questionário obrigatório. Nada é
 * gravado no perfil até terminar ou pular: o rascunho vive só neste ViewModel, e o passo de
 * vocabulário reaproveita o mesmo composable da tela de Vocabulário ([VocabularyContent]).
 */
class OnboardingViewModel(
    private val profiles: ProfileRepository,
    private val clinicalLabels: ClinicalLabels,
    private val settings: SettingsStore,
    private val measurements: MeasurementRepository,
    private val clock: Clock,
) : ViewModel() {

    private val draft = MutableStateFlow(Draft())
    private val effectChannel = Channel<OnboardingEffect>(Channel.BUFFERED)
    val effects: Flow<OnboardingEffect> = effectChannel.receiveAsFlow()

    val state: StateFlow<OnboardingUiState> = draft.map { it.toUiState(clinicalLabels) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            draft.value.toUiState(clinicalLabels)
        )

    fun onEvent(event: OnboardingUiEvent) {
        when (event) {
            OnboardingUiEvent.NextStep -> draft.update {
                it.copy(
                    step = (it.step + 1).coerceAtMost(ONBOARDING_LAST_STEP)
                )
            }
            OnboardingUiEvent.Finished -> finish()
            is OnboardingUiEvent.HrtStartDateChanged -> draft.update { it.copy(hrtStartDate = event.date) }
            is OnboardingUiEvent.HeightChanged -> changeHeight(event.text)
            is OnboardingUiEvent.ShowBmiChanged ->
                draft.update { it.copy(showBmi = event.enabled && it.heightText.isNotBlank()) }
            is OnboardingUiEvent.WeightChanged ->
                draft.update { it.copy(weightText = event.text, weightError = isInvalidWeight(event.text)) }
            is OnboardingUiEvent.VocabularyChanged ->
                draft.update { it.copy(vocabulary = it.vocabulary.with(event.event.region, event.event.toChoice())) }
        }
    }

    /** Altura vazia desliga "mostrar IMC" (Seção 9: só liga se houver altura). */
    private fun changeHeight(text: String) = draft.update { current ->
        val heightCm = Formatters.parseNumber(text)
        current.copy(heightText = text, showBmi = current.showBmi && heightCm != null)
    }

    /**
     * Grava só o que foi preenchido; pular sem preencher nada não grava nada no perfil. O peso
     * (Seção 7.4) nunca depende do "mostrar IMC" e nunca bloqueia Pular nem Concluir: texto inválido
     * só marca erro no campo, sem gravar.
     */
    private fun finish() = viewModelScope.launch {
        val current = draft.value
        val heightCm = Formatters.parseNumber(current.heightText)
        val vocabularyTouched = current.vocabulary.choices.isNotEmpty()
        if (current.hrtStartDate != null || heightCm != null || vocabularyTouched) {
            profiles.update { profile ->
                profile.copy(
                    hrtStartDate = current.hrtStartDate ?: profile.hrtStartDate,
                    heightCm = heightCm ?: profile.heightCm,
                    showBmi = if (heightCm != null) current.showBmi else profile.showBmi,
                    bodyVocabulary = if (vocabularyTouched) current.vocabulary else profile.bodyVocabulary,
                )
            }
        }
        if (recordWeightIfPresent(current.weightText)) {
            draft.update { it.copy(weightError = true) }
        }
        settings.update { it.copy(onboardingDone = true) }
        effectChannel.send(OnboardingEffect.Finished)
    }

    /**
     * Texto preenchido que não é número maior que zero. O erro aparece enquanto se digita: ao terminar
     * a tela fecha, então marcar só no fim não seria visto.
     */
    private fun isInvalidWeight(text: String): Boolean {
        val weightKg = Formatters.parseNumber(text)
        return text.isNotBlank() && (weightKg == null || weightKg <= 0.0)
    }

    /** `true` se o texto não estava vazio mas não era um número maior que zero (erro, nada gravado). */
    private suspend fun recordWeightIfPresent(text: String): Boolean {
        if (text.isBlank()) return false
        val weightKg = Formatters.parseNumber(text)
        if (weightKg == null || weightKg <= 0.0) return true
        measurements.create(MeasurementType.WEIGHT, null, weightKg, MeasurementUnit.KG, clock.now(), null)
        return false
    }

    private data class Draft(
        val step: Int = 0,
        val hrtStartDate: LocalDate? = null,
        val heightText: String = "",
        val showBmi: Boolean = false,
        val weightText: String = "",
        val weightError: Boolean = false,
        val vocabulary: BodyVocabulary = BodyVocabulary(),
        val locale: String = Locale.getDefault().toLanguageTag(),
    )

    private fun Draft.toUiState(labels: ClinicalLabels): OnboardingUiState = OnboardingUiState(
        step = step,
        hrtStartDate = hrtStartDate,
        heightText = heightText,
        showBmi = showBmi,
        weightText = weightText,
        weightError = weightError,
        vocabulary = VocabularyUiState(buildVocabularyRegions(labels.forLocale(locale), vocabulary)),
    )
}
