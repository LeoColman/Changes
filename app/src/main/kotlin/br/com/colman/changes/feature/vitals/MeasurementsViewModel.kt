// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.vitals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.changes.core.clinical.BodyVocabularyResolver
import br.com.colman.changes.core.clinical.ClinicalLabels
import br.com.colman.changes.core.data.MeasurementRepository
import br.com.colman.changes.core.data.ProfileRepository
import br.com.colman.changes.core.model.Measurement
import br.com.colman.changes.core.model.MeasurementType
import br.com.colman.changes.core.model.MeasurementUnit
import br.com.colman.changes.core.model.Profile
import br.com.colman.changes.core.model.RecordedTime
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.model.TimeZoneProvider
import br.com.colman.changes.core.model.UnitSystem
import br.com.colman.changes.core.model.Units
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.ui.chart.ChartPoint
import br.com.colman.changes.ui.format.Formatters
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import java.util.Locale
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

private const val STOP_TIMEOUT_MILLIS = 5_000L

/** Tipos fixos, na ordem de exibição da tela (Seção 7.4). `CUSTOM` vira uma seção por rótulo digitado. */
private val FIXED_MEASUREMENT_TYPES: List<MeasurementType> = listOf(
    MeasurementType.WEIGHT,
    MeasurementType.WAIST,
    MeasurementType.HIP,
    MeasurementType.CHEST,
    MeasurementType.BICEP,
    MeasurementType.NECK,
    MeasurementType.BODY_FAT_PCT,
)

/** Unidade de exibição de um tipo fixo, conforme o sistema de unidades do perfil. `CUSTOM` guarda a própria unidade. */
private fun displayUnit(type: MeasurementType, unitSystem: UnitSystem): MeasurementUnit = when (type) {
    MeasurementType.WEIGHT -> if (unitSystem == UnitSystem.IMPERIAL) MeasurementUnit.LB else MeasurementUnit.KG
    MeasurementType.WAIST, MeasurementType.HIP, MeasurementType.CHEST, MeasurementType.BICEP, MeasurementType.NECK ->
        if (unitSystem == UnitSystem.IMPERIAL) MeasurementUnit.IN else MeasurementUnit.CM
    MeasurementType.BODY_FAT_PCT, MeasurementType.CUSTOM -> MeasurementUnit.PERCENT
}

/**
 * Tela de Medidas (Seção 7.4): peso com gráfico e IMC, e as demais medidas do corpo, todas com
 * adicionar/editar/excluir com desfazer. IMC é sempre calculado pelo repositório, nunca aqui.
 */
class MeasurementsViewModel(
    private val measurements: MeasurementRepository,
    private val profiles: ProfileRepository,
    private val clinicalLabels: ClinicalLabels,
    private val clock: Clock,
    private val timeZones: TimeZoneProvider,
) : ViewModel() {

    private val form = MutableStateFlow<MeasurementFormUiState?>(null)
    private val effectsChannel = Channel<MeasurementsEffect>(Channel.BUFFERED)
    private var lastDeletedId: Uuid? = null

    val effects: Flow<MeasurementsEffect> = effectsChannel.receiveAsFlow()

    val state: StateFlow<MeasurementsUiState> = combine(
        profiles.observe(),
        measurements.observeAll(),
        measurements.observeBmi(),
        form,
    ) { profile, all, bmi, formState ->
        MeasurementsUiState(
            isLoading = false,
            unitSystem = profile.unitSystem,
            bmi = bmiState(profile, bmi),
            sections = buildSections(all, profile),
            form = formState,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), MeasurementsUiState())

    fun onEvent(event: MeasurementsUiEvent) {
        when (event) {
            is MeasurementsUiEvent.AddRequested -> beginAdd(event.type, event.customLabel)
            is MeasurementsUiEvent.EditRequested -> beginEdit(event.entryId)
            is MeasurementsUiEvent.DeleteRequested -> delete(event.entryId)
            MeasurementsUiEvent.UndoDeleteRequested -> undoDelete()
            MeasurementsUiEvent.FormSaved -> save()
            MeasurementsUiEvent.FormDismissed -> form.value = null
            else -> updateForm { applyFormEvent(it, event) }
        }
    }

    private fun applyFormEvent(current: MeasurementFormUiState, event: MeasurementsUiEvent): MeasurementFormUiState =
        when (event) {
            is MeasurementsUiEvent.FormTypeChanged -> current.copy(type = event.type)
            is MeasurementsUiEvent.FormCustomLabelChanged -> current.copy(customLabel = event.text)
            is MeasurementsUiEvent.FormUnitChanged -> current.copy(unit = event.unit)
            is MeasurementsUiEvent.FormValueChanged -> current.copy(valueText = event.text, valueError = false)
            is MeasurementsUiEvent.FormDateChanged -> current.copy(date = event.date)
            is MeasurementsUiEvent.FormNotesChanged -> current.copy(notes = event.text)
            else -> current
        }

    private fun bmiState(profile: Profile, bmi: Double?): BmiUiState? = when {
        !profile.showBmi -> null
        profile.heightCm == null -> BmiUiState.NeedsHeight
        bmi != null -> BmiUiState.Value(bmi)
        else -> BmiUiState.NeedsWeight
    }

    private fun buildSections(all: List<Measurement>, profile: Profile): List<MeasurementSectionUiState> {
        val fixedEntries = all.filter { it.type != MeasurementType.CUSTOM }.groupBy { it.type }
        val fixedSections = FIXED_MEASUREMENT_TYPES.map { type ->
            section(
                type,
                null,
                fixedEntries[type].orEmpty(),
                profile
            )
        }
        val customSections = all.filter { it.type == MeasurementType.CUSTOM }
            .groupBy { it.customLabel.orEmpty() }
            .toSortedMap()
            .map { (label, rows) -> section(MeasurementType.CUSTOM, label, rows, profile) }
        return fixedSections + customSections
    }

    private fun section(
        type: MeasurementType,
        customLabel: String?,
        rows: List<Measurement>,
        profile: Profile,
    ): MeasurementSectionUiState {
        val entries = rows.sortedBy { it.measuredAt.instant }.map { row -> entryFor(type, row, profile) }
        return MeasurementSectionUiState(
            type = type,
            customLabel = customLabel,
            label = labelFor(type, customLabel, profile),
            entries = entries,
            chartPoints = entries.map { ChartPoint(it.date.toEpochDays().toDouble(), it.value) },
        )
    }

    private fun entryFor(type: MeasurementType, row: Measurement, profile: Profile): MeasurementEntryUiState {
        val unit = if (type == MeasurementType.CUSTOM) row.unit else displayUnit(type, profile.unitSystem)
        val value = if (type == MeasurementType.CUSTOM) {
            row.value
        } else {
            Units.convert(row.value, row.unit, unit).getOrNull() ?: row.value
        }
        return MeasurementEntryUiState(row.id, value, unit, row.measuredAt.localDate, row.notes)
    }

    private fun labelFor(type: MeasurementType, customLabel: String?, profile: Profile): String? = when (type) {
        MeasurementType.CHEST -> chestLabel(profile)
        MeasurementType.CUSTOM -> customLabel
        else -> null
    }

    private fun chestLabel(profile: Profile): String {
        val locale = profile.locale ?: Locale.getDefault().toLanguageTag()
        return BodyVocabularyResolver(clinicalLabels.forLocale(locale)).chestMeasurementLabel(profile.bodyVocabulary)
    }

    private fun beginAdd(type: MeasurementType, customLabel: String?) {
        val unitSystem = state.value.unitSystem
        form.value = MeasurementFormUiState(
            editingId = null,
            type = type,
            customLabel = customLabel.orEmpty(),
            unit = if (type == MeasurementType.CUSTOM) MeasurementUnit.CM else displayUnit(type, unitSystem),
            valueText = "",
            date = today(),
            notes = "",
        )
    }

    private fun beginEdit(entryId: Uuid) {
        val section = state.value.sections.firstOrNull { it.entries.any { entry -> entry.id == entryId } } ?: return
        val entry = section.entries.first { it.id == entryId }
        form.value = MeasurementFormUiState(
            editingId = entry.id,
            type = section.type,
            customLabel = section.customLabel.orEmpty(),
            unit = entry.unit,
            valueText = entry.value.toString(),
            date = entry.date,
            notes = entry.notes.orEmpty(),
        )
    }

    private fun updateForm(transform: (MeasurementFormUiState) -> MeasurementFormUiState) {
        form.value = form.value?.let(transform)
    }

    private fun save() {
        val current = form.value ?: return
        val value = Formatters.parseNumber(current.valueText)
        if (value == null) {
            form.value = current.copy(valueError = true)
            return
        }
        viewModelScope.launch {
            val profile = profiles.observe().first()
            val isCustom = current.type == MeasurementType.CUSTOM
            val unit = if (isCustom) current.unit else displayUnit(current.type, profile.unitSystem)
            val customLabel = current.customLabel.trim().takeIf { isCustom && it.isNotBlank() }
            val result = persist(current, value, unit, customLabel)
            form.value = if (result is Result.Success) null else current.copy(valueError = true)
        }
    }

    private suspend fun persist(
        draft: MeasurementFormUiState,
        value: Double,
        unit: MeasurementUnit,
        customLabel: String?,
    ): Result<Measurement> {
        val notes = draft.notes.trim().ifBlank { null }
        val measuredAt = instantFor(draft.date)
        return if (draft.editingId == null) {
            measurements.create(draft.type, customLabel, value, unit, measuredAt, notes)
        } else {
            measurements.update(
                Measurement(
                    id = draft.editingId,
                    type = draft.type,
                    customLabel = customLabel,
                    value = value,
                    unit = unit,
                    measuredAt = RecordedTime.of(measuredAt, timeZones.current()),
                    notes = notes,
                ),
            )
        }
    }

    private fun delete(entryId: Uuid) = viewModelScope.launch {
        measurements.delete(entryId)
        lastDeletedId = entryId
        effectsChannel.send(MeasurementsEffect.ShowUndoDelete)
    }

    private fun undoDelete() = viewModelScope.launch {
        lastDeletedId?.let { measurements.restore(it) }
        lastDeletedId = null
    }

    private fun today(): LocalDate = clock.now().toLocalDateTime(timeZones.current()).date

    /** Combina a data escolhida com a hora atual, evitando que "hoje" vire um instante futuro por fuso. */
    private fun instantFor(date: LocalDate): Instant {
        val currentTime = clock.now().toLocalDateTime(timeZones.current()).time
        return LocalDateTime(date, currentTime).toInstant(timeZones.current())
    }
}
