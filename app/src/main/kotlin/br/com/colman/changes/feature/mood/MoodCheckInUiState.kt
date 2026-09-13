// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.mood

import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDate

private val EPOCH = LocalDate(year = 1970, monthNumber = 1, dayOfMonth = 1)

/**
 * Estado do check-in diário (Seção 7.7). Humor e energia são obrigatórios; ansiedade, disforia, sono,
 * nota e etiquetas são opcionais e podem ficar em branco. Nada aqui deriva do texto da nota (7.7.2):
 * a nota só é guardada e devolvida, nunca lida por heurística.
 */
@Immutable
data class MoodCheckInUiState(
    val isLoading: Boolean = true,
    val date: LocalDate = EPOCH,
    val mood: Int? = null,
    val energy: Int? = null,
    val anxiety: Int? = null,
    val dysphoria: Int? = null,
    val sleepHoursText: String = "",
    val note: String = "",
    val tagsText: String = "",
    val moodError: Boolean = false,
    val energyError: Boolean = false,
    val sleepHoursError: Boolean = false,
)

/** Ações do check-in. [Load] é disparado uma vez pela `Route` (Seção 5) com o dia da rota. */
sealed interface MoodCheckInUiEvent {
    data class Load(val epochDay: Long?) : MoodCheckInUiEvent

    data class MoodChanged(val value: Int?) : MoodCheckInUiEvent

    data class EnergyChanged(val value: Int?) : MoodCheckInUiEvent

    data class AnxietyChanged(val value: Int?) : MoodCheckInUiEvent

    data class DysphoriaChanged(val value: Int?) : MoodCheckInUiEvent

    data class SleepHoursChanged(val text: String) : MoodCheckInUiEvent

    data class NoteChanged(val text: String) : MoodCheckInUiEvent

    data class TagsChanged(val text: String) : MoodCheckInUiEvent

    data object Save : MoodCheckInUiEvent
}

/** Efeito de uma vez (Seção 5): mostra o snackbar `mood_saved` depois de gravar com sucesso. */
sealed interface MoodCheckInEffect {
    data object Saved : MoodCheckInEffect
}
