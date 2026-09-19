// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.medication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.changes.core.data.MedicationRepository
import br.com.colman.changes.core.data.NewRegimen
import br.com.colman.changes.core.data.RegimenRepository
import br.com.colman.changes.core.model.Concentration
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.Dose
import br.com.colman.changes.core.model.DoseSchedule
import br.com.colman.changes.core.model.Regimen
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.model.Route
import br.com.colman.changes.core.model.Schedule
import br.com.colman.changes.core.model.TimeZoneProvider
import br.com.colman.changes.core.model.isSuccess
import br.com.colman.changes.ui.format.Formatters
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.uuid.Uuid

/** Criar/editar regime (Seção 7.1). Dose nasce vazia: nenhuma sugestão de valor. */
class RegimenEditViewModel(
    private val regimenRepository: RegimenRepository,
    private val medicationRepository: MedicationRepository,
    private val clock: Clock,
    private val timeZones: TimeZoneProvider,
    private val names: MedicationDisplayNames,
) : ViewModel() {

    private val today get() = clock.now().toLocalDateTime(timeZones.current()).date

    private val _state = MutableStateFlow(RegimenEditUiState(startDate = today))
    val state: StateFlow<RegimenEditUiState> = _state.asStateFlow()

    private val effectsChannel = Channel<RegimenEditEffect>(Channel.BUFFERED)
    val effects: Flow<RegimenEditEffect> = effectsChannel.receiveAsFlow()

    private var regimenId: Uuid? = null
    private var loadedSchedule: Schedule? = null

    /** Seção 9: instantâneo do formulário no último carregamento ou salvamento, para comparar contra o atual. */
    private var loadedSnapshot: RegimenEditUiState? = null

    init {
        viewModelScope.launch {
            medicationRepository.observeVisible().collect { medications ->
                val options = medications.map { MedicationOption(it.id.toString(), names.name(it), it.defaultRoute) }
                _state.update { it.copy(availableMedications = options) }
            }
        }
    }

    fun onEvent(event: RegimenEditUiEvent) {
        when (event) {
            is RegimenEditUiEvent.Load -> load(event.regimenId)
            is RegimenEditUiEvent.FieldChanged -> _state.update { withPreview(event.apply(it)).withUnsavedFlag() }
            RegimenEditUiEvent.StartCreatingMedication -> _state.update { it.copy(isCreatingMedication = true) }
            RegimenEditUiEvent.CancelCreatingMedication -> _state.update { it.copy(isCreatingMedication = false) }
            RegimenEditUiEvent.ConfirmNewMedication -> confirmNewMedication()
            RegimenEditUiEvent.Save -> save()
            RegimenEditUiEvent.RequestDelete -> _state.update { it.copy(showDeleteConfirm = true) }
            RegimenEditUiEvent.CancelDelete -> _state.update { it.copy(showDeleteConfirm = false) }
            RegimenEditUiEvent.ConfirmDelete -> delete()
            RegimenEditUiEvent.Back -> navigateBack()
        }
    }

    private fun load(id: String?) = viewModelScope.launch {
        val uuid = id?.let(Uuid::parse)
        regimenId = uuid
        val regimen = uuid?.let { regimenRepository.get(it) }
        when {
            id != null && regimen == null ->
                _state.update { it.copy(isLoading = false, error = DomainError.NotFound("regimen", id)) }
            regimen == null ->
                _state.update { withPreview(it.copy(isLoading = false, isNew = true, startDate = today)) }
            else -> {
                loadedSchedule = regimen.schedule
                _state.update { withPreview(populatedFrom(it, regimen)) }
            }
        }
        captureBaseline()
    }

    /** Seção 9: o formulário recém carregado (ou salvo) passa a ser a referência sem alteração pendente. */
    private fun captureBaseline() {
        loadedSnapshot = _state.value.formSnapshot()
        _state.update { it.copy(hasUnsavedChanges = false) }
    }

    /** Só os campos do formulário importam para detectar alteração pendente (Seção 9), não a lista de opções. */
    private fun RegimenEditUiState.formSnapshot(): RegimenEditUiState = copy(
        isLoading = false,
        availableMedications = emptyList(),
        error = null,
        showDeleteConfirm = false,
        nextDoses = emptyList(),
        hasUnsavedChanges = false,
    )

    private fun RegimenEditUiState.withUnsavedFlag(): RegimenEditUiState {
        val loaded = loadedSnapshot ?: return copy(hasUnsavedChanges = false)
        return copy(hasUnsavedChanges = formSnapshot() != loaded)
    }

    private fun populatedFrom(current: RegimenEditUiState, regimen: Regimen): RegimenEditUiState = current.copy(
        isLoading = false,
        isNew = false,
        selectedMedicationId = regimen.medicationId.toString(),
        doseValue = Formatters.number(regimen.dose.value, DOSE_FRACTION_DIGITS),
        doseUnit = regimen.dose.unit,
        route = regimen.route,
        timeOfDay = regimen.timeOfDay,
        startDate = regimen.startDate,
        endDate = regimen.endDate,
        isActive = regimen.isActive,
        notes = regimen.notes.orEmpty(),
    ).withSchedule(regimen.schedule)

    /**
     * Até [PREVIEW_COUNT] doses previstas a partir de hoje (ou do início, se for depois). Só com agenda
     * válida: um intervalo zero ou vazio não gera prévia, e o erro aparece ao salvar.
     */
    private fun withPreview(state: RegimenEditUiState): RegimenEditUiState {
        val schedule = state.toSchedule(loadedSchedule)
        val from = maxOf(today, state.startDate)
        val dates = if (DoseSchedule.validate(schedule).isSuccess) {
            val to = from.plus(PREVIEW_HORIZON_DAYS, DateTimeUnit.DAY)
            DoseSchedule.occurrences(schedule, state.startDate, state.endDate, from, to).take(PREVIEW_COUNT)
        } else {
            emptyList()
        }
        return state.copy(nextDoses = dates)
    }

    private fun confirmNewMedication() = viewModelScope.launch {
        val current = _state.value
        val concentration = Formatters.parseNumber(current.newMedicationConcentrationValue)
            ?.let { Concentration(it, current.newMedicationConcentrationUnit) }
        val result = medicationRepository.createCustom(
            name = current.newMedicationName,
            substance = current.newMedicationSubstance.ifBlank { null },
            defaultRoute = current.newMedicationRoute,
            concentration = concentration,
        )
        when (result) {
            is Result.Success -> _state.update {
                it.copy(
                    isCreatingMedication = false,
                    selectedMedicationId = result.value.id.toString(),
                    newMedicationName = "",
                    newMedicationSubstance = "",
                    newMedicationRoute = null,
                    newMedicationConcentrationValue = "",
                    error = null,
                ).withUnsavedFlag()
            }
            is Result.Failure -> _state.update { it.copy(error = result.error) }
        }
    }

    private fun save() = viewModelScope.launch {
        val current = _state.value
        val medicationId = current.selectedMedicationId?.let(Uuid::parse)
        if (medicationId == null) {
            _state.update { it.copy(error = DomainError.Invalid("medicationId", DomainError.Reason.REQUIRED)) }
            return@launch
        }
        val result = persist(medicationId, current)
        when (result) {
            is Result.Success -> {
                captureBaseline()
                effectsChannel.send(RegimenEditEffect.NavigateBack)
            }
            is Result.Failure -> _state.update { it.copy(error = result.error) }
        }
    }

    private suspend fun persist(medicationId: Uuid, current: RegimenEditUiState): Result<Regimen> {
        val dose = Dose(Formatters.parseNumber(current.doseValue) ?: Double.NaN, current.doseUnit)
        val route = current.route ?: Route.OTHER
        val schedule = current.toSchedule(loadedSchedule)
        val notes = current.notes.ifBlank { null }
        val existingId = regimenId
        return if (existingId == null) {
            val newRegimen = NewRegimen(
                medicationId = medicationId,
                dose = dose,
                route = route,
                schedule = schedule,
                timeOfDay = current.timeOfDay,
                startDate = current.startDate,
                endDate = current.endDate,
                notes = notes,
            )
            regimenRepository.create(newRegimen)
        } else {
            regimenRepository.update(
                Regimen(
                    id = existingId,
                    medicationId = medicationId,
                    dose = dose,
                    route = route,
                    schedule = schedule,
                    timeOfDay = current.timeOfDay,
                    startDate = current.startDate,
                    endDate = current.endDate,
                    isActive = current.isActive,
                    notes = notes,
                ),
            )
        }
    }

    private fun delete() = viewModelScope.launch {
        val id = regimenId ?: return@launch
        regimenRepository.delete(id)
        effectsChannel.send(RegimenEditEffect.NavigateBack)
    }

    private fun navigateBack() = viewModelScope.launch { effectsChannel.send(RegimenEditEffect.NavigateBack) }

    private companion object {
        const val DOSE_FRACTION_DIGITS = 4
        const val PREVIEW_COUNT = 4

        /** Cobre 4 doses mesmo com passos de 366 dias. */
        const val PREVIEW_HORIZON_DAYS = 1500
    }
}
