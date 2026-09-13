// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.medication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.changes.core.data.MedicationRepository
import br.com.colman.changes.core.data.RegimenRepository
import br.com.colman.changes.core.model.DoseSchedule
import br.com.colman.changes.core.model.Medication
import br.com.colman.changes.core.model.Regimen
import br.com.colman.changes.core.model.TimeZoneProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

/** Lista de regimes (Seção 7.1): ativos primeiro, depois encerrados. */
class RegimenListViewModel(
    regimenRepository: RegimenRepository,
    medicationRepository: MedicationRepository,
    private val clock: Clock,
    private val timeZones: TimeZoneProvider,
    private val names: MedicationDisplayNames,
) : ViewModel() {

    private val effectsChannel = Channel<RegimenListEffect>(Channel.BUFFERED)
    val effects: Flow<RegimenListEffect> = effectsChannel.receiveAsFlow()

    val state: StateFlow<RegimenListUiState> = combine(
        regimenRepository.observeAll(),
        medicationRepository.observeAll(),
    ) { regimens, medications -> buildState(regimens, medications) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), RegimenListUiState())

    fun onEvent(event: RegimenListUiEvent) {
        viewModelScope.launch {
            effectsChannel.send(
                when (event) {
                    is RegimenListUiEvent.OpenRegimen -> RegimenListEffect.NavigateToRegimen(event.id)
                    RegimenListUiEvent.CreateRegimen -> RegimenListEffect.NavigateToCreate
                    RegimenListUiEvent.OpenHistory -> RegimenListEffect.NavigateToHistory
                    RegimenListUiEvent.Back -> RegimenListEffect.NavigateBack
                },
            )
        }
    }

    private fun buildState(regimens: List<Regimen>, medications: List<Medication>): RegimenListUiState {
        val medicationById = medications.associateBy { it.id }
        val today = clock.now().toLocalDateTime(timeZones.current()).date
        val items = regimens.map { regimen ->
            RegimenListItem(
                regimen = regimen,
                medicationName = medicationById[regimen.medicationId]?.let(names::name).orEmpty(),
                nextDose = if (regimen.isActive) DoseSchedule.next(regimen, today) else null,
            )
        }
        val (active, ended) = items.partition { it.regimen.isActive }
        return RegimenListUiState(
            isLoading = false,
            activeRegimens = active.sortedBy { it.nextDose?.toEpochDays() ?: Long.MAX_VALUE },
            endedRegimens = ended.sortedByDescending { it.regimen.endDate?.toEpochDays() ?: Long.MIN_VALUE },
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
