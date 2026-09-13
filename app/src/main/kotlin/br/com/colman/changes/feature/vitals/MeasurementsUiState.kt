// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.vitals

import androidx.compose.runtime.Immutable
import br.com.colman.changes.core.model.MeasurementType
import br.com.colman.changes.core.model.MeasurementUnit
import br.com.colman.changes.core.model.UnitSystem
import br.com.colman.changes.ui.chart.ChartPoint
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

/** Estado da tela de Medidas (Seção 7.4): peso, IMC e outras medidas do corpo. */
@Immutable
data class MeasurementsUiState(
    val isLoading: Boolean = true,
    val unitSystem: UnitSystem = UnitSystem.METRIC,
    val bmi: BmiUiState? = null,
    val sections: List<MeasurementSectionUiState> = emptyList(),
    val form: MeasurementFormUiState? = null,
)

/** `null` (fora deste tipo) esconde o IMC por completo: `show_bmi = false` (critério 7.4.1). */
@Immutable
sealed interface BmiUiState {
    /** Sem altura no perfil: pede a altura, não estima nada (critério 7.4.2). */
    data object NeedsHeight : BmiUiState

    /** Há altura, mas nenhum peso registrado: pede o peso, não estima nada (critério 7.4.2). */
    data object NeedsWeight : BmiUiState

    /** Número puro, sem classificação nem faixa (critério 7.4.3). */
    @Immutable
    data class Value(val value: Double) : BmiUiState
}

/**
 * Uma seção de medida: um tipo fixo (peso, cintura, quadril, tórax, braço, pescoço, % de gordura)
 * ou um grupo de medidas personalizadas com o mesmo [customLabel].
 */
@Immutable
data class MeasurementSectionUiState(
    val type: MeasurementType,
    val customLabel: String?,
    /**
     * Rótulo resolvido fora de `strings.xml`: o tipo de tórax vem do `BodyVocabularyResolver`, o
     * personalizado do [customLabel]. Nos demais tipos é `null` (a tela resolve pelo [type]).
     */
    val label: String?,
    /** Ordenados por data, mais antigo primeiro. */
    val entries: List<MeasurementEntryUiState>,
    val chartPoints: List<ChartPoint>,
) {
    val current: MeasurementEntryUiState? get() = entries.lastOrNull()
}

@Immutable
data class MeasurementEntryUiState(
    val id: Uuid,
    val value: Double,
    val unit: MeasurementUnit,
    val date: LocalDate,
    val notes: String?,
)

/** Formulário de adicionar/editar uma medida. `editingId == null` é uma nova medida. */
@Immutable
data class MeasurementFormUiState(
    val editingId: Uuid?,
    val type: MeasurementType,
    val customLabel: String,
    val unit: MeasurementUnit,
    val valueText: String,
    val date: LocalDate,
    val notes: String,
    val valueError: Boolean = false,
)

sealed interface MeasurementsUiEvent {
    data class AddRequested(val type: MeasurementType, val customLabel: String? = null) : MeasurementsUiEvent

    data class EditRequested(val entryId: Uuid) : MeasurementsUiEvent

    data class FormTypeChanged(val type: MeasurementType) : MeasurementsUiEvent

    data class FormCustomLabelChanged(val text: String) : MeasurementsUiEvent

    data class FormUnitChanged(val unit: MeasurementUnit) : MeasurementsUiEvent

    data class FormValueChanged(val text: String) : MeasurementsUiEvent

    data class FormDateChanged(val date: LocalDate) : MeasurementsUiEvent

    data class FormNotesChanged(val text: String) : MeasurementsUiEvent

    data object FormSaved : MeasurementsUiEvent

    data object FormDismissed : MeasurementsUiEvent

    data class DeleteRequested(val entryId: Uuid) : MeasurementsUiEvent

    data object UndoDeleteRequested : MeasurementsUiEvent
}

/** Efeito de uma vez (Seção 5): snackbar de desfazer após excluir. */
sealed interface MeasurementsEffect {
    data object ShowUndoDelete : MeasurementsEffect
}
