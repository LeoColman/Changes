// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.changes.core.data.BodyChangeRepository
import br.com.colman.changes.core.data.MediaRepository
import br.com.colman.changes.core.model.BodyChangeCategory
import br.com.colman.changes.core.model.BodyChangeType
import br.com.colman.changes.core.model.BodyMeasurementUnit
import br.com.colman.changes.core.model.BodyVocabulary
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.Intensity
import br.com.colman.changes.core.model.MediaAttachment
import br.com.colman.changes.core.model.MediaOwnerType
import br.com.colman.changes.core.model.MediaPaths
import br.com.colman.changes.core.model.RecordedTime
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.model.TimeZoneProvider
import br.com.colman.changes.core.model.asFailure
import br.com.colman.changes.core.model.map
import br.com.colman.changes.platform.AndroidVoiceRecorder
import br.com.colman.changes.platform.VoicePlayer
import br.com.colman.changes.platform.VoiceRecorder
import br.com.colman.changes.ui.format.Formatters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import java.io.File
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.uuid.Uuid

private const val JPEG_MIME_TYPE = "image/jpeg"
private const val MILLIS_PER_SECOND = 1_000

/** Argumentos de navegação de `BodyEntryEditRoute`: um dos dois é obrigatório. */
data class BodyEntryEditArgs(val typeId: String?, val entryId: String?)

/** Repositórios da edição de uma entrada, agrupados para não estourar o limite de parâmetros. */
data class BodyEntryEditRepositories(
    val bodyChangeRepository: BodyChangeRepository,
    val mediaRepository: MediaRepository,
    val bodyLabels: BodyLabels,
)

/** Gravador, player e relógio da gravação de voz (ADR 0013), agrupados pelo mesmo motivo. */
data class BodyVoiceControls(val recorder: VoiceRecorder, val player: VoicePlayer, val clock: Clock)

/** Uma foto exibida no formulário: já anexada, ou pendente de anexar ao salvar. */
sealed interface EntryPhoto {
    val key: String

    data class Attached(val media: MediaAttachment) : EntryPhoto {
        override val key: String get() = media.id.toString()
    }

    data class Pending(val token: String, val filePath: String) : EntryPhoto {
        override val key: String get() = token
    }
}

/** A gravação de voz da entrada (ADR 0013): já anexada, ou pendente de anexar ao salvar. */
sealed interface EntryVoiceRecording {
    data class Attached(val media: MediaAttachment) : EntryVoiceRecording

    data class Pending(val file: File) : EntryVoiceRecording
}

/** Estado da seção de gravação de voz: sem gravação, gravando, ou uma gravação pronta pra ouvir. */
sealed interface VoiceRecordingUiState {
    data object None : VoiceRecordingUiState

    data class Recording(val elapsedSeconds: Int) : VoiceRecordingUiState

    data class Recorded(val isPlaying: Boolean) : VoiceRecordingUiState
}

/** Criação/edição de uma entrada de mudança corporal, com fotos e voz (Seção 7.3, item 3; ADR 0013). */
@Immutable
data class BodyEntryEditUiState(
    val isLoading: Boolean = true,
    val typeMissing: Boolean = false,
    val isNew: Boolean = true,
    val typeLabel: String = "",
    val supportsMeasurement: Boolean = false,
    val measurementUnit: BodyMeasurementUnit? = null,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val intensity: Intensity? = null,
    val measurementText: String = "",
    val notes: String = "",
    val photos: List<EntryPhoto> = emptyList(),
    /** Categoria `VOICE` (ADR 0013), hoje só `VOICE_DEEPENING`: oferece a seção de gravação. */
    val supportsVoiceRecording: Boolean = false,
    val voiceState: VoiceRecordingUiState = VoiceRecordingUiState.None,
    val voiceRecording: EntryVoiceRecording? = null,
    val microphoneUnavailable: Boolean = false,
    val errorMessage: BodyErrorMessage? = null,
)

sealed interface BodyEntryEditUiEvent {
    data class DateChanged(val date: LocalDate) : BodyEntryEditUiEvent

    data class TimeChanged(val time: LocalTime) : BodyEntryEditUiEvent

    data class IntensityChanged(val intensity: Intensity?) : BodyEntryEditUiEvent

    data class MeasurementChanged(val text: String) : BodyEntryEditUiEvent

    data class NotesChanged(val text: String) : BodyEntryEditUiEvent

    data class PhotoSelected(val raw: RawBodyPhoto) : BodyEntryEditUiEvent

    data class RemovePhoto(val photoKey: String) : BodyEntryEditUiEvent

    /** Disparado pela Route depois que a permissão do microfone foi concedida. */
    data object StartRecordingVoice : BodyEntryEditUiEvent

    data object MicrophonePermissionDenied : BodyEntryEditUiEvent

    data object StopRecordingVoice : BodyEntryEditUiEvent

    data object PlayVoice : BodyEntryEditUiEvent

    data object StopVoice : BodyEntryEditUiEvent

    data object DeleteVoice : BodyEntryEditUiEvent

    data object Save : BodyEntryEditUiEvent

    data object ErrorMessageShown : BodyEntryEditUiEvent
}

/** Efeito de uma vez: a Route navega de volta quando o salvamento termina. */
sealed interface BodyEntryEditEffect {
    data object Saved : BodyEntryEditEffect
}

@OptIn(ExperimentalCoroutinesApi::class)
class BodyEntryEditViewModel(
    private val repositories: BodyEntryEditRepositories,
    private val photoIntake: BodyPhotoIntake,
    private val timeZones: TimeZoneProvider,
    private val voiceControls: BodyVoiceControls,
    private val args: BodyEntryEditArgs,
) : ViewModel() {

    private val bodyChangeRepository get() = repositories.bodyChangeRepository
    private val mediaRepository get() = repositories.mediaRepository
    private val bodyLabels get() = repositories.bodyLabels

    private val draft = MutableStateFlow(Draft())

    /**
     * Id da entrada sendo editada. Para uma entrada nova começa `null` e passa a valer assim que o
     * primeiro Salvar cria a linha, para que as fotos anexadas na sequência apareçam no formulário.
     */
    private val currentEntryId = MutableStateFlow(args.entryId?.let { Uuid.parse(it) })

    private val attachedMedia: Flow<List<MediaAttachment>> = currentEntryId.flatMapLatest { id ->
        if (id != null) mediaRepository.observeByOwner(MediaOwnerType.BODY_CHANGE_ENTRY, id) else flowOf(emptyList())
    }

    val state: StateFlow<BodyEntryEditUiState> =
        combine(draft, attachedMedia, bodyLabels.observeVocabulary()) { d, attached, vocabulary ->
            d.toUiState(attached, vocabulary)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BodyEntryEditUiState())

    private val effectChannel = Channel<BodyEntryEditEffect>(Channel.BUFFERED)
    val effects: Flow<BodyEntryEditEffect> = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch { loadInitial() }
    }

    fun onEvent(event: BodyEntryEditUiEvent) {
        if (handleVoiceEvent(event)) return
        if (handleDraftEvent(event)) return
        when (event) {
            BodyEntryEditUiEvent.Save -> save()
            BodyEntryEditUiEvent.ErrorMessageShown -> draft.update { it.copy(errorMessage = null) }
            else -> Unit
        }
    }

    /** Os campos simples do formulário e as fotos, fora do `when` principal (complexidade ciclomática). */
    private fun handleDraftEvent(event: BodyEntryEditUiEvent): Boolean {
        when (event) {
            is BodyEntryEditUiEvent.DateChanged -> draft.update { it.copy(date = event.date) }
            is BodyEntryEditUiEvent.TimeChanged -> draft.update { it.copy(time = event.time) }
            is BodyEntryEditUiEvent.IntensityChanged -> draft.update { it.copy(intensity = event.intensity) }
            is BodyEntryEditUiEvent.MeasurementChanged -> draft.update { it.copy(measurementText = event.text) }
            is BodyEntryEditUiEvent.NotesChanged -> draft.update { it.copy(notes = event.text) }
            is BodyEntryEditUiEvent.PhotoSelected -> addPhoto(event.raw)
            is BodyEntryEditUiEvent.RemovePhoto -> removePhoto(event.photoKey)
            else -> return false
        }
        return true
    }

    /** Eventos de gravação/reprodução de voz, fora do `when` principal (complexidade ciclomática). */
    private fun handleVoiceEvent(event: BodyEntryEditUiEvent): Boolean {
        when (event) {
            BodyEntryEditUiEvent.StartRecordingVoice -> startRecordingVoice()
            BodyEntryEditUiEvent.MicrophonePermissionDenied -> draft.update { it.copy(microphoneUnavailable = true) }
            BodyEntryEditUiEvent.StopRecordingVoice -> stopRecordingVoice()
            BodyEntryEditUiEvent.PlayVoice -> playVoice()
            BodyEntryEditUiEvent.StopVoice -> stopVoice()
            BodyEntryEditUiEvent.DeleteVoice -> deleteVoice()
            else -> return false
        }
        return true
    }

    /**
     * Uma gravação em curso não sobrevive à tela fechando, nenhum áudio fica tocando sozinho, e uma
     * gravação pendente que não foi salva sai do cache.
     */
    override fun onCleared() {
        super.onCleared()
        voiceControls.recorder.cancel()
        voiceControls.player.stop()
        draft.value.pendingVoiceFile?.delete()
    }

    private suspend fun loadInitial() {
        val entry = args.entryId?.let { bodyChangeRepository.getEntry(Uuid.parse(it)) }
        val type = entry?.let { bodyChangeRepository.getType(it.changeTypeId) }
            ?: args.typeId?.let { bodyChangeRepository.getType(Uuid.parse(it)) }
        if (type == null) {
            draft.update { it.copy(isLoading = false, typeMissing = true) }
            return
        }
        draft.update {
            it.copy(
                isLoading = false,
                type = type,
                date = entry?.observedAt?.localDateTime?.date,
                time = entry?.observedAt?.localDateTime?.time,
                intensity = entry?.intensity,
                measurementText = entry?.measurementValue?.let { value -> Formatters.number(value) }.orEmpty(),
                notes = entry?.notes.orEmpty(),
            )
        }
    }

    private fun addPhoto(raw: RawBodyPhoto) {
        viewModelScope.launch {
            val file = photoIntake.sanitize(raw)
            draft.update { it.copy(pendingPhotos = it.pendingPhotos + PendingPhoto(Uuid.random().toString(), file)) }
        }
    }

    private fun removePhoto(photoKey: String) {
        draft.update { current ->
            val pending = current.pendingPhotos.find { it.token == photoKey }
            if (pending != null) {
                pending.file.delete()
                current.copy(pendingPhotos = current.pendingPhotos.filterNot { it.token == photoKey })
            } else {
                current.copy(removedAttachedIds = current.removedAttachedIds + photoKey)
            }
        }
    }

    /** `start()` nulo é microfone indisponível (sem permissão, ocupado): mostra o aviso e não quebra. */
    private fun startRecordingVoice() {
        draft.update { it.copy(microphoneUnavailable = false) }
        val file = voiceControls.recorder.start()
        if (file == null) {
            draft.update { it.copy(microphoneUnavailable = true) }
            return
        }
        draft.update { it.copy(recordingFile = file, recordingElapsedSeconds = 0) }
        viewModelScope.launch { tickRecording() }
    }

    /** Conta os segundos gravados; para sozinho ao atingir [AndroidVoiceRecorder.MAX_DURATION_MS]. */
    private suspend fun tickRecording() {
        while (draft.value.recordingFile != null && draft.value.recordingElapsedSeconds < MAX_RECORDING_SECONDS) {
            delay(1.seconds)
            draft.update { current ->
                if (current.recordingFile == null) {
                    current
                } else {
                    current.copy(recordingElapsedSeconds = current.recordingElapsedSeconds + 1)
                }
            }
        }
        if (draft.value.recordingFile != null) stopRecordingVoice()
    }

    private fun stopRecordingVoice() {
        val file = voiceControls.recorder.stop()
        draft.update { current ->
            current.copy(
                recordingFile = null,
                recordingElapsedSeconds = 0,
                pendingVoiceFile = file ?: current.pendingVoiceFile,
                microphoneUnavailable = current.microphoneUnavailable || file == null,
            )
        }
    }

    private fun playVoice() {
        val voice = state.value.voiceRecording ?: return
        draft.update { it.copy(isPlayingVoice = true) }
        val onFinished = { draft.update { current -> current.copy(isPlayingVoice = false) } }
        when (voice) {
            is EntryVoiceRecording.Pending -> voiceControls.player.play(voice.file, onFinished)
            is EntryVoiceRecording.Attached -> voiceControls.player.playMedia(voice.media.relativePath, onFinished)
        }
    }

    private fun stopVoice() {
        voiceControls.player.stop()
        draft.update { it.copy(isPlayingVoice = false) }
    }

    private fun deleteVoice() {
        val voice = state.value.voiceRecording ?: return
        voiceControls.player.stop()
        draft.update { current ->
            val next = when (voice) {
                is EntryVoiceRecording.Pending -> {
                    voice.file.delete()
                    current.copy(pendingVoiceFile = null)
                }

                is EntryVoiceRecording.Attached -> current.copy(
                    removedAttachedIds = current.removedAttachedIds + voice.media.id.toString(),
                )
            }
            next.copy(isPlayingVoice = false)
        }
    }

    private fun save() {
        // Salvar no meio de uma gravação encerra a gravação e anexa o que foi gravado.
        if (draft.value.recordingFile != null) stopRecordingVoice()
        val current = draft.value
        val type = current.type ?: return
        val date = current.date
        val time = current.time
        if (date == null || time == null) {
            draft.update { it.copy(errorMessage = BodyErrorMessage.REQUIRED_FIELD) }
            return
        }
        val measurementValue = Formatters.parseNumber(current.measurementText)
        val observedAt = LocalDateTime(date, time).toInstant(timeZones.current())
        viewModelScope.launch {
            val result = submit(type, observedAt, current, measurementValue)
            when (result) {
                is Result.Success -> {
                    currentEntryId.value = result.value
                    finishSave(result.value, current)
                }
                is Result.Failure -> draft.update { it.copy(errorMessage = result.error.toBodyErrorMessage()) }
            }
        }
    }

    private suspend fun submit(
        type: BodyChangeType,
        observedAt: Instant,
        current: Draft,
        measurementValue: Double?,
    ): Result<Uuid> {
        val notes = current.notes.ifBlank { null }
        val entryId = args.entryId
        return if (entryId != null) {
            updateExisting(Uuid.parse(entryId), observedAt, current, measurementValue, notes)
        } else {
            bodyChangeRepository.createEntry(type.id, observedAt, current.intensity, measurementValue, notes)
                .map { it.id }
        }
    }

    private suspend fun updateExisting(
        id: Uuid,
        observedAt: Instant,
        current: Draft,
        measurementValue: Double?,
        notes: String?,
    ): Result<Uuid> {
        val existing = bodyChangeRepository.getEntry(id)
            ?: return DomainError.NotFound("bodyChangeEntry", id.toString()).asFailure()
        val updated = existing.copy(
            observedAt = RecordedTime.of(observedAt, timeZones.current()),
            intensity = current.intensity,
            measurementValue = measurementValue,
            notes = notes,
        )
        return bodyChangeRepository.updateEntry(updated).map { it.id }
    }

    private suspend fun finishSave(entryId: Uuid, current: Draft) {
        current.removedAttachedIds.forEach { mediaRepository.delete(Uuid.parse(it)) }
        current.pendingPhotos.forEach { pending ->
            pending.file.inputStream().use { stream ->
                mediaRepository.attach(MediaOwnerType.BODY_CHANGE_ENTRY, entryId, stream, JPEG_MIME_TYPE, null)
            }
            pending.file.delete()
        }
        current.pendingVoiceFile?.let { file -> attachVoice(entryId, file) }
        draft.update {
            it.copy(pendingPhotos = emptyList(), removedAttachedIds = emptySet(), pendingVoiceFile = null)
        }
        effectChannel.send(BodyEntryEditEffect.Saved)
    }

    /** Anexa como as fotos pendentes, com `capturedAt` marcando o instante da gravação (ADR 0013). */
    private suspend fun attachVoice(entryId: Uuid, file: File) {
        file.inputStream().use { stream ->
            mediaRepository.attach(
                MediaOwnerType.BODY_CHANGE_ENTRY,
                entryId,
                stream,
                MediaPaths.VOICE_MIME_TYPE,
                voiceControls.clock.now(),
            )
        }
        file.delete()
    }

    private fun Draft.toUiState(attached: List<MediaAttachment>, vocabulary: BodyVocabulary): BodyEntryEditUiState {
        val visibleAttached = attached.filterNot { it.id.toString() in removedAttachedIds }
        val photos = visibleAttached.filterNot { it.isAudio }.map { EntryPhoto.Attached(it) } +
            pendingPhotos.map { EntryPhoto.Pending(it.token, it.file.path) }
        val voiceRecording = voiceRecordingOf(visibleAttached)
        return BodyEntryEditUiState(
            isLoading = isLoading,
            typeMissing = typeMissing,
            isNew = args.entryId == null,
            typeLabel = type?.let { bodyLabels.label(it, vocabulary) }.orEmpty(),
            supportsMeasurement = type?.supportsMeasurement == true,
            measurementUnit = type?.measurementUnit,
            date = date,
            time = time,
            intensity = intensity,
            measurementText = measurementText,
            notes = notes,
            photos = photos,
            supportsVoiceRecording = type?.category == BodyChangeCategory.VOICE,
            voiceState = voiceStateOf(voiceRecording),
            voiceRecording = voiceRecording,
            microphoneUnavailable = microphoneUnavailable,
            errorMessage = errorMessage,
        )
    }

    /** Uma gravação pendente tem prioridade: é ela que vai ser anexada no próximo Salvar. */
    private fun Draft.voiceRecordingOf(visibleAttached: List<MediaAttachment>): EntryVoiceRecording? = when {
        pendingVoiceFile != null -> EntryVoiceRecording.Pending(pendingVoiceFile)
        else -> visibleAttached.firstOrNull { it.isAudio }?.let { EntryVoiceRecording.Attached(it) }
    }

    private fun Draft.voiceStateOf(voiceRecording: EntryVoiceRecording?): VoiceRecordingUiState = when {
        recordingFile != null -> VoiceRecordingUiState.Recording(recordingElapsedSeconds)
        voiceRecording != null -> VoiceRecordingUiState.Recorded(isPlayingVoice)
        else -> VoiceRecordingUiState.None
    }

    private data class Draft(
        val isLoading: Boolean = true,
        val typeMissing: Boolean = false,
        val type: BodyChangeType? = null,
        val date: LocalDate? = null,
        val time: LocalTime? = null,
        val intensity: Intensity? = null,
        val measurementText: String = "",
        val notes: String = "",
        val removedAttachedIds: Set<String> = emptySet(),
        val pendingPhotos: List<PendingPhoto> = emptyList(),
        val recordingFile: File? = null,
        val recordingElapsedSeconds: Int = 0,
        val pendingVoiceFile: File? = null,
        val isPlayingVoice: Boolean = false,
        val microphoneUnavailable: Boolean = false,
        val errorMessage: BodyErrorMessage? = null,
    )

    private data class PendingPhoto(val token: String, val file: File)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val MAX_RECORDING_SECONDS = AndroidVoiceRecorder.MAX_DURATION_MS / MILLIS_PER_SECOND
    }
}
