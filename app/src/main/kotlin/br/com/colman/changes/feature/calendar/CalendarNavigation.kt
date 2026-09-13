// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.calendar

import kotlin.uuid.Uuid

/**
 * Callbacks de navegação da tela Calendário, agrupados num único tipo para não violar o limite de
 * parâmetros do Detekt (`LongParameterList`, dispara a partir de 7). O grafo (`nav/`) os liga.
 */
data class CalendarNavigation(
    /** Toque numa dose prevista: abre o registro já pré-preenchido (Seção 7.9). */
    val onLogDose: (regimenId: Uuid, epochDay: Long) -> Unit,
    /** Toque numa dose já registrada: abre a edição desse registro. */
    val onEditDose: (doseLogId: Uuid) -> Unit,
    /** Toque num evento manual: abre a edição desse evento. */
    val onEditEvent: (eventId: Uuid) -> Unit,
    /** FAB ou toque num marco vazio do dia: cria um evento novo nesse dia. */
    val onNewEvent: (epochDay: Long) -> Unit,
    /** Toque num marco da literatura: abre a linha do tempo de mudanças esperadas (Seção 7.2). */
    val onOpenExpectedChanges: () -> Unit,
)
