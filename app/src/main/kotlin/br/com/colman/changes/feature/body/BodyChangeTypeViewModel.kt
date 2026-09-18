// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.changes.core.data.BodyChangeRepository
import br.com.colman.changes.core.data.MediaRepository
import br.com.colman.changes.core.model.BodyChangeCategory
import br.com.colman.changes.core.model.BodyChangeEntry
import br.com.colman.changes.core.model.BodyChangeType
import br.com.colman.changes.core.model.BodyMeasurementUnit
import br.com.colman.changes.core.model.BodyVocabulary
import br.com.colman.changes.core.model.MediaOwnerType
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.platform.VoicePlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/** Linha do tempo de um tipo de mudança, com comparação de fotos (Seção 7.3, item 2). */
@Immutable
data class BodyChangeTypeUiState(
    val isLoading: Boolean = true,
    val typeLabel: String = "",
    val isBuiltin: Boolean = true,
    val isHidden: Boolean = false,
    val supportsMeasurement: Boolean = false,
    val measurementUnit: BodyMeasurementUnit? = null,
    val entries: List<BodyEntrySummary> = emptyList(),
    val comparison: ComparisonState = ComparisonState(),
    /** Categoria `VOICE` (ADR 0013): oferece "Ouvir a primeira"/"Ouvir a mais recente". */
    val isVoiceCategory: Boolean = false,
    /** Id da entrada cuja gravação está tocando agora; um áudio por vez. */
    val playingEntryId: String? = null,
    val pendingDeletionEntryId: String? = null,
    val deleteTypeRequested: Boolean = false,
    val errorMessage: BodyErrorMessage? = null,
)

/** Estado da comparação lado a lado: entradas com foto e o par escolhido para o slider de data. */
@Immutable
data class ComparisonState(
    val candidates: List<BodyEntrySummary> = emptyList(),
    val leftEntryId: String? = null,
    val rightEntryId: String? = null,
    val sliderPosition: Float = DEFAULT_SLIDER_POSITION,
)

private const val DEFAULT_SLIDER_POSITION = 0.5f

sealed interface BodyChangeTypeUiEvent {
    data object ToggleHidden : BodyChangeTypeUiEvent

    data object RequestDeleteType : BodyChangeTypeUiEvent

    data object DismissDeleteType : BodyChangeTypeUiEvent

    data object ConfirmDeleteType : BodyChangeTypeUiEvent

    data class DeleteEntry(val entryId: String) : BodyChangeTypeUiEvent

    data object UndoDeleteEntry : BodyChangeTypeUiEvent

    data class SelectComparisonLeft(val entryId: String) : BodyChangeTypeUiEvent

    data class SelectComparisonRight(val entryId: String) : BodyChangeTypeUiEvent

    data class SetComparisonSlider(val position: Float) : BodyChangeTypeUiEvent

    /** Toca a gravação de voz de uma entrada (ADR 0013); usado também pelos botões do topo. */
    data class PlayVoice(val entryId: String) : BodyChangeTypeUiEvent

    data object StopVoice : BodyChangeTypeUiEvent

    data object ErrorMessageShown : BodyChangeTypeUiEvent
}

class BodyChangeTypeViewModel(
    private val bodyChangeRepository: BodyChangeRepository,
    private val mediaRepository: MediaRepository,
    private val bodyLabels: BodyLabels,
    private val voicePlayer: VoicePlayer,
    typeId: String,
) : ViewModel() {

    private val typeUuid = Uuid.parse(typeId)
    private val local = MutableStateFlow(LocalState())

    val state: StateFlow<BodyChangeTypeUiState> = combine(
        bodyChangeRepository.observeAllTypes(),
        bodyChangeRepository.observeEntriesByType(typeUuid),
        bodyLabels.observeVocabulary(),
        local,
    ) { types, entries, vocabulary, localState -> buildState(types, entries, vocabulary, localState) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BodyChangeTypeUiState())

    fun onEvent(event: BodyChangeTypeUiEvent) {
        if (handleVoiceEvent(event)) return
        if (handleComparisonEvent(event)) return
        when (event) {
            BodyChangeTypeUiEvent.ToggleHidden -> toggleHidden()
            BodyChangeTypeUiEvent.RequestDeleteType -> local.update { it.copy(deleteTypeRequested = true) }
            BodyChangeTypeUiEvent.DismissDeleteType -> local.update { it.copy(deleteTypeRequested = false) }
            BodyChangeTypeUiEvent.ConfirmDeleteType -> confirmDeleteType()
            is BodyChangeTypeUiEvent.DeleteEntry -> deleteEntry(event.entryId)
            BodyChangeTypeUiEvent.UndoDeleteEntry -> undoDeleteEntry()
            BodyChangeTypeUiEvent.ErrorMessageShown -> local.update { it.copy(errorMessage = null) }
            else -> Unit
        }
    }

    /** Seleção do par e do slider de comparação, fora do `when` principal (complexidade ciclomática). */
    private fun handleComparisonEvent(event: BodyChangeTypeUiEvent): Boolean {
        when (event) {
            is BodyChangeTypeUiEvent.SelectComparisonLeft -> local.update { it.copy(comparisonLeft = event.entryId) }
            is BodyChangeTypeUiEvent.SelectComparisonRight ->
                local.update { it.copy(comparisonRight = event.entryId) }
            is BodyChangeTypeUiEvent.SetComparisonSlider -> local.update { it.copy(sliderPosition = event.position) }
            else -> return false
        }
        return true
    }

    /** Eventos de reprodução de voz, fora do `when` principal (complexidade ciclomática). */
    private fun handleVoiceEvent(event: BodyChangeTypeUiEvent): Boolean {
        when (event) {
            is BodyChangeTypeUiEvent.PlayVoice -> playVoice(event.entryId)
            BodyChangeTypeUiEvent.StopVoice -> stopVoice()
            else -> return false
        }
        return true
    }

    override fun onCleared() {
        super.onCleared()
        voicePlayer.stop()
    }

    private fun playVoice(entryId: String) {
        val voice = state.value.entries.firstOrNull { it.entryId == entryId }?.voice ?: return
        local.update { it.copy(playingEntryId = entryId) }
        voicePlayer.playMedia(voice.relativePath) { local.update { current -> current.copy(playingEntryId = null) } }
    }

    private fun stopVoice() {
        voicePlayer.stop()
        local.update { it.copy(playingEntryId = null) }
    }

    private fun toggleHidden() {
        viewModelScope.launch {
            val type = bodyChangeRepository.getType(typeUuid) ?: return@launch
            bodyChangeRepository.setTypeHidden(typeUuid, !type.isHidden)
        }
    }

    private fun confirmDeleteType() {
        viewModelScope.launch {
            val result = bodyChangeRepository.deleteType(typeUuid)
            val error = (result as? Result.Failure)?.error?.toBodyErrorMessage()
            local.update { it.copy(deleteTypeRequested = false, errorMessage = error) }
        }
    }

    private fun deleteEntry(entryId: String) {
        viewModelScope.launch {
            bodyChangeRepository.deleteEntry(Uuid.parse(entryId))
            local.update { it.copy(pendingDeletionEntryId = entryId) }
        }
    }

    private fun undoDeleteEntry() {
        val entryId = local.value.pendingDeletionEntryId ?: return
        viewModelScope.launch {
            bodyChangeRepository.restoreEntry(Uuid.parse(entryId))
            local.update { it.copy(pendingDeletionEntryId = null) }
        }
    }

    private suspend fun buildState(
        types: List<BodyChangeType>,
        entries: List<BodyChangeEntry>,
        vocabulary: BodyVocabulary,
        localState: LocalState,
    ): BodyChangeTypeUiState {
        val type = types.firstOrNull { it.id == typeUuid }
        val summaries = entries.map { toSummary(it) }
        val candidates = summaries.filter { it.photo != null }
        return BodyChangeTypeUiState(
            isLoading = false,
            typeLabel = type?.let { bodyLabels.label(it, vocabulary) }.orEmpty(),
            isBuiltin = type?.isBuiltin ?: true,
            isHidden = type?.isHidden == true,
            supportsMeasurement = type?.supportsMeasurement == true,
            measurementUnit = type?.measurementUnit,
            entries = summaries,
            comparison = ComparisonState(
                candidates = candidates,
                leftEntryId = localState.comparisonLeft?.takeIf { id -> candidates.any { it.entryId == id } },
                rightEntryId = localState.comparisonRight?.takeIf { id -> candidates.any { it.entryId == id } },
                sliderPosition = localState.sliderPosition,
            ),
            isVoiceCategory = type?.category == BodyChangeCategory.VOICE,
            playingEntryId = localState.playingEntryId,
            pendingDeletionEntryId = localState.pendingDeletionEntryId,
            deleteTypeRequested = localState.deleteTypeRequested,
            errorMessage = localState.errorMessage,
        )
    }

    /** [BodyEntrySummary.photo] nunca é a gravação de voz da entrada, e vice-versa (ADR 0013). */
    private suspend fun toSummary(entry: BodyChangeEntry): BodyEntrySummary {
        val media = mediaRepository.observeByOwner(MediaOwnerType.BODY_CHANGE_ENTRY, entry.id).first()
        return BodyEntrySummary(
            entryId = entry.id.toString(),
            observedAt = entry.observedAt,
            intensity = entry.intensity,
            measurementValue = entry.measurementValue,
            measurementUnit = entry.measurementUnit,
            notes = entry.notes,
            photo = media.firstOrNull { !it.isAudio },
            voice = media.firstOrNull { it.isAudio },
        )
    }

    private data class LocalState(
        val comparisonLeft: String? = null,
        val comparisonRight: String? = null,
        val sliderPosition: Float = DEFAULT_SLIDER_POSITION,
        val playingEntryId: String? = null,
        val pendingDeletionEntryId: String? = null,
        val deleteTypeRequested: Boolean = false,
        val errorMessage: BodyErrorMessage? = null,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
