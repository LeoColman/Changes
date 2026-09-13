// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.changes.core.clinical.ClinicalLabels
import br.com.colman.changes.core.clinical.LocaleLabels
import br.com.colman.changes.core.data.LabRepository
import br.com.colman.changes.core.data.NewLabResult
import br.com.colman.changes.core.data.ProfileRepository
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.LabAnalyte
import br.com.colman.changes.core.model.LabResult
import br.com.colman.changes.core.model.Profile
import br.com.colman.changes.core.model.RecordedTime
import br.com.colman.changes.core.model.ReferenceRange
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.model.TimeZoneProvider
import br.com.colman.changes.ui.chart.ChartBand
import br.com.colman.changes.ui.chart.ChartPoint
import br.com.colman.changes.ui.format.Formatters
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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

/** Retrato dos dados persistidos, para combinar com o estado de UI num único `combine`. */
private data class LabDataSnapshot(val profile: Profile, val analytes: List<LabAnalyte>, val results: List<LabResult>)

/** Retrato do que só existe na tela (seleção e formulários abertos). */
private data class LabFormsSnapshot(
    val selectedAnalyteId: Uuid?,
    val newAnalyteForm: NewAnalyteFormUiState?,
    val resultForm: LabResultFormUiState?,
)

/**
 * Tela de Exames laboratoriais (Seção 7.6). Nenhum limiar clínico é embutido aqui: a marcação de
 * fora da faixa usa só a faixa que a própria pessoa digitou ([ReferenceRange.isOutside]).
 */
class LabResultsViewModel(
    private val labs: LabRepository,
    private val profiles: ProfileRepository,
    private val clinicalLabels: ClinicalLabels,
    private val clock: Clock,
    private val timeZones: TimeZoneProvider,
) : ViewModel() {

    private val selectedAnalyteId = MutableStateFlow<Uuid?>(null)
    private val newAnalyteForm = MutableStateFlow<NewAnalyteFormUiState?>(null)
    private val resultForm = MutableStateFlow<LabResultFormUiState?>(null)
    private val effectsChannel = Channel<LabResultsEffect>(Channel.BUFFERED)
    private var lastDeletedResultId: Uuid? = null

    val effects: Flow<LabResultsEffect> = effectsChannel.receiveAsFlow()

    private val dataSnapshot =
        combine(profiles.observe(), labs.observeVisibleAnalytes(), labs.observeAllResults(), ::LabDataSnapshot)
    private val formsSnapshot = combine(selectedAnalyteId, newAnalyteForm, resultForm, ::LabFormsSnapshot)

    val state: StateFlow<LabResultsUiState> = combine(dataSnapshot, formsSnapshot, ::buildState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), LabResultsUiState())

    fun onEvent(event: LabResultsUiEvent) {
        when (event) {
            is LabResultsUiEvent.Load -> selectedAnalyteId.value = event.analyteId
            is LabResultsUiEvent.AnalyteOpened -> selectedAnalyteId.value = event.analyteId
            LabResultsUiEvent.BackToListRequested -> backToList()
            LabResultsUiEvent.NewAnalyteRequested,
            LabResultsUiEvent.NewAnalyteSaved,
            LabResultsUiEvent.NewAnalyteDismissed,
            is LabResultsUiEvent.NewAnalyteChanged,
            -> onNewAnalyteEvent(event)
            else -> onResultEvent(event)
        }
    }

    private fun onNewAnalyteEvent(event: LabResultsUiEvent) {
        when (event) {
            LabResultsUiEvent.NewAnalyteRequested -> newAnalyteForm.value = NewAnalyteFormUiState()
            is LabResultsUiEvent.NewAnalyteChanged -> newAnalyteForm.value = newAnalyteForm.value?.let(event.apply)
            LabResultsUiEvent.NewAnalyteSaved -> saveNewAnalyte()
            LabResultsUiEvent.NewAnalyteDismissed -> newAnalyteForm.value = null
            else -> Unit
        }
    }

    private fun onResultEvent(event: LabResultsUiEvent) {
        when (event) {
            LabResultsUiEvent.AddResultRequested -> beginAddResult()
            is LabResultsUiEvent.EditResultRequested -> beginEditResult(event.resultId)
            is LabResultsUiEvent.ResultFormChanged -> resultForm.value = resultForm.value?.let(event.apply)
            LabResultsUiEvent.ResultFormSaved -> saveResult()
            LabResultsUiEvent.ResultFormDismissed -> resultForm.value = null
            is LabResultsUiEvent.DeleteResultRequested -> deleteResult(event.resultId)
            LabResultsUiEvent.UndoDeleteRequested -> undoDeleteResult()
            else -> Unit
        }
    }

    private fun backToList() {
        selectedAnalyteId.value = null
        resultForm.value = null
    }

    private fun buildState(data: LabDataSnapshot, forms: LabFormsSnapshot): LabResultsUiState {
        val locale = data.profile.locale ?: Locale.getDefault().toLanguageTag()
        val labels = clinicalLabels.forLocale(locale)
        val resultsByAnalyte = data.results.groupBy { it.analyteId }
        val listItems = data.analytes.map { analyte ->
            val rows = resultsByAnalyte[analyte.id].orEmpty()
            LabAnalyteListItemUiState(
                id = analyte.id,
                label = analyteLabel(analyte, labels),
                latest = rows.maxByOrNull { it.collectedAt.instant }?.toRowUiState(),
            )
        }
        val detail = forms.selectedAnalyteId
            ?.let { id -> data.analytes.firstOrNull { it.id == id } }
            ?.let { analyte -> buildDetail(analyte, labels, resultsByAnalyte[analyte.id].orEmpty()) }
        return LabResultsUiState(
            isLoading = false,
            analytes = listItems,
            detail = detail,
            newAnalyteForm = forms.newAnalyteForm,
            resultForm = forms.resultForm,
        )
    }

    private fun analyteLabel(analyte: LabAnalyte, labels: LocaleLabels): String = if (analyte.isBuiltin) {
        labels.analyteLabel(analyte.labelKey ?: analyte.code) ?: analyte.code
    } else {
        analyte.customLabel ?: analyte.code
    }

    private fun buildDetail(
        analyte: LabAnalyte,
        labels: LocaleLabels,
        results: List<LabResult>,
    ): LabAnalyteDetailUiState {
        val ascending = results.sortedBy { it.collectedAt.instant }
        val band = ascending.filter { it.referenceRange != null }
            .maxByOrNull { it.collectedAt.instant }
            ?.referenceRange
            ?.let { ChartBand(it.low, it.high) }
        return LabAnalyteDetailUiState(
            analyteId = analyte.id,
            label = analyteLabel(analyte, labels),
            defaultUnit = analyte.defaultUnit,
            chartPoints = ascending.map { ChartPoint(it.collectedAt.localDate.toEpochDays().toDouble(), it.value) },
            chartBand = band,
            results = ascending.map { it.toRowUiState() },
        )
    }

    private fun LabResult.toRowUiState(): LabResultRowUiState = LabResultRowUiState(
        id = id,
        value = value,
        unit = unit,
        date = collectedAt.localDate,
        referenceLow = referenceRange?.low,
        referenceHigh = referenceRange?.high,
        isOutOfRange = referenceRange?.isOutside(value) == true,
        labName = labName,
        notes = notes,
    )

    private fun saveNewAnalyte() = viewModelScope.launch {
        val current = newAnalyteForm.value ?: return@launch
        val result = labs.createCustomAnalyte(current.label.trim(), current.defaultUnit.trim())
        when (result) {
            is Result.Success -> {
                newAnalyteForm.value = null
                selectedAnalyteId.value = result.value.id
            }
            is Result.Failure -> newAnalyteForm.value = current.copy(error = result.error)
        }
    }

    private fun beginAddResult() {
        val analyteId = selectedAnalyteId.value ?: return
        val detail = state.value.detail
        resultForm.value = LabResultFormUiState(
            editingId = null,
            analyteId = analyteId,
            unit = detail?.defaultUnit.orEmpty(),
            date = today(),
        )
    }

    private fun beginEditResult(resultId: Uuid) {
        val detail = state.value.detail ?: return
        val row = detail.results.firstOrNull { it.id == resultId } ?: return
        resultForm.value = LabResultFormUiState(
            editingId = row.id,
            analyteId = detail.analyteId,
            valueText = Formatters.number(row.value),
            unit = row.unit,
            date = row.date,
            referenceLowText = row.referenceLow?.let { Formatters.number(it) }.orEmpty(),
            referenceHighText = row.referenceHigh?.let { Formatters.number(it) }.orEmpty(),
            labName = row.labName.orEmpty(),
            notes = row.notes.orEmpty(),
        )
    }

    private fun saveResult() = viewModelScope.launch {
        val current = resultForm.value ?: return@launch
        val value = Formatters.parseNumber(current.valueText)
        val unit = current.unit.trim()
        if (value == null || unit.isBlank()) {
            val reason = if (value == null) DomainError.Reason.NOT_FINITE else DomainError.Reason.REQUIRED
            resultForm.value = current.copy(error = DomainError.Invalid("value", reason))
            return@launch
        }
        val range = ReferenceRange.ofNullable(
            current.referenceLowText.trim().ifEmpty { null }?.let { Formatters.parseNumber(it) },
            current.referenceHighText.trim().ifEmpty { null }?.let { Formatters.parseNumber(it) },
        )
        val draft = NewLabResult(
            analyteId = current.analyteId,
            value = value,
            unit = unit,
            collectedAt = instantFor(current.date),
            referenceRange = range,
            labName = current.labName.trim().ifBlank { null },
            notes = current.notes.trim().ifBlank { null },
        )
        val result = persistResult(current.editingId, draft)
        resultForm.value = when (result) {
            is Result.Success -> null
            is Result.Failure -> current.copy(error = result.error)
        }
    }

    private suspend fun persistResult(editingId: Uuid?, draft: NewLabResult): Result<LabResult> =
        if (editingId == null) {
            labs.createResult(draft)
        } else {
            labs.updateResult(
                LabResult(
                    id = editingId,
                    analyteId = draft.analyteId,
                    value = draft.value,
                    unit = draft.unit,
                    collectedAt = RecordedTime.of(draft.collectedAt, timeZones.current()),
                    referenceRange = draft.referenceRange,
                    labName = draft.labName,
                    notes = draft.notes,
                ),
            )
        }

    private fun deleteResult(resultId: Uuid) = viewModelScope.launch {
        labs.deleteResult(resultId)
        lastDeletedResultId = resultId
        effectsChannel.send(LabResultsEffect.ShowUndoDelete)
    }

    private fun undoDeleteResult() = viewModelScope.launch {
        lastDeletedResultId?.let { labs.restoreResult(it) }
        lastDeletedResultId = null
    }

    private fun today(): LocalDate = clock.now().toLocalDateTime(timeZones.current()).date

    /** Combina a data escolhida com a hora atual, evitando que "hoje" vire um instante futuro por fuso. */
    private fun instantFor(date: LocalDate): Instant {
        val currentTime = clock.now().toLocalDateTime(timeZones.current()).time
        return LocalDateTime(date, currentTime).toInstant(timeZones.current())
    }
}
