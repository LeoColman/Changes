// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.vitals

import app.cash.turbine.test
import br.com.colman.changes.core.data.MeasurementRepository
import br.com.colman.changes.core.data.ProfileRepository
import br.com.colman.changes.core.model.MeasurementType
import br.com.colman.changes.core.model.MeasurementUnit
import br.com.colman.changes.core.model.UnitSystem
import br.com.colman.changes.core.model.Units
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.FixedTimeZoneProvider
import br.com.colman.changes.core.testing.MainDispatcherListener
import br.com.colman.changes.core.testing.TestZones
import br.com.colman.changes.core.testing.testDatabase
import br.com.colman.changes.core.testing.testLabels
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher

private const val TOLERANCE = 0.001

/** Instala peso, perfil e o ViewModel sobre um banco novo, em memória. */
private class MeasurementsFixture {
    val io = UnconfinedTestDispatcher()
    val clock = FixedClock()
    val zones = FixedTimeZoneProvider(TestZones.SAO_PAULO)
    val database = testDatabase(clock)
    val profiles = ProfileRepository(database, io, clock)
    val measurements = MeasurementRepository(database, io, clock, zones, profiles)
    val viewModel = MeasurementsViewModel(measurements, profiles, testLabels, clock, zones)
}

class MeasurementsViewModelSpec : FunSpec({
    register(MainDispatcherListener())

    test("critério 7.4.1: show_bmi = false tira o IMC do estado da tela") {
        val fixture = MeasurementsFixture()
        fixture.profiles.update { it.copy(showBmi = false, heightCm = 180.0) }
        fixture.measurements.create(MeasurementType.WEIGHT, null, 80.0, MeasurementUnit.KG, fixture.clock.now, null)

        fixture.viewModel.state.test { awaitItem().bmi.shouldBeNull() }
    }

    test("critério 7.4.2: sem altura, sem IMC e com o pedido de altura") {
        val fixture = MeasurementsFixture()
        fixture.profiles.update { it.copy(showBmi = true, heightCm = null) }
        fixture.measurements.create(MeasurementType.WEIGHT, null, 80.0, MeasurementUnit.KG, fixture.clock.now, null)

        fixture.viewModel.state.test { awaitItem().bmi shouldBe BmiUiState.NeedsHeight }
    }

    test("critério 7.4.2: com altura e IMC ligado mas sem peso, o pedido é de peso") {
        val fixture = MeasurementsFixture()
        fixture.profiles.update { it.copy(showBmi = true, heightCm = 180.0) }

        fixture.viewModel.state.test { awaitItem().bmi shouldBe BmiUiState.NeedsWeight }
    }

    test("registrar peso pelo botão do cartão de IMC muda NeedsWeight para Value") {
        val fixture = MeasurementsFixture()
        fixture.profiles.update { it.copy(showBmi = true, heightCm = 200.0) }

        fixture.viewModel.state.test {
            awaitItem().bmi shouldBe BmiUiState.NeedsWeight

            fixture.viewModel.onEvent(MeasurementsUiEvent.AddRequested(MeasurementType.WEIGHT))
            fixture.viewModel.onEvent(MeasurementsUiEvent.FormValueChanged("100"))
            fixture.viewModel.onEvent(MeasurementsUiEvent.FormSaved)

            val bmi = expectMostRecentItem().bmi.shouldNotBeNull()
            (bmi as BmiUiState.Value).value shouldBe (25.0 plusOrMinus TOLERANCE)
        }
    }

    test("IMC aparece como número puro quando há altura, peso e show_bmi ligado") {
        val fixture = MeasurementsFixture()
        fixture.profiles.update { it.copy(showBmi = true, heightCm = 200.0) }
        fixture.measurements.create(MeasurementType.WEIGHT, null, 100.0, MeasurementUnit.KG, fixture.clock.now, null)

        fixture.viewModel.state.test {
            val bmi = awaitItem().bmi.shouldNotBeNull()
            (bmi as BmiUiState.Value).value shouldBe (25.0 plusOrMinus TOLERANCE)
        }
    }

    test("conversão para o sistema imperial na exibição sem alterar o valor gravado") {
        val fixture = MeasurementsFixture()
        fixture.measurements.create(MeasurementType.WEIGHT, null, 80.0, MeasurementUnit.KG, fixture.clock.now, null)
        fixture.profiles.update { it.copy(unitSystem = UnitSystem.IMPERIAL) }

        fixture.viewModel.state.test {
            val state = awaitItem()
            val weight = state.sections.first { it.type == MeasurementType.WEIGHT }
            val current = weight.current.shouldNotBeNull()
            current.unit shouldBe MeasurementUnit.LB
            current.value shouldBe (80.0 / Units.KG_PER_LB plusOrMinus TOLERANCE)
        }

        val stored = fixture.database.measurementQueries.selectAll().executeAsList().single()
        stored.unit shouldBe "KG"
        stored.value_ shouldBe 80.0
    }

    test("resumo acessível do gráfico: os pontos do gráfico acompanham os registros do tipo") {
        val fixture = MeasurementsFixture()
        fixture.measurements.create(MeasurementType.WAIST, null, 80.0, MeasurementUnit.CM, fixture.clock.now, null)
        fixture.measurements.create(MeasurementType.WAIST, null, 79.0, MeasurementUnit.CM, fixture.clock.now, null)

        fixture.viewModel.state.test {
            val waist = awaitItem().sections.first { it.type == MeasurementType.WAIST }
            waist.entries shouldHaveSize 2
            waist.chartPoints shouldHaveSize waist.entries.size
        }
    }

    test("adicionar uma medida pelo formulário grava e limpa o formulário") {
        val fixture = MeasurementsFixture()

        fixture.viewModel.state.test {
            awaitItem()
            fixture.viewModel.onEvent(MeasurementsUiEvent.AddRequested(MeasurementType.WAIST))
            fixture.viewModel.onEvent(MeasurementsUiEvent.FormValueChanged("81.5"))
            fixture.viewModel.onEvent(MeasurementsUiEvent.FormSaved)

            val saved = expectMostRecentItem()
            saved.form.shouldBeNull()
            val waist = saved.sections.first { it.type == MeasurementType.WAIST }
            waist.entries shouldHaveSize 1
            waist.current!!.value shouldBe (81.5 plusOrMinus TOLERANCE)
        }
    }

    test("editar uma medida existente atualiza o mesmo registro, sem duplicar") {
        val fixture = MeasurementsFixture()
        val created = fixture.measurements.create(
            MeasurementType.WEIGHT,
            null,
            80.0,
            MeasurementUnit.KG,
            fixture.clock.now,
            null,
        ).getOrNull().shouldNotBeNull()

        fixture.viewModel.state.test {
            awaitItem()
            fixture.viewModel.onEvent(MeasurementsUiEvent.EditRequested(created.id))
            fixture.viewModel.onEvent(MeasurementsUiEvent.FormValueChanged("79"))
            fixture.viewModel.onEvent(MeasurementsUiEvent.FormSaved)

            val saved = expectMostRecentItem()
            saved.form.shouldBeNull()
            val weight = saved.sections.first { it.type == MeasurementType.WEIGHT }
            weight.entries shouldHaveSize 1
            weight.current!!.value shouldBe (79.0 plusOrMinus TOLERANCE)
        }
    }

    test("valor inválido no formulário mantém o formulário aberto com erro, sem gravar") {
        val fixture = MeasurementsFixture()

        fixture.viewModel.state.test {
            awaitItem()
            fixture.viewModel.onEvent(MeasurementsUiEvent.AddRequested(MeasurementType.WEIGHT))
            fixture.viewModel.onEvent(MeasurementsUiEvent.FormValueChanged("abc"))
            fixture.viewModel.onEvent(MeasurementsUiEvent.FormSaved)

            val afterSave = expectMostRecentItem()
            afterSave.form.shouldNotBeNull().valueError shouldBe true
            afterSave.sections.first { it.type == MeasurementType.WEIGHT }.entries.shouldHaveSize(0)
        }
    }

    test("excluir mostra o efeito de desfazer e o registro some; desfazer traz de volta") {
        val fixture = MeasurementsFixture()
        val created = fixture.measurements.create(
            MeasurementType.WEIGHT,
            null,
            80.0,
            MeasurementUnit.KG,
            fixture.clock.now,
            null,
        ).getOrNull().shouldNotBeNull()

        fixture.viewModel.state.test {
            awaitItem()
            fixture.viewModel.onEvent(MeasurementsUiEvent.DeleteRequested(created.id))
            val afterDelete = expectMostRecentItem()
            afterDelete.sections.first { it.type == MeasurementType.WEIGHT }.entries.shouldHaveSize(0)

            fixture.viewModel.onEvent(MeasurementsUiEvent.UndoDeleteRequested)
            val afterUndo = expectMostRecentItem()
            afterUndo.sections.first { it.type == MeasurementType.WEIGHT }.entries shouldHaveSize 1
        }

        fixture.viewModel.effects.test { awaitItem() shouldBe MeasurementsEffect.ShowUndoDelete }
    }

    test("medidas personalizadas com rótulos diferentes viram seções separadas") {
        val fixture = MeasurementsFixture()
        fixture.measurements.create(
            MeasurementType.CUSTOM,
            "panturrilha",
            35.0,
            MeasurementUnit.CM,
            fixture.clock.now,
            null
        )
        fixture.measurements.create(MeasurementType.CUSTOM, "punho", 16.0, MeasurementUnit.CM, fixture.clock.now, null)

        fixture.viewModel.state.test {
            val state = awaitItem()
            val customSections = state.sections.filter { it.type == MeasurementType.CUSTOM }
            customSections shouldHaveSize 2
            customSections.map { it.customLabel }.toSet() shouldBe setOf("panturrilha", "punho")
        }
    }
})
