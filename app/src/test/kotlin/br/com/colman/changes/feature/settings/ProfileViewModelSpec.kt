// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.settings

import app.cash.turbine.test
import br.com.colman.changes.core.data.ProfileRepository
import br.com.colman.changes.core.model.UnitSystem
import br.com.colman.changes.core.model.Units
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.MainDispatcherListener
import br.com.colman.changes.core.testing.testDatabase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.LocalDate

private const val TOLERANCE = 0.001

private class ProfileFixture {
    val io = UnconfinedTestDispatcher()
    val clock = FixedClock()
    val database = testDatabase(clock)
    val profiles = ProfileRepository(database, io, clock)
    val viewModel = ProfileViewModel(profiles)
}

class ProfileViewModelSpec : FunSpec({
    register(MainDispatcherListener())

    test("o estado carrega o perfil default sem campo obrigatório preenchido") {
        val fixture = ProfileFixture()

        fixture.viewModel.state.test {
            val state = awaitItem()
            state.isLoading shouldBe false
            state.displayName shouldBe ""
            state.hrtStartDate.shouldBeNull()
            state.heightText shouldBe ""
            state.showBmi shouldBe false
        }
    }

    test("nome de exibição em branco grava null") {
        val fixture = ProfileFixture()

        fixture.viewModel.onEvent(ProfileUiEvent.DisplayNameChanged("  "))

        fixture.profiles.observe().test { awaitItem().displayName.shouldBeNull() }
    }

    test("nome de exibição preenchido grava sem espaços nas pontas") {
        val fixture = ProfileFixture()

        fixture.viewModel.onEvent(ProfileUiEvent.DisplayNameChanged("  Sam  "))

        fixture.profiles.observe().test { awaitItem().displayName shouldBe "Sam" }
    }

    test("data de início do tratamento hormonal grava no perfil") {
        val fixture = ProfileFixture()
        val date = LocalDate(2024, 3, 1)

        fixture.viewModel.onEvent(ProfileUiEvent.HrtStartDateChanged(date))

        fixture.profiles.observe().test { awaitItem().hrtStartDate shouldBe date }
    }

    test("critério: altura digitada em polegadas grava em cm") {
        val fixture = ProfileFixture()
        fixture.viewModel.onEvent(ProfileUiEvent.UnitSystemChanged(UnitSystem.IMPERIAL))

        fixture.viewModel.onEvent(ProfileUiEvent.HeightChanged("70"))

        fixture.profiles.observe().test {
            val heightCm = awaitItem().heightCm.shouldNotBeNull()
            heightCm shouldBe (70.0 * Units.CM_PER_INCH plusOrMinus TOLERANCE)
        }
    }

    test("altura digitada em cm grava sem conversão") {
        val fixture = ProfileFixture()

        fixture.viewModel.onEvent(ProfileUiEvent.HeightChanged("180"))

        fixture.profiles.observe().test { awaitItem().heightCm shouldBe (180.0 plusOrMinus TOLERANCE) }
    }

    test("trocar o sistema de unidades converte o texto exibido sem alterar a altura gravada") {
        val fixture = ProfileFixture()
        fixture.viewModel.onEvent(ProfileUiEvent.HeightChanged("180"))

        fixture.viewModel.onEvent(ProfileUiEvent.UnitSystemChanged(UnitSystem.IMPERIAL))

        fixture.viewModel.state.test {
            val state = awaitItem()
            state.unitSystem shouldBe UnitSystem.IMPERIAL
        }
        fixture.profiles.observe().test { awaitItem().heightCm shouldBe (180.0 plusOrMinus TOLERANCE) }
    }

    test("limpar a altura desliga mostrar IMC e apaga a altura gravada") {
        val fixture = ProfileFixture()
        fixture.viewModel.onEvent(ProfileUiEvent.HeightChanged("180"))
        fixture.viewModel.onEvent(ProfileUiEvent.ShowBmiChanged(true))

        fixture.viewModel.onEvent(ProfileUiEvent.HeightChanged(""))

        fixture.profiles.observe().test {
            val profile = awaitItem()
            profile.heightCm.shouldBeNull()
            profile.showBmi shouldBe false
        }
    }

    test("critério: mostrar IMC é persistido quando há altura") {
        val fixture = ProfileFixture()
        fixture.viewModel.onEvent(ProfileUiEvent.HeightChanged("180"))

        fixture.viewModel.onEvent(ProfileUiEvent.ShowBmiChanged(true))

        fixture.profiles.observe().test { awaitItem().showBmi shouldBe true }
        fixture.viewModel.state.test { awaitItem().showBmi shouldBe true }
    }

    test("critério: mostrar IMC não é gravado sem altura") {
        val fixture = ProfileFixture()

        fixture.viewModel.onEvent(ProfileUiEvent.ShowBmiChanged(true))

        fixture.profiles.observe().test { awaitItem().showBmi shouldBe false }
    }

    test("ano de nascimento válido grava no perfil") {
        val fixture = ProfileFixture()

        fixture.viewModel.onEvent(ProfileUiEvent.BirthYearChanged("1990"))

        fixture.profiles.observe().test { awaitItem().birthYear shouldBe 1990 }
    }

    test("ano de nascimento não numérico não grava, mas mantém o texto digitado no estado") {
        val fixture = ProfileFixture()

        fixture.viewModel.onEvent(ProfileUiEvent.BirthYearChanged("abc"))

        fixture.viewModel.state.test { awaitItem().birthYearText shouldBe "abc" }
        fixture.profiles.observe().test { awaitItem().birthYear.shouldBeNull() }
    }
})
