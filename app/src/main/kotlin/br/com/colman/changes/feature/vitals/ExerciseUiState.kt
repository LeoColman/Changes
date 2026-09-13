// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.vitals

import androidx.compose.runtime.Immutable
import br.com.colman.changes.core.model.ExerciseIntensity
import br.com.colman.changes.ui.chart.ChartPoint
import kotlinx.datetime.LocalDate
import kotlin.uuid.Uuid

/** Estado da tela de Exercício (Seção 7.4): sessões e resumo semanal em minutos, sem meta. */
@Immutable
data class ExerciseUiState(
    val isLoading: Boolean = true,
    val sessions: List<ExerciseSessionUiState> = emptyList(),
    val weeklyMinutes: List<WeekMinutesUiState> = emptyList(),
    val weeklyChartPoints: List<ChartPoint> = emptyList(),
    val form: ExerciseFormUiState? = null,
)

/**
 * As duas fontes de estado da tela de Exercício (ADR 0011: sessões e rotinas vêm de ViewModels
 * diferentes), juntas num só parâmetro para não estourar o limite de parâmetros de [ExerciseScreen].
 */
@Immutable
data class ExerciseScreenState(val exercise: ExerciseUiState, val routines: RoutinesUiState)

@Immutable
data class ExerciseSessionUiState(
    val id: Uuid,
    val activity: String,
    val durationMinutes: Int,
    val intensity: ExerciseIntensity,
    val date: LocalDate,
    val notes: String?,
)

/** Um fato: minutos de exercício numa semana ISO. Semana sem sessão entra com 0, nunca fica de fora. */
@Immutable
data class WeekMinutesUiState(val weekStart: LocalDate, val minutes: Int)

/** Formulário de adicionar/editar uma sessão. `editingId == null` é uma nova sessão. */
@Immutable
data class ExerciseFormUiState(
    val editingId: Uuid?,
    val activity: String,
    val durationText: String,
    val intensity: ExerciseIntensity,
    val date: LocalDate,
    val notes: String,
    val activityError: Boolean = false,
    val durationError: Boolean = false,
    val dateError: Boolean = false,
)

sealed interface ExerciseUiEvent {
    data object AddRequested : ExerciseUiEvent

    data class EditRequested(val sessionId: Uuid) : ExerciseUiEvent

    data class FormActivityChanged(val text: String) : ExerciseUiEvent

    data class FormDurationChanged(val text: String) : ExerciseUiEvent

    data class FormIntensityChanged(val intensity: ExerciseIntensity) : ExerciseUiEvent

    data class FormDateChanged(val date: LocalDate) : ExerciseUiEvent

    data class FormNotesChanged(val text: String) : ExerciseUiEvent

    data object FormSaved : ExerciseUiEvent

    data object FormDismissed : ExerciseUiEvent

    data class DeleteRequested(val sessionId: Uuid) : ExerciseUiEvent

    data object UndoDeleteRequested : ExerciseUiEvent
}

/** Efeito de uma vez (Seção 5): snackbar de desfazer após excluir. */
sealed interface ExerciseEffect {
    data object ShowUndoDelete : ExerciseEffect
}
