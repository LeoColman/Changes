// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.changes.core.data.backup.BackupRepository
import br.com.colman.changes.core.data.backup.ImportPlan
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.model.TimeZoneProvider
import br.com.colman.changes.platform.DocumentStreams
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime

/**
 * Tela de prévia do import (Seção 7.8, ADR 0009): mostra o efeito calculado por
 * [BackupRepository.plan] e só aplica depois de confirmação na tela (Mesclar: `ConfirmDialog`;
 * Substituir: `TypedConfirmDialog`, com a palavra exigida). Cancelar ou concluir apaga a cópia
 * temporária do arquivo ([DocumentStreams.clearImportCache]) e limpa [planHolder].
 */
class ImportPreviewViewModel(
    private val repository: BackupRepository,
    private val documentStreams: DocumentStreams,
    private val planHolder: ImportPlanHolder,
    timeZoneProvider: TimeZoneProvider,
) : ViewModel() {

    private val plan: ImportPlan? = planHolder.plan

    private val _state = MutableStateFlow(plan?.let { initialStateOf(it, timeZoneProvider) } ?: ImportPreviewUiState())
    val state: StateFlow<ImportPreviewUiState> = _state.asStateFlow()

    private val effectChannel = Channel<ImportPreviewEffect>(Channel.BUFFERED)
    val effects: Flow<ImportPreviewEffect> = effectChannel.receiveAsFlow()

    fun onEvent(event: ImportPreviewUiEvent) {
        when (event) {
            ImportPreviewUiEvent.RequestApply -> _state.update { it.copy(showConfirm = true) }
            ImportPreviewUiEvent.DismissConfirm -> _state.update { it.copy(showConfirm = false) }
            ImportPreviewUiEvent.ConfirmApply -> apply()
            ImportPreviewUiEvent.Cancel -> cancel()
            ImportPreviewUiEvent.Done -> finish()
        }
    }

    private fun apply() {
        val currentPlan = plan ?: return
        viewModelScope.launch {
            _state.update { it.copy(showConfirm = false, isApplying = true, error = null) }
            when (val result = repository.apply(currentPlan)) {
                is Result.Success -> {
                    documentStreams.clearImportCache()
                    planHolder.clear()
                    _state.update { it.copy(isApplying = false, result = result.value) }
                }
                is Result.Failure -> _state.update {
                    it.copy(isApplying = false, error = result.error.rejectionOrNull())
                }
            }
        }
    }

    private fun cancel() = viewModelScope.launch {
        documentStreams.clearImportCache()
        planHolder.clear()
        effectChannel.send(ImportPreviewEffect.NavigateBack)
    }

    private fun finish() = viewModelScope.launch {
        effectChannel.send(ImportPreviewEffect.Finished)
    }

    private companion object {
        fun initialStateOf(plan: ImportPlan, timeZoneProvider: TimeZoneProvider) = ImportPreviewUiState(
            mode = plan.mode,
            schemaVersion = plan.schemaVersion,
            appVersion = plan.appVersion,
            exportedAt = plan.exportedAt.toLocalDateTime(timeZoneProvider.current()),
            tables = plan.tables,
            mediaToAdd = plan.mediaToAdd,
            missingMedia = plan.missingMedia,
        )
    }
}
