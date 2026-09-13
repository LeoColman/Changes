// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.calendar

import kotlinx.datetime.LocalDate

/** Ações da tela Calendário que mudam estado (navegação pura fica em [CalendarNavigation]). */
sealed interface CalendarUiEvent {
    data class ChangeViewMode(val mode: CalendarViewMode) : CalendarUiEvent

    data object PreviousMonth : CalendarUiEvent

    data object NextMonth : CalendarUiEvent

    data class SelectDate(val date: LocalDate) : CalendarUiEvent

    /** Liga/desliga a camada de marcos da literatura (grava em `SettingsStore`, Seção 7.9). */
    data class ToggleMilestones(val enabled: Boolean) : CalendarUiEvent
}
