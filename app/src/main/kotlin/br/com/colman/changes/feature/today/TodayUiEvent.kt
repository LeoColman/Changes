// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.today

import kotlin.uuid.Uuid

/** Ações da tela Hoje que mudam dado (navegação pura fica em [TodayNavigation]). */
sealed interface TodayUiEvent {
    /** Fluxo de um toque (Seção 7.1): grava a dose do regime com a hora atual. */
    data class LogDose(val regimenId: Uuid) : TodayUiEvent

    /** Desfaz o registro feito por [LogDose] (soft delete). */
    data class UndoDoseLog(val doseLogId: Uuid) : TodayUiEvent
}

/** Efeito de uma vez emitido após [TodayUiEvent.LogDose] ter sucesso: mostra o snackbar de desfazer. */
sealed interface TodayEffect {
    data class DoseLogged(val doseLogId: Uuid) : TodayEffect
}
