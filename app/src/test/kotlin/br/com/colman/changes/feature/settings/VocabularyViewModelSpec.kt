// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.settings

import app.cash.turbine.test
import br.com.colman.changes.core.data.ProfileRepository
import br.com.colman.changes.core.model.BodyRegion
import br.com.colman.changes.core.model.VocabularyChoice
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.MainDispatcherListener
import br.com.colman.changes.core.testing.testDatabase
import br.com.colman.changes.core.testing.testLabels
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher

private class VocabularyFixture {
    val io = UnconfinedTestDispatcher()
    val clock = FixedClock()
    val database = testDatabase(clock)
    val profiles = ProfileRepository(database, io, clock)
    val viewModel = VocabularyViewModel(profiles, testLabels)
}

/** Fixa o locale em pt-BR: sem isso, a prévia dependeria do locale default da JVM que roda o teste. */
private suspend fun vocabularyFixture(): VocabularyFixture {
    val fixture = VocabularyFixture()
    fixture.profiles.update { it.copy(locale = "pt-BR") }
    return fixture
}

private fun VocabularyUiState.region(region: BodyRegion) = regions.first { it.region == region }

class VocabularyViewModelSpec : FunSpec({
    register(MainDispatcherListener())

    test("o estado default mostra a prévia com o termo neutro de cada região") {
        val fixture = vocabularyFixture()

        fixture.viewModel.state.test {
            val state = awaitItem()
            state.region(BodyRegion.GENITAL).selectedOption shouldBe BodyRegion.GENITAL.defaultOption
            state.region(BodyRegion.GENITAL).previewLabel shouldBe "Crescimento genital"
        }
    }

    test("critério: escolher um preset grava o vocabulário e muda a prévia") {
        val fixture = vocabularyFixture()

        fixture.viewModel.onEvent(VocabularyUiEvent.OptionSelected(BodyRegion.GENITAL, "DICK"))

        fixture.viewModel.state.test {
            val region = awaitItem().region(BodyRegion.GENITAL)
            region.selectedOption shouldBe "DICK"
            region.previewLabel shouldBe "Crescimento do dick"
        }
        fixture.profiles.observe().test {
            awaitItem().bodyVocabulary.choices[BodyRegion.GENITAL] shouldBe VocabularyChoice.Preset("DICK")
        }
    }

    test("critério: escolher termo livre por região grava o vocabulário e muda a prévia") {
        val fixture = vocabularyFixture()

        fixture.viewModel.onEvent(VocabularyUiEvent.CustomSelected(BodyRegion.GENITAL))
        fixture.viewModel.onEvent(VocabularyUiEvent.CustomTextChanged(BodyRegion.GENITAL, "meu pau"))

        fixture.viewModel.state.test {
            val region = awaitItem().region(BodyRegion.GENITAL)
            region.isCustomSelected shouldBe true
            region.customText shouldBe "meu pau"
            region.previewLabel shouldBe "Crescimento: meu pau"
        }
    }

    test("critério: termo livre em branco volta ao padrão neutro na prévia") {
        val fixture = vocabularyFixture()
        fixture.viewModel.onEvent(VocabularyUiEvent.CustomSelected(BodyRegion.GENITAL))
        fixture.viewModel.onEvent(VocabularyUiEvent.CustomTextChanged(BodyRegion.GENITAL, "meu pau"))

        fixture.viewModel.onEvent(VocabularyUiEvent.CustomTextChanged(BodyRegion.GENITAL, ""))

        fixture.viewModel.state.test {
            val region = awaitItem().region(BodyRegion.GENITAL)
            region.customText shouldBe ""
            region.previewLabel shouldBe "Crescimento genital"
        }
    }

    test("escolher vocabulário de uma região não muda a prévia das outras") {
        val fixture = vocabularyFixture()

        fixture.viewModel.onEvent(VocabularyUiEvent.OptionSelected(BodyRegion.GENITAL, "DICK"))

        fixture.viewModel.state.test {
            val state = awaitItem()
            state.region(BodyRegion.CHEST).previewLabel shouldBe "Sensibilidade no tórax"
        }
    }
})
