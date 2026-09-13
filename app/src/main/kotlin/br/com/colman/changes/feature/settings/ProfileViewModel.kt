// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.colman.changes.core.data.ProfileRepository
import br.com.colman.changes.core.model.Profile
import br.com.colman.changes.core.model.UnitSystem
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.ui.format.Formatters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * Tela de Perfil (Seção 6.1): cada campo grava sozinho ao mudar, sem botão de salvar. O estado é
 * lido uma vez do repositório e mantido localmente, para o texto digitado não ser sobrescrito por
 * uma nova emissão do repositório enquanto a pessoa ainda está digitando.
 */
class ProfileViewModel(private val profiles: ProfileRepository) : ViewModel() {

    private val mutableState = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            mutableState.value = profiles.observe().first().toUiState()
        }
    }

    fun onEvent(event: ProfileUiEvent) {
        when (event) {
            is ProfileUiEvent.DisplayNameChanged -> changeDisplayName(event.text)
            is ProfileUiEvent.HrtStartDateChanged -> changeHrtStartDate(event.date)
            is ProfileUiEvent.HeightChanged -> changeHeight(event.text)
            is ProfileUiEvent.UnitSystemChanged -> changeUnitSystem(event.unitSystem)
            is ProfileUiEvent.BirthYearChanged -> changeBirthYear(event.text)
            is ProfileUiEvent.ShowBmiChanged -> changeShowBmi(event.enabled)
        }
    }

    private fun changeDisplayName(text: String) {
        mutableState.update { it.copy(displayName = text) }
        persist { it.copy(displayName = text.trim().ifBlank { null }) }
    }

    private fun changeHrtStartDate(date: LocalDate) {
        mutableState.update { it.copy(hrtStartDate = date) }
        persist { it.copy(hrtStartDate = date) }
    }

    /** Altura vazia limpa a altura e desliga o IMC (Seção 7.4.2: sem altura, sem IMC). */
    private fun changeHeight(text: String) {
        mutableState.update { it.copy(heightText = text) }
        val heightCm = parseHeightCm(text, mutableState.value.unitSystem)
        when {
            text.isBlank() -> persist { it.copy(heightCm = null, showBmi = false) }
            heightCm != null -> persist { it.copy(heightCm = heightCm) }
        }
    }

    /** Só muda como a altura é exibida e lida; a altura gravada continua a mesma, em cm. */
    private fun changeUnitSystem(unitSystem: UnitSystem) {
        val currentCm = parseHeightCm(mutableState.value.heightText, mutableState.value.unitSystem)
        val newText = currentCm?.let { formatHeight(it, unitSystem) }.orEmpty()
        mutableState.update { it.copy(unitSystem = unitSystem, heightText = newText) }
        persist { it.copy(unitSystem = unitSystem) }
    }

    private fun changeBirthYear(text: String) {
        mutableState.update { it.copy(birthYearText = text) }
        Formatters.parseNumber(text)?.toInt()?.let { year -> persist { it.copy(birthYear = year) } }
    }

    /** Só liga se já houver altura gravada (Seção 7.4.2), verificada no perfil atual do repositório. */
    private fun changeShowBmi(enabled: Boolean) = persist { it.copy(showBmi = enabled && it.heightCm != null) }

    /** Grava e sincroniza [ProfileUiState.showBmi] de volta: é o único campo que pode ser recusado silenciosamente. */
    private fun persist(transform: (Profile) -> Profile) {
        viewModelScope.launch {
            val profile = profiles.update(transform).getOrNull() ?: return@launch
            mutableState.update { it.copy(showBmi = profile.showBmi) }
        }
    }

    private fun Profile.toUiState(): ProfileUiState = ProfileUiState(
        isLoading = false,
        displayName = displayName.orEmpty(),
        hrtStartDate = hrtStartDate,
        heightText = heightCm?.let { formatHeight(it, unitSystem) }.orEmpty(),
        unitSystem = unitSystem,
        birthYearText = birthYear?.toString().orEmpty(),
        showBmi = showBmi,
    )
}
