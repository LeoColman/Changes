// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.changes.core.data.backup.BackupRepository
import br.com.colman.changes.core.data.backup.ImportMode
import br.com.colman.changes.core.model.BackupRejection
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.model.TimeZoneProvider
import br.com.colman.changes.core.model.asFailure
import br.com.colman.changes.platform.AppLogger
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
import java.io.File
import java.io.IOException
import kotlin.time.Clock

private const val LOG_TAG = "Backup"

/**
 * Tela Backup (Seção 7.8, ADR 0009): exporta tudo para um arquivo e prepara a prévia de um import.
 * O plano calculado por [BackupRepository.plan] vai para [planHolder]; a tela de prévia lê de lá,
 * porque `ImportPlan` carrega um `File` e não cabe num argumento de navegação.
 */
class BackupViewModel(
    private val repository: BackupRepository,
    private val documentStreams: DocumentStreams,
    private val planHolder: ImportPlanHolder,
    private val clock: Clock,
    private val timeZoneProvider: TimeZoneProvider,
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState(suggestedFileName = suggestedFileName()))
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    private val effectChannel = Channel<BackupEffect>(Channel.BUFFERED)
    val effects: Flow<BackupEffect> = effectChannel.receiveAsFlow()

    private var importFile: File? = null

    fun onEvent(event: BackupUiEvent) {
        when (event) {
            is BackupUiEvent.ExportRequested -> export(event.uri)
            is BackupUiEvent.ImportFilePicked -> pickImportFile(event.uri)
            is BackupUiEvent.ImportModeChosen -> plan(event.mode)
            BackupUiEvent.CancelImportPick -> cancelImportPick()
            BackupUiEvent.DismissError -> _state.update { it.copy(error = null) }
        }
    }

    private fun suggestedFileName(): String {
        val today = clock.now().toLocalDateTime(timeZoneProvider.current()).date
        return "changes-$today.ttbackup.zip"
    }

    /** O destino escolhido pode falhar ao abrir (provedor do SAF indisponível): vira erro, nunca crash. */
    private fun export(uri: String) = viewModelScope.launch {
        _state.update { it.copy(isExporting = true, error = null) }
        val result = try {
            repository.export(documentStreams.openOutput(uri))
        } catch (error: IOException) {
            DomainError.BackupRejected(BackupRejection.EXPORT_FAILED, error.message).asFailure()
        }
        when (result) {
            is Result.Success -> _state.update { it.copy(isExporting = false, exportSummary = result.value) }
            is Result.Failure -> _state.update {
                it.copy(isExporting = false, error = result.error.rejectionOrNull())
            }
        }
    }

    private fun pickImportFile(uri: String) = viewModelScope.launch {
        _state.update { it.copy(error = null) }
        try {
            importFile = documentStreams.copyToCache(uri)
            _state.update { it.copy(choosingImportMode = true) }
        } catch (error: IOException) {
            AppLogger.warn(LOG_TAG) { "Import copy failed: ${error.message}" }
            _state.update { it.copy(error = BackupRejection.UNREADABLE) }
        }
    }

    private fun plan(mode: ImportMode) {
        val archive = importFile ?: return
        viewModelScope.launch {
            _state.update { it.copy(isPlanning = true) }
            when (val result = repository.plan(archive, mode)) {
                is Result.Success -> {
                    planHolder.set(result.value)
                    importFile = null
                    _state.update { it.copy(isPlanning = false, choosingImportMode = false) }
                    effectChannel.send(BackupEffect.OpenImportPreview)
                }
                is Result.Failure -> {
                    documentStreams.clearImportCache()
                    importFile = null
                    _state.update {
                        it.copy(isPlanning = false, choosingImportMode = false, error = result.error.rejectionOrNull())
                    }
                }
            }
        }
    }

    private fun cancelImportPick() = viewModelScope.launch {
        documentStreams.clearImportCache()
        importFile = null
        _state.update { it.copy(choosingImportMode = false) }
    }
}
