// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.settings

import app.cash.turbine.test
import br.com.colman.changes.core.data.MeasurementRepository
import br.com.colman.changes.core.data.ProfileRepository
import br.com.colman.changes.core.model.BodyRegion
import br.com.colman.changes.core.model.MeasurementType
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.FixedTimeZoneProvider
import br.com.colman.changes.core.testing.MainDispatcherListener
import br.com.colman.changes.core.testing.TestZones
import br.com.colman.changes.core.testing.testDatabase
import br.com.colman.changes.core.testing.testLabels
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.LocalDate

private class OnboardingFixture {
    val io = UnconfinedTestDispatcher()
    val clock = FixedClock()
    val zones = FixedTimeZoneProvider(TestZones.SAO_PAULO)
    val database = testDatabase(clock)
    val profiles = ProfileRepository(database, io, clock)
    val measurements = MeasurementRepository(database, io, clock, zones, profiles)
    val settingsStore = FakeSettingsStore()
    val viewModel = OnboardingViewModel(profiles, testLabels, settingsStore, measurements, clock)
}

class OnboardingViewModelSpec : FunSpec({
    register(MainDispatcherListener())

    test("o onboarding começa no primeiro passo, sem nada preenchido") {
        val fixture = OnboardingFixture()

        fixture.viewModel.state.test {
            val state = awaitItem()
            state.step shouldBe 0
            state.hrtStartDate.shouldBeNull()
            state.heightText shouldBe ""
        }
    }

    test("avançar não passa do último passo") {
        val fixture = OnboardingFixture()

        repeat(ONBOARDING_LAST_STEP + 2) { fixture.viewModel.onEvent(OnboardingUiEvent.NextStep) }

        fixture.viewModel.state.test { awaitItem().step shouldBe ONBOARDING_LAST_STEP }
    }

    test("critério: pular no primeiro passo marca onboardingDone sem gravar nada no perfil") {
        val fixture = OnboardingFixture()

        fixture.viewModel.onEvent(OnboardingUiEvent.Finished)

        fixture.settingsStore.settings.value.onboardingDone shouldBe true
        fixture.profiles.observe().test {
            val profile = awaitItem()
            profile.hrtStartDate.shouldBeNull()
            profile.heightCm.shouldBeNull()
            profile.showBmi shouldBe false
            profile.bodyVocabulary.choices.shouldBeEmpty()
        }
    }

    test("critério: pular chama o efeito de terminar") {
        val fixture = OnboardingFixture()

        fixture.viewModel.effects.test {
            fixture.viewModel.onEvent(OnboardingUiEvent.Finished)
            awaitItem() shouldBe OnboardingEffect.Finished
        }
    }

    test("critério: preencher a data de início grava só esse campo") {
        val fixture = OnboardingFixture()
        val date = LocalDate(2024, 3, 1)

        fixture.viewModel.onEvent(OnboardingUiEvent.HrtStartDateChanged(date))
        fixture.viewModel.onEvent(OnboardingUiEvent.Finished)

        fixture.profiles.observe().test {
            val profile = awaitItem()
            profile.hrtStartDate shouldBe date
            profile.heightCm.shouldBeNull()
        }
    }

    test("critério: preencher altura e mostrar IMC grava os dois campos") {
        val fixture = OnboardingFixture()

        fixture.viewModel.onEvent(OnboardingUiEvent.HeightChanged("180"))
        fixture.viewModel.onEvent(OnboardingUiEvent.ShowBmiChanged(true))
        fixture.viewModel.onEvent(OnboardingUiEvent.Finished)

        fixture.profiles.observe().test {
            val profile = awaitItem()
            profile.heightCm shouldBe 180.0
            profile.showBmi shouldBe true
        }
    }

    test("critério: mostrar IMC sem altura não é gravado") {
        val fixture = OnboardingFixture()

        fixture.viewModel.onEvent(OnboardingUiEvent.ShowBmiChanged(true))
        fixture.viewModel.onEvent(OnboardingUiEvent.Finished)

        fixture.profiles.observe().test { awaitItem().showBmi shouldBe false }
    }

    test("escolher um termo de vocabulário no passo 4 grava só o vocabulário") {
        val fixture = OnboardingFixture()
        val event = VocabularyUiEvent.OptionSelected(BodyRegion.CHEST, "CHEST")

        fixture.viewModel.onEvent(OnboardingUiEvent.VocabularyChanged(event))
        fixture.viewModel.onEvent(OnboardingUiEvent.Finished)

        fixture.profiles.observe().test {
            val profile = awaitItem()
            profile.bodyVocabulary.choices shouldContainKey BodyRegion.CHEST
            profile.hrtStartDate.shouldBeNull()
        }
    }

    test("critério: preencher o peso atual no passo 2 grava uma medida de peso em kg") {
        val fixture = OnboardingFixture()

        fixture.viewModel.onEvent(OnboardingUiEvent.WeightChanged("70"))
        fixture.viewModel.onEvent(OnboardingUiEvent.Finished)

        fixture.measurements.observeAll().test {
            val entries = awaitItem()
            entries shouldHaveSize 1
            entries.single().type shouldBe MeasurementType.WEIGHT
            entries.single().value shouldBe 70.0
        }
    }

    test("o peso do onboarding não depende de \"mostrar IMC\"") {
        val fixture = OnboardingFixture()

        fixture.viewModel.onEvent(OnboardingUiEvent.WeightChanged("70"))
        fixture.viewModel.onEvent(OnboardingUiEvent.Finished)

        fixture.profiles.observe().test { awaitItem().showBmi shouldBe false }
        fixture.measurements.observeAll().test { awaitItem() shouldHaveSize 1 }
    }

    test("critério: pular sem preencher o peso não grava nenhuma medida") {
        val fixture = OnboardingFixture()

        fixture.viewModel.onEvent(OnboardingUiEvent.Finished)

        fixture.measurements.observeAll().test { awaitItem().shouldBeEmpty() }
    }

    test("critério: peso inválido marca erro no campo, não grava, e não bloqueia Concluir") {
        val fixture = OnboardingFixture()

        fixture.viewModel.effects.test {
            fixture.viewModel.onEvent(OnboardingUiEvent.WeightChanged("abc"))
            fixture.viewModel.onEvent(OnboardingUiEvent.Finished)
            awaitItem() shouldBe OnboardingEffect.Finished
        }
        fixture.viewModel.state.test { awaitItem().weightError shouldBe true }
        fixture.measurements.observeAll().test { awaitItem().shouldBeEmpty() }
    }

    test("peso inválido mostra o erro enquanto se digita, e o erro some ao corrigir ou apagar") {
        val fixture = OnboardingFixture()

        fixture.viewModel.onEvent(OnboardingUiEvent.WeightChanged("0"))
        fixture.viewModel.state.test { awaitItem().weightError shouldBe true }
        fixture.viewModel.onEvent(OnboardingUiEvent.WeightChanged("70,5"))
        fixture.viewModel.state.test { awaitItem().weightError shouldBe false }
        fixture.viewModel.onEvent(OnboardingUiEvent.WeightChanged("abc"))
        fixture.viewModel.state.test { awaitItem().weightError shouldBe true }
        fixture.viewModel.onEvent(OnboardingUiEvent.WeightChanged(""))
        fixture.viewModel.state.test { awaitItem().weightError shouldBe false }
    }
})
