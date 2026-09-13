// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.medication

import androidx.compose.runtime.Immutable
import br.com.colman.changes.core.model.ConcentrationUnit
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.DoseUnit
import br.com.colman.changes.core.model.Route
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Opções de agenda da tela (ADR 0007): atalhos sobre os tipos do domínio. [CUSTOM] só aparece para uma
 * regra gravada que a tela não sabe editar, e é salva sem mudança.
 */
enum class ScheduleOption { DAILY, INTERVAL_DAYS, WEEKLY, MONTHLY, QUARTERLY, STEPPED, AS_NEEDED, CUSTOM }

/**
 * Estado da tela de criar/editar regime (Seção 7.1). Nenhum campo de dose nasce preenchido: quem
 * inicia [RegimenEditViewModel] decide o valor default de [startDate]; os demais campos começam
 * vazios ou com a opção padrão [ScheduleOption.INTERVAL_DAYS].
 */
@Immutable
data class RegimenEditUiState(
    val isLoading: Boolean = true,
    val isNew: Boolean = true,
    val availableMedications: List<MedicationOption> = emptyList(),
    val selectedMedicationId: String? = null,
    val isCreatingMedication: Boolean = false,
    val newMedicationName: String = "",
    val newMedicationSubstance: String = "",
    val newMedicationRoute: Route? = null,
    val newMedicationConcentrationValue: String = "",
    val newMedicationConcentrationUnit: ConcentrationUnit = ConcentrationUnit.MG_PER_ML,
    val doseValue: String = "",
    val doseUnit: DoseUnit = DoseUnit.MG,
    val route: Route? = null,
    val scheduleOption: ScheduleOption = ScheduleOption.INTERVAL_DAYS,
    val intervalDays: String = "",
    val weeklyDays: Set<DayOfWeek> = emptySet(),
    val everyWeeks: String = "1",
    val everyMonths: String = "1",
    /** Dias de cada dose de "intervalos variáveis": o primeiro desde o início, os demais desde a anterior. */
    val stepDays: List<String> = listOf(""),
    /** Depois do último passo, repete a cada [continuousEveryDays] dias. */
    val continuous: Boolean = true,
    val continuousEveryDays: String = "",
    val timeOfDay: LocalTime? = null,
    val startDate: LocalDate,
    val endDate: LocalDate? = null,
    val isActive: Boolean = true,
    val notes: String = "",
    val error: DomainError? = null,
    val showDeleteConfirm: Boolean = false,
    /** Próximas doses previstas pela agenda do formulário (até 4), para conferir antes de salvar. */
    val nextDoses: List<LocalDate> = emptyList(),
)

sealed interface RegimenEditUiEvent {
    /** `regimenId` nulo cria um regime novo; carregado uma vez pelo `Route` (Seção 5). */
    data class Load(val regimenId: String?) : RegimenEditUiEvent

    /** Qualquer campo simples de formulário (texto, seleção, data, hora). */
    data class FieldChanged(val apply: (RegimenEditUiState) -> RegimenEditUiState) : RegimenEditUiEvent
    data object StartCreatingMedication : RegimenEditUiEvent
    data object CancelCreatingMedication : RegimenEditUiEvent
    data object ConfirmNewMedication : RegimenEditUiEvent
    data object Save : RegimenEditUiEvent
    data object RequestDelete : RegimenEditUiEvent
    data object ConfirmDelete : RegimenEditUiEvent
    data object CancelDelete : RegimenEditUiEvent
    data object Back : RegimenEditUiEvent
}

sealed interface RegimenEditEffect {
    data object NavigateBack : RegimenEditEffect
}
