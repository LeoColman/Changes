// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.colman.changes.R
import br.com.colman.changes.ui.components.DateField
import br.com.colman.changes.ui.components.NumberField

/** Onboarding, sem estado próprio de negócio (Seção 5): "Pular" sempre visível, nada obrigatório. */
@Composable
fun OnboardingScreen(state: OnboardingUiState, onEvent: (OnboardingUiEvent) -> Unit, modifier: Modifier = Modifier) {
    Scaffold(modifier = modifier) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                OnboardingStepContent(state, onEvent)
            }
            OnboardingActions(state, onEvent)
        }
    }
}

@Composable
private fun OnboardingStepContent(state: OnboardingUiState, onEvent: (OnboardingUiEvent) -> Unit) {
    when (state.step) {
        0 -> WelcomeStep()
        1 -> HrtStartDateStep(state, onEvent)
        2 -> HeightStep(state, onEvent)
        else -> VocabularyContent(state.vocabulary, { onEvent(OnboardingUiEvent.VocabularyChanged(it)) })
    }
}

@Composable
private fun OnboardingActions(state: OnboardingUiState, onEvent: (OnboardingUiEvent) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { onEvent(OnboardingUiEvent.Finished) }) {
            Text(stringResource(R.string.settings_onboarding_skip))
        }
        if (state.step < ONBOARDING_LAST_STEP) {
            Button(onClick = { onEvent(OnboardingUiEvent.NextStep) }) {
                Text(stringResource(R.string.settings_onboarding_next))
            }
        } else {
            Button(onClick = { onEvent(OnboardingUiEvent.Finished) }) {
                Text(stringResource(R.string.settings_onboarding_finish))
            }
        }
    }
}

@Composable
private fun WelcomeStep() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.settings_onboarding_welcome_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.sensitive_disclaimer))
        Text(stringResource(R.string.privacy_offline_notice), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun HrtStartDateStep(state: OnboardingUiState, onEvent: (OnboardingUiEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.settings_profile_hrt_start_date), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.settings_onboarding_start_date_body))
        DateField(
            date = state.hrtStartDate,
            onDateChange = { onEvent(OnboardingUiEvent.HrtStartDateChanged(it)) },
            label = stringResource(R.string.settings_profile_hrt_start_date),
        )
    }
}

@Composable
private fun HeightStep(state: OnboardingUiState, onEvent: (OnboardingUiEvent) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.settings_onboarding_height_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.settings_onboarding_height_body))
        NumberField(
            value = state.heightText,
            onValueChange = { onEvent(OnboardingUiEvent.HeightChanged(it)) },
            label = stringResource(R.string.settings_profile_height_cm),
        )
        NumberField(
            value = state.weightText,
            onValueChange = { onEvent(OnboardingUiEvent.WeightChanged(it)) },
            label = stringResource(R.string.settings_onboarding_weight_label),
            supportingText = if (state.weightError) stringResource(R.string.settings_onboarding_weight_error) else null,
            isError = state.weightError,
        )
        ToggleRow(
            label = stringResource(R.string.settings_profile_show_bmi, stringResource(R.string.bmi_label)),
            checked = state.showBmi,
            onCheckedChange = { onEvent(OnboardingUiEvent.ShowBmiChanged(it)) },
            enabled = state.heightText.isNotBlank(),
        )
    }
}
