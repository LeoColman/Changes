// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.changes.core.data.BodyChangeRepository
import br.com.colman.changes.core.data.MediaRepository
import br.com.colman.changes.core.model.BodyChangeType
import br.com.colman.changes.core.model.BodyMeasurementUnit
import br.com.colman.changes.core.model.BodyVocabulary
import br.com.colman.changes.core.model.DomainError
import br.com.colman.changes.core.model.Intensity
import br.com.colman.changes.core.model.MediaAttachment
import br.com.colman.changes.core.model.MediaOwnerType
import br.com.colman.changes.core.model.RecordedTime
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.model.TimeZoneProvider
import br.com.colman.changes.core.model.asFailure
import br.com.colman.changes.core.model.map
import br.com.colman.changes.ui.format.Formatters
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
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
import kotlin.time.Instant
import kotlin.uuid.Uuid

private const val JPEG_MIME_TYPE = "image/jpeg"

/** Argumentos de navegação de `BodyEntryEditRoute`: um dos dois é obrigatório. */
data class BodyEntryEditArgs(val typeId: String?, val entryId: String?)

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

/** Criação/edição de uma entrada de mudança corporal, com fotos (Seção 7.3, item 3). */
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

    data object Save : BodyEntryEditUiEvent

    data object ErrorMessageShown : BodyEntryEditUiEvent
}

/** Efeito de uma vez: a Route navega de volta quando o salvamento termina. */
sealed interface BodyEntryEditEffect {
    data object Saved : BodyEntryEditEffect
}

@OptIn(ExperimentalCoroutinesApi::class)
class BodyEntryEditViewModel(
    private val bodyChangeRepository: BodyChangeRepository,
    private val mediaRepository: MediaRepository,
    private val bodyLabels: BodyLabels,
    private val photoIntake: BodyPhotoIntake,
    private val timeZones: TimeZoneProvider,
    private val args: BodyEntryEditArgs,
) : ViewModel() {

    private val draft = MutableStateFlow(Draft())

    /**
     * Id da entrada sendo editada. Para uma entrada nova começa `null` e passa a valer assim que o
     * primeiro Salvar cria a linha, para que as fotos anexadas na sequência apareçam no formulário.
     */
    private val currentEntryId = MutableStateFlow(args.entryId?.let { Uuid.parse(it) })

    private val attachedPhotos: Flow<List<MediaAttachment>> = currentEntryId.flatMapLatest { id ->
        if (id != null) mediaRepository.observeByOwner(MediaOwnerType.BODY_CHANGE_ENTRY, id) else flowOf(emptyList())
    }

    val state: StateFlow<BodyEntryEditUiState> =
        combine(draft, attachedPhotos, bodyLabels.observeVocabulary()) { d, attached, vocabulary ->
            d.toUiState(attached, vocabulary)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BodyEntryEditUiState())

    private val effectChannel = Channel<BodyEntryEditEffect>(Channel.BUFFERED)
    val effects: Flow<BodyEntryEditEffect> = effectChannel.receiveAsFlow()

    init {
        viewModelScope.launch { loadInitial() }
    }

    fun onEvent(event: BodyEntryEditUiEvent) {
        when (event) {
            is BodyEntryEditUiEvent.DateChanged -> draft.update { it.copy(date = event.date) }
            is BodyEntryEditUiEvent.TimeChanged -> draft.update { it.copy(time = event.time) }
            is BodyEntryEditUiEvent.IntensityChanged -> draft.update { it.copy(intensity = event.intensity) }
            is BodyEntryEditUiEvent.MeasurementChanged -> draft.update { it.copy(measurementText = event.text) }
            is BodyEntryEditUiEvent.NotesChanged -> draft.update { it.copy(notes = event.text) }
            is BodyEntryEditUiEvent.PhotoSelected -> addPhoto(event.raw)
            is BodyEntryEditUiEvent.RemovePhoto -> removePhoto(event.photoKey)
            BodyEntryEditUiEvent.Save -> save()
            BodyEntryEditUiEvent.ErrorMessageShown -> draft.update { it.copy(errorMessage = null) }
        }
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

    private fun save() {
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
        draft.update { it.copy(pendingPhotos = emptyList(), removedAttachedIds = emptySet()) }
        effectChannel.send(BodyEntryEditEffect.Saved)
    }

    private fun Draft.toUiState(attached: List<MediaAttachment>, vocabulary: BodyVocabulary): BodyEntryEditUiState {
        val visibleAttached = attached.filterNot { it.id.toString() in removedAttachedIds }
        val photos = visibleAttached.map { EntryPhoto.Attached(it) } +
            pendingPhotos.map { EntryPhoto.Pending(it.token, it.file.path) }
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
            errorMessage = errorMessage,
        )
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
        val errorMessage: BodyErrorMessage? = null,
    )

    private data class PendingPhoto(val token: String, val file: File)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
