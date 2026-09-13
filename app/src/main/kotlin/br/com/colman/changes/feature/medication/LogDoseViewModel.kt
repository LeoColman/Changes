// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.medication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.changes.core.data.DoseLogRepository
import br.com.colman.changes.core.data.MedicationRepository
import br.com.colman.changes.core.data.NewDoseLog
import br.com.colman.changes.core.data.RegimenRepository
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.Dose
import br.com.colman.changes.core.model.DoseLog
import br.com.colman.changes.core.model.RecordedTime
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.model.TimeZoneProvider
import br.com.colman.changes.ui.format.Formatters
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Registrar dose (Seção 7.1): um toque a partir de um regime, ou registro avulso/edição. */
class LogDoseViewModel(
    private val doseLogRepository: DoseLogRepository,
    private val regimenRepository: RegimenRepository,
    medicationRepository: MedicationRepository,
    displayNames: MedicationDisplayNames,
    private val clock: Clock,
    private val timeZones: TimeZoneProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(LogDoseUiState(date = today(), time = nowTime()))
    val state: StateFlow<LogDoseUiState> = _state.asStateFlow()

    private val effectsChannel = Channel<LogDoseEffect>(Channel.BUFFERED)
    val effects: Flow<LogDoseEffect> = effectsChannel.receiveAsFlow()

    private var doseLogId: Uuid? = null

    init {
        viewModelScope.launch {
            medicationRepository.observeVisible().collect { medications ->
                val options = medications.map { medication ->
                    MedicationOption(medication.id.toString(), displayNames.name(medication), medication.defaultRoute)
                }
                _state.update { it.copy(availableMedications = options) }
            }
        }
    }

    private fun today(): LocalDate = clock.now().toLocalDateTime(timeZones.current()).date
    private fun nowTime() = clock.now().toLocalDateTime(timeZones.current()).time

    fun onEvent(event: LogDoseUiEvent) {
        when (event) {
            is LogDoseUiEvent.Load -> load(event.regimenId, event.plannedEpochDay, event.doseLogId)
            is LogDoseUiEvent.FieldChanged -> _state.update(event.apply)
            LogDoseUiEvent.Save -> save()
            LogDoseUiEvent.RequestDelete -> _state.update { it.copy(showDeleteConfirm = true) }
            LogDoseUiEvent.ConfirmDelete -> delete()
            LogDoseUiEvent.CancelDelete -> _state.update { it.copy(showDeleteConfirm = false) }
            LogDoseUiEvent.Back -> viewModelScope.launch { effectsChannel.send(LogDoseEffect.NavigateBack) }
        }
    }

    private fun load(regimenId: String?, plannedEpochDay: Long?, doseLogId: String?) = viewModelScope.launch {
        val logId = doseLogId?.let(Uuid::parse)
        this@LogDoseViewModel.doseLogId = logId
        when {
            logId != null -> loadExistingLog(logId)
            regimenId != null -> loadFromRegimen(Uuid.parse(regimenId), plannedEpochDay)
            else -> _state.update { it.copy(isLoading = false) }
        }
    }

    private suspend fun loadExistingLog(id: Uuid) {
        val log = doseLogRepository.get(id)
        if (log == null) {
            _state.update { it.copy(isLoading = false, error = DomainError.NotFound("doseLog", id.toString())) }
            return
        }
        _state.update {
            it.copy(
                isLoading = false,
                isEditing = true,
                regimenId = log.regimenId?.toString(),
                selectedMedicationId = log.medicationId.toString(),
                doseValue = Formatters.number(log.dose.value, MAX_DOSE_FRACTION_DIGITS),
                doseUnit = log.dose.unit,
                route = log.route,
                injectionSite = log.injectionSite,
                date = log.takenAt.localDate,
                time = log.takenAt.localDateTime.time,
                notes = log.notes.orEmpty(),
            )
        }
    }

    private suspend fun loadFromRegimen(regimenId: Uuid, plannedEpochDay: Long?) {
        val regimen = regimenRepository.get(regimenId)
        val suggested = if (regimen?.route?.isInjection == true) doseLogRepository.suggestInjectionSite() else null
        val date = plannedEpochDay?.let(LocalDate::fromEpochDays) ?: today()
        _state.update { current ->
            current.copy(
                isLoading = false,
                regimenId = regimenId.toString(),
                selectedMedicationId = regimen?.medicationId?.toString(),
                doseValue = regimen?.dose?.value?.let { Formatters.number(it, MAX_DOSE_FRACTION_DIGITS) }.orEmpty(),
                doseUnit = regimen?.dose?.unit ?: current.doseUnit,
                route = regimen?.route ?: current.route,
                injectionSite = suggested,
                date = date,
                time = regimen?.timeOfDay ?: current.time,
            )
        }
    }

    private fun save() = viewModelScope.launch {
        val current = _state.value
        val medicationId = current.selectedMedicationId?.let(Uuid::parse)
        if (medicationId == null) {
            _state.update { it.copy(error = DomainError.Invalid("medicationId", DomainError.Reason.REQUIRED)) }
            return@launch
        }
        val takenAt = LocalDateTime(current.date, current.time).toInstant(timeZones.current())
        val newLog = NewDoseLog(
            regimenId = current.regimenId?.let(Uuid::parse),
            medicationId = medicationId,
            dose = Dose(Formatters.parseNumber(current.doseValue) ?: Double.NaN, current.doseUnit),
            route = current.route,
            injectionSite = current.injectionSite,
            takenAt = takenAt,
            notes = current.notes.ifBlank { null },
        )
        val id = doseLogId
        val result = if (id == null) {
            doseLogRepository.log(newLog)
        } else {
            doseLogRepository.update(existingLog(id, newLog))
        }
        when (result) {
            is Result.Success -> effectsChannel.send(LogDoseEffect.NavigateBack)
            is Result.Failure -> _state.update { it.copy(error = result.error) }
        }
    }

    private fun existingLog(id: Uuid, newLog: NewDoseLog) = DoseLog(
        id = id,
        regimenId = newLog.regimenId,
        medicationId = newLog.medicationId,
        dose = newLog.dose,
        route = newLog.route,
        injectionSite = newLog.injectionSite,
        takenAt = RecordedTime.of(newLog.takenAt, timeZones.current()),
        notes = newLog.notes,
    )

    private fun delete() = viewModelScope.launch {
        val id = doseLogId ?: return@launch
        doseLogRepository.delete(id)
        effectsChannel.send(LogDoseEffect.NavigateBack)
    }

    private companion object {
        const val MAX_DOSE_FRACTION_DIGITS = 4
    }
}
