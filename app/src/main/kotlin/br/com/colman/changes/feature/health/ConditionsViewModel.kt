// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.changes.core.clinical.ClinicalLabels
import br.com.colman.changes.core.data.HealthConditionRepository
import br.com.colman.changes.core.data.ProfileRepository
import br.com.colman.changes.core.model.HealthCondition
import br.com.colman.changes.core.model.Result
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.uuid.Uuid

private const val STOP_TIMEOUT_MILLIS = 5_000L

/**
 * Tela de Condições de saúde (Seção 7.5). O app nunca classifica risco, contraindica nem sugere
 * parar o tratamento: só guarda o que a pessoa registrou.
 */
class ConditionsViewModel(
    private val conditions: HealthConditionRepository,
    private val profiles: ProfileRepository,
    private val clinicalLabels: ClinicalLabels,
) : ViewModel() {

    private val form = MutableStateFlow<ConditionFormUiState?>(null)
    private val effectsChannel = Channel<ConditionsEffect>(Channel.BUFFERED)
    private var lastDeletedId: Uuid? = null

    val effects: Flow<ConditionsEffect> = effectsChannel.receiveAsFlow()

    val state: StateFlow<ConditionsUiState> = combine(
        profiles.observe(),
        conditions.observeAll(),
        form,
    ) { profile, all, formState ->
        val locale = profile.locale ?: Locale.getDefault().toLanguageTag()
        val suggestions = clinicalLabels.forLocale(locale).conditionSuggestions
        val (pinned, others) = all.map { it.toUiState() }.partition { it.affectsTreatment }
        ConditionsUiState(
            isLoading = false,
            pinned = pinned,
            others = others,
            suggestions = suggestions,
            form = formState,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), ConditionsUiState())

    fun onEvent(event: ConditionsUiEvent) {
        when (event) {
            ConditionsUiEvent.AddRequested -> form.value = ConditionFormUiState(editingId = null)
            is ConditionsUiEvent.EditRequested -> beginEdit(event.conditionId)
            is ConditionsUiEvent.FormChanged -> form.value = form.value?.let(event.apply)
            ConditionsUiEvent.FormSaved -> save()
            ConditionsUiEvent.FormDismissed -> form.value = null
            is ConditionsUiEvent.DeleteRequested -> delete(event.conditionId)
            ConditionsUiEvent.UndoDeleteRequested -> undoDelete()
        }
    }

    private fun beginEdit(conditionId: Uuid) {
        val current = state.value
        val item = (current.pinned + current.others).firstOrNull { it.id == conditionId } ?: return
        form.value = ConditionFormUiState(
            editingId = item.id,
            label = item.label,
            code = item.code.orEmpty(),
            severity = item.severity,
            status = item.status,
            diagnosedAt = item.diagnosedAt,
            resolvedAt = item.resolvedAt,
            affectsTreatment = item.affectsTreatment,
            notes = item.notes.orEmpty(),
        )
    }

    private fun save() = viewModelScope.launch {
        val current = form.value ?: return@launch
        val model = HealthCondition(
            id = current.editingId ?: Uuid.random(),
            label = current.label.trim(),
            code = current.code.trim().ifBlank { null },
            severity = current.severity,
            status = current.status,
            diagnosedAt = current.diagnosedAt,
            resolvedAt = current.resolvedAt,
            affectsTreatment = current.affectsTreatment,
            notes = current.notes.trim().ifBlank { null },
        )
        val result = if (current.editingId == null) conditions.create(model) else conditions.update(model)
        form.value = when (result) {
            is Result.Success -> null
            is Result.Failure -> current.copy(error = result.error)
        }
    }

    private fun delete(conditionId: Uuid) = viewModelScope.launch {
        conditions.delete(conditionId)
        lastDeletedId = conditionId
        effectsChannel.send(ConditionsEffect.ShowUndoDelete)
    }

    private fun undoDelete() = viewModelScope.launch {
        lastDeletedId?.let { conditions.restore(it) }
        lastDeletedId = null
    }

    private fun HealthCondition.toUiState(): ConditionUiState =
        ConditionUiState(id, label, code, severity, status, diagnosedAt, resolvedAt, affectsTreatment, notes)
}
