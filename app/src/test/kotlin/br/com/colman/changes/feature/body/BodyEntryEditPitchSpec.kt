// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import app.cash.turbine.turbineScope
import br.com.colman.changes.core.model.BodyChangeCategory
import br.com.colman.changes.core.model.BodyMeasurementUnit
import br.com.colman.changes.core.model.MediaOwnerType
import br.com.colman.changes.core.model.MediaPaths
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.testing.MainDispatcherListener
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** ADR 0014: a frequência da voz estimada da gravação, no formulário da entrada. */
class BodyEntryEditPitchSpec : FunSpec({
    extension(MainDispatcherListener())
    extension(PtBrDefaultLocale())

    val pastDate = LocalDate(2026, 9, 10)
    val pastTime = LocalTime(10, 0)

    fun BodyTestEnvironment.newEntryViewModel(typeId: String) = BodyEntryEditViewModel(
        repositories,
        FakePhotoIntake(),
        timeZones,
        voiceControls,
        BodyEntryEditArgs(typeId = typeId, entryId = null),
    )

    suspend fun ReceiveTurbine<BodyEntryEditUiState>.awaitUntil(
        predicate: (BodyEntryEditUiState) -> Boolean,
    ): BodyEntryEditUiState {
        var state = awaitItem()
        while (!predicate(state)) state = awaitItem()
        return state
    }

    /** Grava e para; devolve o estado com a gravação pendente e a estimativa já terminada. */
    suspend fun ReceiveTurbine<BodyEntryEditUiState>.recordAndStop(
        viewModel: BodyEntryEditViewModel,
    ): BodyEntryEditUiState {
        viewModel.onEvent(BodyEntryEditUiEvent.StartRecordingVoice)
        awaitUntil { it.voiceState is VoiceRecordingUiState.Recording }
        viewModel.onEvent(BodyEntryEditUiEvent.StopRecordingVoice)
        return awaitUntil { it.voiceRecording is EntryVoiceRecording.Pending && !it.isEstimatingPitch }
    }

    test("ADR 0014: parar a gravação estima a frequência, preenche a medida em Hz e salva com ela") {
        val env = BodyTestEnvironment()
        val viewModel = env.newEntryViewModel(env.typeByCode("VOICE_DEEPENING").id.toString())

        turbineScope {
            val states = viewModel.state.testIn(this)
            val effects = viewModel.effects.testIn(this)
            states.awaitUntil { !it.isLoading }.supportsPitchEstimate shouldBe true

            val recorded = states.recordAndStop(viewModel)
            recorded.measurementText shouldBe "142"
            recorded.pitchUnavailable shouldBe false
            recorded.hasUnsavedChanges shouldBe true

            viewModel.onEvent(BodyEntryEditUiEvent.DateChanged(pastDate))
            viewModel.onEvent(BodyEntryEditUiEvent.TimeChanged(pastTime))
            viewModel.onEvent(BodyEntryEditUiEvent.Save)
            effects.awaitItem() shouldBe BodyEntryEditEffect.Saved

            states.cancelAndIgnoreRemainingEvents()
            effects.cancelAndIgnoreRemainingEvents()
        }

        env.voicePitch.analyzedFiles.size shouldBe 1
        val entry = env.bodyChangeRepository.observeAllEntries().first().single()
        entry.measurementValue shouldBe 142.0
        entry.measurementUnit shouldBe BodyMeasurementUnit.HZ
    }

    test("ADR 0014: a estimativa não troca um valor digitado; Estimar troca") {
        val env = BodyTestEnvironment()
        val viewModel = env.newEntryViewModel(env.typeByCode("VOICE_DEEPENING").id.toString())

        viewModel.state.test {
            awaitUntil { !it.isLoading }
            viewModel.onEvent(BodyEntryEditUiEvent.MeasurementChanged("180"))
            awaitUntil { it.measurementText == "180" }

            recordAndStop(viewModel).measurementText shouldBe "180"

            viewModel.onEvent(BodyEntryEditUiEvent.EstimatePitch)
            awaitUntil { it.measurementText == "142" }
            cancelAndIgnoreRemainingEvents()
        }
        env.voicePitch.analyzedFiles.size shouldBe 2
    }

    test("ADR 0014: depois de apagar, uma nova gravação traz a nova estimativa, arredondada ao Hz") {
        val env = BodyTestEnvironment()
        val viewModel = env.newEntryViewModel(env.typeByCode("VOICE_DEEPENING").id.toString())

        viewModel.state.test {
            awaitUntil { !it.isLoading }
            recordAndStop(viewModel).measurementText shouldBe "142"

            env.voicePitch.result = 131.6
            viewModel.onEvent(BodyEntryEditUiEvent.DeleteVoice)
            awaitUntil { it.voiceRecording == null }
            recordAndStop(viewModel).measurementText shouldBe "132"
            cancelAndIgnoreRemainingEvents()
        }
    }

    test("ADR 0014: gravação sem voz suficiente avisa e deixa a medida vazia") {
        val env = BodyTestEnvironment()
        env.voicePitch.result = null
        val viewModel = env.newEntryViewModel(env.typeByCode("VOICE_DEEPENING").id.toString())

        viewModel.state.test {
            awaitUntil { !it.isLoading }
            val recorded = recordAndStop(viewModel)
            recorded.pitchUnavailable shouldBe true
            recorded.measurementText shouldBe ""

            env.voicePitch.result = 142.4
            viewModel.onEvent(BodyEntryEditUiEvent.EstimatePitch)
            val estimated = awaitUntil { it.measurementText == "142" }
            estimated.pitchUnavailable shouldBe false
            cancelAndIgnoreRemainingEvents()
        }
    }

    test("ADR 0014: apagar a gravação leva a medida estimada, mas não uma corrigida à mão") {
        val env = BodyTestEnvironment()
        val viewModel = env.newEntryViewModel(env.typeByCode("VOICE_DEEPENING").id.toString())

        viewModel.state.test {
            awaitUntil { !it.isLoading }
            recordAndStop(viewModel).measurementText shouldBe "142"
            viewModel.onEvent(BodyEntryEditUiEvent.DeleteVoice)
            awaitUntil { it.voiceRecording == null }.measurementText shouldBe ""

            recordAndStop(viewModel).measurementText shouldBe "142"
            viewModel.onEvent(BodyEntryEditUiEvent.MeasurementChanged("150"))
            awaitUntil { it.measurementText == "150" }
            viewModel.onEvent(BodyEntryEditUiEvent.DeleteVoice)
            awaitUntil { it.voiceRecording == null }.measurementText shouldBe "150"
            cancelAndIgnoreRemainingEvents()
        }
    }

    test("ADR 0014: numa entrada antiga, Estimar usa a gravação anexada, e abrir não estima sozinho") {
        val env = BodyTestEnvironment()
        val type = env.typeByCode("VOICE_DEEPENING")
        val entry = (
            env.bodyChangeRepository.createEntry(type.id, env.clock.now, null, null, null) as Result.Success
            ).value
        val voice = (
            env.mediaRepository.attach(
                MediaOwnerType.BODY_CHANGE_ENTRY,
                entry.id,
                fakePhotoBytes().inputStream(),
                MediaPaths.VOICE_MIME_TYPE,
                null,
            ) as Result.Success
            ).value
        val viewModel = BodyEntryEditViewModel(
            env.repositories,
            FakePhotoIntake(),
            env.timeZones,
            env.voiceControls,
            BodyEntryEditArgs(typeId = null, entryId = entry.id.toString()),
        )

        viewModel.state.test {
            val loaded = awaitUntil { it.voiceRecording is EntryVoiceRecording.Attached }
            loaded.measurementText shouldBe ""
            loaded.hasUnsavedChanges shouldBe false
            env.voicePitch.analyzedMedia.shouldBeEmpty()

            viewModel.onEvent(BodyEntryEditUiEvent.EstimatePitch)
            awaitUntil { it.measurementText == "142" }.hasUnsavedChanges shouldBe true
            cancelAndIgnoreRemainingEvents()
        }
        env.voicePitch.analyzedMedia shouldBe listOf(voice.relativePath)
    }

    test("ADR 0014: salvar logo depois de parar a gravação espera a estimativa") {
        val env = BodyTestEnvironment()
        val gate = CompletableDeferred<Unit>()
        env.voicePitch.gate = gate
        val viewModel = env.newEntryViewModel(env.typeByCode("VOICE_DEEPENING").id.toString())

        turbineScope {
            val states = viewModel.state.testIn(this)
            val effects = viewModel.effects.testIn(this)
            states.awaitUntil { !it.isLoading }

            viewModel.onEvent(BodyEntryEditUiEvent.StartRecordingVoice)
            states.awaitUntil { it.voiceState is VoiceRecordingUiState.Recording }
            viewModel.onEvent(BodyEntryEditUiEvent.StopRecordingVoice)
            states.awaitUntil { it.isEstimatingPitch }
            viewModel.onEvent(BodyEntryEditUiEvent.DateChanged(pastDate))
            viewModel.onEvent(BodyEntryEditUiEvent.TimeChanged(pastTime))
            viewModel.onEvent(BodyEntryEditUiEvent.Save)
            effects.expectNoEvents()

            gate.complete(Unit)
            effects.awaitItem() shouldBe BodyEntryEditEffect.Saved
            states.cancelAndIgnoreRemainingEvents()
            effects.cancelAndIgnoreRemainingEvents()
        }

        env.bodyChangeRepository.observeAllEntries().first().single().measurementValue shouldBe 142.0
    }

    test("ADR 0014: um tipo de voz sem medida em Hz grava, mas não estima") {
        val env = BodyTestEnvironment()
        val custom = (
            env.bodyChangeRepository.createCustomType("Rouquidão", BodyChangeCategory.VOICE, null) as Result.Success
            ).value
        val viewModel = env.newEntryViewModel(custom.id.toString())

        viewModel.state.test {
            val loaded = awaitUntil { !it.isLoading }
            loaded.supportsVoiceRecording shouldBe true
            loaded.supportsPitchEstimate shouldBe false

            viewModel.onEvent(BodyEntryEditUiEvent.StartRecordingVoice)
            awaitUntil { it.voiceState is VoiceRecordingUiState.Recording }
            viewModel.onEvent(BodyEntryEditUiEvent.StopRecordingVoice)
            val recorded = awaitUntil { it.voiceRecording is EntryVoiceRecording.Pending }
            recorded.isEstimatingPitch shouldBe false
            viewModel.onEvent(BodyEntryEditUiEvent.EstimatePitch)
            cancelAndIgnoreRemainingEvents()
        }
        env.voicePitch.analyzedFiles.shouldBeEmpty()
    }
})
