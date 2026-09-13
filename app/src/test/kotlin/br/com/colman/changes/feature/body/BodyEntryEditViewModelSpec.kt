// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import br.com.colman.changes.core.model.BodyMeasurementUnit
import br.com.colman.changes.core.model.Intensity
import br.com.colman.changes.core.model.MediaOwnerType
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.core.testing.MainDispatcherListener
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import java.io.File
import java.nio.file.Files

private class FakePhotoIntake : BodyPhotoIntake {
    override suspend fun sanitize(raw: RawBodyPhoto): File = (raw as RawBodyPhoto.Camera).file
}

class BodyEntryEditViewModelSpec : FunSpec({
    extension(MainDispatcherListener())
    extension(PtBrDefaultLocale())

    val pastDate = LocalDate(2026, 9, 10)
    val pastTime = LocalTime(10, 0)
    val futureDate = LocalDate(2026, 9, 15)

    test("aceite 7.3: medida só é aceita quando o tipo suporta") {
        val env = BodyTestEnvironment()
        val type = env.typeByCode("SKIN_OILINESS_ACNE")
        val viewModel = BodyEntryEditViewModel(
            env.bodyChangeRepository,
            env.mediaRepository,
            env.bodyLabels,
            FakePhotoIntake(),
            env.timeZones,
            BodyEntryEditArgs(typeId = type.id.toString(), entryId = null),
        )

        viewModel.state.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.onEvent(BodyEntryEditUiEvent.DateChanged(pastDate))
            viewModel.onEvent(BodyEntryEditUiEvent.TimeChanged(pastTime))
            viewModel.onEvent(BodyEntryEditUiEvent.MeasurementChanged("5"))
            viewModel.onEvent(BodyEntryEditUiEvent.Save)

            var afterSave = awaitItem()
            while (afterSave.errorMessage == null) afterSave = awaitItem()
            afterSave.errorMessage shouldBe BodyErrorMessage.MEASUREMENT_UNSUPPORTED
            cancelAndIgnoreRemainingEvents()
        }
        env.bodyChangeRepository.observeAllEntries().first().shouldBeEmpty()
    }

    test("aceite 7.3: uma data futura é rejeitada") {
        val env = BodyTestEnvironment()
        val type = env.typeByCode("SKIN_OILINESS_ACNE")
        val viewModel = BodyEntryEditViewModel(
            env.bodyChangeRepository,
            env.mediaRepository,
            env.bodyLabels,
            FakePhotoIntake(),
            env.timeZones,
            BodyEntryEditArgs(typeId = type.id.toString(), entryId = null),
        )

        viewModel.state.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()

            viewModel.onEvent(BodyEntryEditUiEvent.DateChanged(futureDate))
            viewModel.onEvent(BodyEntryEditUiEvent.TimeChanged(pastTime))
            viewModel.onEvent(BodyEntryEditUiEvent.Save)

            var afterSave = awaitItem()
            while (afterSave.errorMessage == null) afterSave = awaitItem()
            afterSave.errorMessage shouldBe BodyErrorMessage.FUTURE_DATE
            cancelAndIgnoreRemainingEvents()
        }
        env.bodyChangeRepository.observeAllEntries().first().shouldBeEmpty()
    }

    test("cria uma entrada com medida quando o tipo suporta") {
        val env = BodyTestEnvironment()
        val type = env.typeByCode("CLITORAL_ENLARGEMENT")
        val viewModel = BodyEntryEditViewModel(
            env.bodyChangeRepository,
            env.mediaRepository,
            env.bodyLabels,
            FakePhotoIntake(),
            env.timeZones,
            BodyEntryEditArgs(typeId = type.id.toString(), entryId = null),
        )

        turbineScope {
            val stateTurbine = viewModel.state.testIn(this)
            val effects = viewModel.effects.testIn(this)

            var state = stateTurbine.awaitItem()
            while (state.isLoading) state = stateTurbine.awaitItem()
            state.supportsMeasurement shouldBe true
            state.measurementUnit shouldBe BodyMeasurementUnit.CM

            viewModel.onEvent(BodyEntryEditUiEvent.DateChanged(pastDate))
            viewModel.onEvent(BodyEntryEditUiEvent.TimeChanged(pastTime))
            viewModel.onEvent(BodyEntryEditUiEvent.IntensityChanged(Intensity.MILD))
            viewModel.onEvent(BodyEntryEditUiEvent.MeasurementChanged("5"))
            viewModel.onEvent(BodyEntryEditUiEvent.Save)

            effects.awaitItem() shouldBe BodyEntryEditEffect.Saved
            stateTurbine.cancelAndIgnoreRemainingEvents()
            effects.cancelAndIgnoreRemainingEvents()
        }

        val entries = env.bodyChangeRepository.observeAllEntries().first()
        entries.size shouldBe 1
        entries.first().measurementValue shouldBe 5.0
        entries.first().measurementUnit shouldBe BodyMeasurementUnit.CM
        entries.first().intensity shouldBe Intensity.MILD
    }

    test("aceite: foto entra pendente, é anexada ao salvar, e o arquivo temporário é apagado") {
        val env = BodyTestEnvironment()
        val type = env.typeByCode("SKIN_OILINESS_ACNE")
        val tempFile = Files.createTempFile("body-test-photo", ".jpg").toFile()
        tempFile.writeBytes(fakePhotoBytes())
        val viewModel = BodyEntryEditViewModel(
            env.bodyChangeRepository,
            env.mediaRepository,
            env.bodyLabels,
            FakePhotoIntake(),
            env.timeZones,
            BodyEntryEditArgs(typeId = type.id.toString(), entryId = null),
        )

        turbineScope {
            val stateTurbine = viewModel.state.testIn(this)
            val effects = viewModel.effects.testIn(this)

            var state = stateTurbine.awaitItem()
            while (state.isLoading) state = stateTurbine.awaitItem()

            viewModel.onEvent(BodyEntryEditUiEvent.PhotoSelected(RawBodyPhoto.Camera(tempFile)))
            var withPhoto = stateTurbine.awaitItem()
            while (withPhoto.photos.isEmpty()) withPhoto = stateTurbine.awaitItem()
            withPhoto.photos.single().shouldBeInstanceOf<EntryPhoto.Pending>()

            viewModel.onEvent(BodyEntryEditUiEvent.DateChanged(pastDate))
            viewModel.onEvent(BodyEntryEditUiEvent.TimeChanged(pastTime))
            viewModel.onEvent(BodyEntryEditUiEvent.Save)

            effects.awaitItem() shouldBe BodyEntryEditEffect.Saved

            var afterSave = stateTurbine.awaitItem()
            while (afterSave.photos.isEmpty() || afterSave.photos.any { it is EntryPhoto.Pending }) {
                afterSave = stateTurbine.awaitItem()
            }
            afterSave.photos.single().shouldBeInstanceOf<EntryPhoto.Attached>()

            stateTurbine.cancelAndIgnoreRemainingEvents()
            effects.cancelAndIgnoreRemainingEvents()
        }

        tempFile.exists() shouldBe false
        val entry = env.bodyChangeRepository.observeAllEntries().first().single()
        env.mediaRepository.observeByOwner(MediaOwnerType.BODY_CHANGE_ENTRY, entry.id).first().size shouldBe 1
    }

    test("edita uma entrada existente e preenche o formulário com os valores dela") {
        val env = BodyTestEnvironment()
        val type = env.typeByCode("SKIN_OILINESS_ACNE")
        val entry = (
            env.bodyChangeRepository.createEntry(type.id, env.clock.now, Intensity.MODERATE, null, "nota original")
                as Result.Success
            ).value

        val viewModel = BodyEntryEditViewModel(
            env.bodyChangeRepository,
            env.mediaRepository,
            env.bodyLabels,
            FakePhotoIntake(),
            env.timeZones,
            BodyEntryEditArgs(typeId = null, entryId = entry.id.toString()),
        )

        turbineScope {
            val stateTurbine = viewModel.state.testIn(this)
            val effects = viewModel.effects.testIn(this)

            var state = stateTurbine.awaitItem()
            while (state.isLoading) state = stateTurbine.awaitItem()
            state.isNew shouldBe false
            state.notes shouldBe "nota original"
            state.intensity shouldBe Intensity.MODERATE

            viewModel.onEvent(BodyEntryEditUiEvent.NotesChanged("nota editada"))
            viewModel.onEvent(BodyEntryEditUiEvent.Save)

            effects.awaitItem() shouldBe BodyEntryEditEffect.Saved
            stateTurbine.cancelAndIgnoreRemainingEvents()
            effects.cancelAndIgnoreRemainingEvents()
        }

        env.bodyChangeRepository.getEntry(entry.id)?.notes shouldBe "nota editada"
    }
})
