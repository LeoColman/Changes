// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.medication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.changes.core.data.DoseLogRepository
import br.com.colman.changes.core.data.MedicationRepository
import br.com.colman.changes.core.data.RegimenRepository
import br.com.colman.changes.core.model.DoseLog
import br.com.colman.changes.core.model.Medication
import br.com.colman.changes.core.model.Regimen
import br.com.colman.changes.core.model.TimeZoneProvider
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/** Histórico de doses (Seção 7.1): agrupado por mês, com filtro e adesão neutra por regime ativo. */
class DoseHistoryViewModel(
    private val doseLogRepository: DoseLogRepository,
    regimenRepository: RegimenRepository,
    medicationRepository: MedicationRepository,
    private val clock: Clock,
    private val timeZones: TimeZoneProvider,
    private val names: MedicationDisplayNames,
) : ViewModel() {

    private val medicationFilter = MutableStateFlow<Uuid?>(null)
    private val periodFilter = MutableStateFlow(HistoryPeriod.DAYS_90)

    private val effectsChannel = Channel<DoseHistoryEffect>(Channel.BUFFERED)
    val effects: Flow<DoseHistoryEffect> = effectsChannel.receiveAsFlow()

    val state: StateFlow<DoseHistoryUiState> = combine(
        medicationFilter,
        periodFilter,
        medicationRepository.observeVisible(),
        regimenRepository.observeActive(),
        doseLogRepository.observeHistory(null, FAR_PAST, FAR_FUTURE),
    ) { medicationId, period, medications, activeRegimens, logs ->
        buildState(medicationId, period, medications, activeRegimens, logs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), DoseHistoryUiState())

    fun onEvent(event: DoseHistoryUiEvent) {
        when (event) {
            is DoseHistoryUiEvent.FilterByMedication -> medicationFilter.value = event.medicationId?.let(Uuid::parse)
            is DoseHistoryUiEvent.FilterByPeriod -> periodFilter.value = event.period
            is DoseHistoryUiEvent.RequestDelete -> requestDelete(event.doseLogId)
            is DoseHistoryUiEvent.Undo -> undo(event.doseLogId)
            is DoseHistoryUiEvent.EditEntry -> edit(event.doseLogId)
            DoseHistoryUiEvent.Back -> back()
        }
    }

    private fun requestDelete(id: String) = viewModelScope.launch {
        doseLogRepository.delete(Uuid.parse(id))
        effectsChannel.send(DoseHistoryEffect.ShowUndoSnackbar(id))
    }

    private fun undo(id: String) = viewModelScope.launch { doseLogRepository.restore(Uuid.parse(id)) }

    private fun edit(id: String) = viewModelScope.launch { effectsChannel.send(DoseHistoryEffect.NavigateToEdit(id)) }

    private fun back() = viewModelScope.launch { effectsChannel.send(DoseHistoryEffect.NavigateBack) }

    private suspend fun buildState(
        medicationId: Uuid?,
        period: HistoryPeriod,
        medications: List<Medication>,
        activeRegimens: List<Regimen>,
        logs: List<DoseLog>,
    ): DoseHistoryUiState {
        val medicationById = medications.associateBy { it.id }
        val today = clock.now().toLocalDateTime(timeZones.current()).date
        val periodStart = period.days?.let { today.minus(it - 1, DateTimeUnit.DAY) }
        val filtered = logs.filter { log ->
            (medicationId == null || log.medicationId == medicationId) &&
                (periodStart == null || log.takenAt.localDate >= periodStart)
        }
        val options = medications.map { MedicationOption(it.id.toString(), names.name(it), it.defaultRoute) }
        return DoseHistoryUiState(
            isLoading = false,
            medicationFilter = medicationId?.toString(),
            availableMedications = options,
            periodFilter = period,
            adherenceSentences = adherenceSummaries(activeRegimens, medicationById, today),
            monthGroups = monthGroups(filtered, medicationById),
        )
    }

    private suspend fun adherenceSummaries(
        activeRegimens: List<Regimen>,
        medicationById: Map<Uuid, Medication>,
        today: LocalDate,
    ): List<AdherenceSummary> = activeRegimens.map { regimen ->
        val summary = doseLogRepository.adherence(regimen, today)
        AdherenceSummary(
            regimenId = regimen.id.toString(),
            medicationName = medicationById[regimen.medicationId]?.let(names::name).orEmpty(),
            registered = summary.registered,
            expected = summary.expected,
            windowDays = summary.windowDays,
        )
    }

    private fun monthGroups(logs: List<DoseLog>, medicationById: Map<Uuid, Medication>): List<MonthGroup> {
        val entries = logs.sortedByDescending { it.takenAt.instant }.map { log ->
            HistoryEntry(log = log, medicationName = medicationById[log.medicationId]?.let(names::name).orEmpty())
        }
        return entries.groupBy { yearMonthKey(it.log.takenAt.localDate) }
            .toSortedMap(compareByDescending { it })
            .map { (key, groupEntries) -> MonthGroup(key, groupEntries) }
    }

    private fun yearMonthKey(date: LocalDate): String {
        val month = date.month.ordinal + 1
        return "${date.year}-${month.toString().padStart(2, '0')}"
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        // Faixa "sempre inclui tudo": o filtro de período é aplicado depois, em memória.
        val FAR_PAST: Instant = Instant.fromEpochMilliseconds(Long.MIN_VALUE / 2)
        val FAR_FUTURE: Instant = Instant.fromEpochMilliseconds(Long.MAX_VALUE / 2)
    }
}
