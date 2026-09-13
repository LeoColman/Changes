// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.today

import kotlin.uuid.Uuid

/**
 * Callbacks de navegação da tela Hoje, agrupados num único tipo para não violar o limite de
 * parâmetros do Detekt (`LongParameterList`, dispara a partir de 7). O grafo (`nav/`) os liga.
 */
data class TodayNavigation(
    val onLogDoseWithDetails: (regimenId: Uuid) -> Unit,
    val onEditDose: (doseLogId: Uuid) -> Unit,
    val onCreateRegimen: () -> Unit,
    val onOpenMoodCheckIn: () -> Unit,
    val onOpenEvent: (eventId: Uuid) -> Unit,
    val onOpenCalendar: () -> Unit,
    val onOpenRegimens: () -> Unit,
    val onOpenMoodHistory: () -> Unit,
)
