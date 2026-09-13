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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.Uuid

/** Tela inicial da aba Corpo: tipos visíveis agrupados por categoria (Seção 7.3, item 1). */
@Immutable
data class BodyHomeUiState(
    val isLoading: Boolean = true,
    val sections: List<BodyCategorySection> = emptyList(),
    val newTypeDialog: NewTypeDialogState? = null,
    val errorMessage: BodyErrorMessage? = null,
)

/** Rascunho do diálogo "Novo tipo": nome livre, categoria, unidade de medida opcional. */
@Immutable
data class NewTypeDialogState(
    val name: String = "",
    val category: BodyChangeCategory = BodyChangeCategory.OTHER,
    val measurementUnit: BodyMeasurementUnit? = null,
    val nameError: Boolean = false,
)

sealed interface BodyHomeUiEvent {
    data object OpenNewTypeDialog : BodyHomeUiEvent

    data object DismissNewTypeDialog : BodyHomeUiEvent

    data class NewTypeNameChanged(val name: String) : BodyHomeUiEvent

    data class NewTypeCategoryChanged(val category: BodyChangeCategory) : BodyHomeUiEvent

    data class NewTypeMeasurementUnitChanged(val unit: BodyMeasurementUnit?) : BodyHomeUiEvent

    data object ConfirmNewType : BodyHomeUiEvent

    data object ErrorMessageShown : BodyHomeUiEvent
}

class BodyHomeViewModel(
    private val bodyChangeRepository: BodyChangeRepository,
    private val mediaRepository: MediaRepository,
    private val bodyLabels: BodyLabels,
) : ViewModel() {

    private val dialog = MutableStateFlow<NewTypeDialogState?>(null)
    private val errorMessage = MutableStateFlow<BodyErrorMessage?>(null)

    val state: StateFlow<BodyHomeUiState> = combine(
        bodyChangeRepository.observeVisibleTypes(),
        bodyChangeRepository.observeAllEntries(),
        bodyLabels.observeVocabulary(),
        dialog,
        errorMessage,
    ) { types, entries, vocabulary, dialogState, error ->
        BodyHomeUiState(
            isLoading = false,
            sections = buildSections(types, entries, vocabulary),
            newTypeDialog = dialogState,
            errorMessage = error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), BodyHomeUiState())

    fun onEvent(event: BodyHomeUiEvent) {
        when (event) {
            BodyHomeUiEvent.OpenNewTypeDialog -> dialog.value = NewTypeDialogState()
            BodyHomeUiEvent.DismissNewTypeDialog -> dialog.value = null
            is BodyHomeUiEvent.NewTypeNameChanged -> dialog.update { it?.copy(name = event.name, nameError = false) }
            is BodyHomeUiEvent.NewTypeCategoryChanged -> dialog.update { it?.copy(category = event.category) }
            is BodyHomeUiEvent.NewTypeMeasurementUnitChanged -> dialog.update { it?.copy(measurementUnit = event.unit) }
            BodyHomeUiEvent.ConfirmNewType -> confirmNewType()
            BodyHomeUiEvent.ErrorMessageShown -> errorMessage.value = null
        }
    }

    private fun confirmNewType() {
        val current = dialog.value ?: return
        if (current.name.isBlank()) {
            dialog.update { it?.copy(nameError = true) }
            return
        }
        viewModelScope.launch {
            val result = bodyChangeRepository.createCustomType(
                current.name.trim(),
                current.category,
                current.measurementUnit,
            )
            when (result) {
                is Result.Success -> dialog.value = null
                is Result.Failure -> errorMessage.value = result.error.toBodyErrorMessage()
            }
        }
    }

    private suspend fun buildSections(
        types: List<BodyChangeType>,
        entries: List<BodyChangeEntry>,
        vocabulary: BodyVocabulary,
    ): List<BodyCategorySection> {
        val entriesByType = entries.groupBy { it.changeTypeId }
        val summaries = types.associateWith { summaryFor(it, entriesByType[it.id].orEmpty(), vocabulary) }
        return BodyChangeCategory.entries
            .map { category -> sectionFor(category, types, summaries) }
            .filter { it.types.isNotEmpty() }
    }

    private fun sectionFor(
        category: BodyChangeCategory,
        types: List<BodyChangeType>,
        summaries: Map<BodyChangeType, BodyTypeSummary>,
    ): BodyCategorySection {
        val inCategory = types.filter { it.category == category }.map { requireNotNull(summaries[it]) }
        return BodyCategorySection(category, inCategory)
    }

    private suspend fun summaryFor(
        type: BodyChangeType,
        typeEntries: List<BodyChangeEntry>,
        vocabulary: BodyVocabulary,
    ): BodyTypeSummary {
        val last = typeEntries.maxByOrNull { it.observedAt.instant }
        return BodyTypeSummary(
            typeId = type.id.toString(),
            label = bodyLabels.label(type, vocabulary),
            lastObservedAt = last?.observedAt,
            lastIntensity = last?.intensity,
            lastPhoto = last?.let { firstPhoto(it.id) },
        )
    }

    private suspend fun firstPhoto(entryId: Uuid) =
        mediaRepository.observeByOwner(MediaOwnerType.BODY_CHANGE_ENTRY, entryId).first().firstOrNull()

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
