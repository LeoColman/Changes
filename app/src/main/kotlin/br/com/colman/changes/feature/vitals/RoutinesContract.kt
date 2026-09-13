// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.vitals

import androidx.compose.runtime.Immutable
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.uuid.Uuid

/** Frequência de uma rotina de exercício (ADR 0011): diária ou semanal com dias escolhidos. */
enum class RoutineFrequency { DAILY, WEEKLY }

/** Opções de lembrete do formulário de rotina, só disponíveis quando há hora marcada (ADR 0011). */
enum class RoutineReminderOption(val minutesBefore: Int?) {
    NONE(null),
    AT_TIME(0),
    MINUTES_15(15),
    HOUR_1(60),
}

/** Estado da seção de Rotinas (ADR 0011), acima das sessões na tela de Exercício. */
@Immutable
data class RoutinesUiState(
    val isLoading: Boolean = true,
    val routines: List<RoutineUiState> = emptyList(),
    val form: RoutineFormUiState? = null,
)

/** Uma rotina, já traduzida do `CalendarEvent` de categoria `EXERCISE` para o que a tela mostra. */
@Immutable
data class RoutineUiState(
    val id: Uuid,
    val activity: String,
    val frequency: RoutineFrequency,
    val interval: Int,
    val weeklyDays: Set<DayOfWeek>,
    val time: LocalTime?,
)

/** Formulário de adicionar/editar uma rotina. `editingId == null` é uma rotina nova. */
@Immutable
data class RoutineFormUiState(
    val editingId: Uuid?,
    val activity: String,
    val frequency: RoutineFrequency,
    val weeklyDays: Set<DayOfWeek>,
    val intervalText: String,
    val startDate: LocalDate,
    val time: LocalTime?,
    val reminder: RoutineReminderOption,
    val activityError: Boolean = false,
    val weeklyDaysError: Boolean = false,
    val intervalError: Boolean = false,
)

sealed interface RoutinesUiEvent {
    data object AddRequested : RoutinesUiEvent

    data class EditRequested(val routineId: Uuid) : RoutinesUiEvent

    data class DeleteRequested(val routineId: Uuid) : RoutinesUiEvent

    data object UndoDeleteRequested : RoutinesUiEvent

    data class FormActivityChanged(val text: String) : RoutinesUiEvent

    data class FormFrequencyChanged(val frequency: RoutineFrequency) : RoutinesUiEvent

    data class FormWeeklyDayToggled(val day: DayOfWeek) : RoutinesUiEvent

    data class FormIntervalChanged(val text: String) : RoutinesUiEvent

    data class FormStartDateChanged(val date: LocalDate) : RoutinesUiEvent

    data class FormTimeChanged(val time: LocalTime?) : RoutinesUiEvent

    data class FormReminderChanged(val reminder: RoutineReminderOption) : RoutinesUiEvent

    data object FormSaved : RoutinesUiEvent

    data object FormDismissed : RoutinesUiEvent
}

/** Efeito de uma vez (Seção 5): snackbar de desfazer após excluir uma rotina. */
sealed interface RoutinesEffect {
    data object ShowUndoDelete : RoutinesEffect
}
