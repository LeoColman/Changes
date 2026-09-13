// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.health

import app.cash.turbine.test
import br.com.colman.changes.core.data.LabRepository
import br.com.colman.changes.core.data.NewLabResult
import br.com.colman.changes.core.data.ProfileRepository
import br.com.colman.changes.core.model.ReferenceRange
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.FixedTimeZoneProvider
import br.com.colman.changes.core.testing.MainDispatcherListener
import br.com.colman.changes.core.testing.TestZones
import br.com.colman.changes.core.testing.testDatabase
import br.com.colman.changes.core.testing.testDataset
import br.com.colman.changes.core.testing.testLabels
import br.com.colman.changes.ui.chart.ChartBand
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlin.time.Duration.Companion.days

/** Instala analitos, resultados e o ViewModel sobre um banco novo, em memória, já com o seed. */
private class LabResultsFixture {
    val io = UnconfinedTestDispatcher()
    val clock = FixedClock()
    val zones = FixedTimeZoneProvider(TestZones.SAO_PAULO)
    val database = testDatabase(clock)
    val profiles = ProfileRepository(database, io, clock)
    val labs = LabRepository(database, io, clock, zones)
    val viewModel = LabResultsViewModel(labs, profiles, testLabels, clock, zones)

    val analyteId = testDataset.labAnalytes.first { it.code == "TESTOSTERONE_TOTAL" }.id
}

class LabResultsViewModelSpec : FunSpec({
    register(MainDispatcherListener())

    test("critério 7.6.1: resultado sem faixa informada não tem marcação de fora da faixa") {
        val fixture = LabResultsFixture()
        fixture.labs.createResult(NewLabResult(fixture.analyteId, 900.0, "ng/dL", fixture.clock.now, null, null, null))

        fixture.viewModel.state.test {
            awaitItem()
            fixture.viewModel.onEvent(LabResultsUiEvent.Load(fixture.analyteId))
            val detail = expectMostRecentItem().detail.shouldNotBeNull()
            detail.results.single().isOutOfRange shouldBe false
        }
    }

    test("critério 7.6.1: valor dentro da faixa informada não tem marcação") {
        val fixture = LabResultsFixture()
        fixture.labs.createResult(
            NewLabResult(
                fixture.analyteId,
                500.0,
                "ng/dL",
                fixture.clock.now,
                ReferenceRange(300.0, 1000.0),
                null,
                null
            ),
        )

        fixture.viewModel.state.test {
            awaitItem()
            fixture.viewModel.onEvent(LabResultsUiEvent.Load(fixture.analyteId))
            val detail = expectMostRecentItem().detail.shouldNotBeNull()
            detail.results.single().isOutOfRange shouldBe false
        }
    }

    test("critério 7.6.1: valor fora da faixa informada tem marcação") {
        val fixture = LabResultsFixture()
        fixture.labs.createResult(
            NewLabResult(
                fixture.analyteId,
                1500.0,
                "ng/dL",
                fixture.clock.now,
                ReferenceRange(300.0, 1000.0),
                null,
                null
            ),
        )

        fixture.viewModel.state.test {
            awaitItem()
            fixture.viewModel.onEvent(LabResultsUiEvent.Load(fixture.analyteId))
            val detail = expectMostRecentItem().detail.shouldNotBeNull()
            detail.results.single().isOutOfRange shouldBe true
        }
    }

    test("a faixa do gráfico vem do resultado mais recente que tiver faixa informada, não do último resultado") {
        val fixture = LabResultsFixture()
        val now = fixture.clock.now
        fixture.labs.createResult(
            NewLabResult(fixture.analyteId, 400.0, "ng/dL", now - 2.days, ReferenceRange(100.0, 200.0), null, null),
        )
        fixture.labs.createResult(
            NewLabResult(fixture.analyteId, 450.0, "ng/dL", now - 1.days, ReferenceRange(150.0, 250.0), null, null),
        )
        fixture.labs.createResult(NewLabResult(fixture.analyteId, 500.0, "ng/dL", now, null, null, null))

        fixture.viewModel.state.test {
            awaitItem()
            fixture.viewModel.onEvent(LabResultsUiEvent.Load(fixture.analyteId))
            val detail = expectMostRecentItem().detail.shouldNotBeNull()
            detail.chartBand shouldBe ChartBand(150.0, 250.0)
        }
    }

    test("criar um analito personalizado deixa ele visível na lista e abre o detalhe dele") {
        val fixture = LabResultsFixture()

        fixture.viewModel.state.test {
            awaitItem()
            fixture.viewModel.onEvent(LabResultsUiEvent.NewAnalyteRequested)
            fixture.viewModel.onEvent(
                LabResultsUiEvent.NewAnalyteChanged { it.copy(label = "Cortisol salivar", defaultUnit = "nmol/L") },
            )
            fixture.viewModel.onEvent(LabResultsUiEvent.NewAnalyteSaved)

            val saved = expectMostRecentItem()
            saved.newAnalyteForm.shouldBeNull()
            saved.detail.shouldNotBeNull().label shouldBe "Cortisol salivar"
            saved.analytes.map { it.label } shouldContain "Cortisol salivar"
        }
    }

    test("adicionar um resultado pelo formulário grava e fecha o formulário") {
        val fixture = LabResultsFixture()

        fixture.viewModel.state.test {
            awaitItem()
            fixture.viewModel.onEvent(LabResultsUiEvent.Load(fixture.analyteId))
            expectMostRecentItem()
            fixture.viewModel.onEvent(LabResultsUiEvent.AddResultRequested)
            fixture.viewModel.onEvent(LabResultsUiEvent.ResultFormChanged { it.copy(valueText = "620") })
            fixture.viewModel.onEvent(LabResultsUiEvent.ResultFormSaved)

            val saved = expectMostRecentItem()
            saved.resultForm.shouldBeNull()
            saved.detail.shouldNotBeNull().results shouldHaveSize 1
        }
    }

    test("excluir um resultado mostra o efeito de desfazer e o resultado some; desfazer traz de volta") {
        val fixture = LabResultsFixture()
        val created = fixture.labs.createResult(
            NewLabResult(fixture.analyteId, 500.0, "ng/dL", fixture.clock.now, null, null, null),
        ).getOrNull().shouldNotBeNull()

        fixture.viewModel.state.test {
            awaitItem()
            fixture.viewModel.onEvent(LabResultsUiEvent.Load(fixture.analyteId))
            expectMostRecentItem()

            fixture.viewModel.onEvent(LabResultsUiEvent.DeleteResultRequested(created.id))
            expectMostRecentItem().detail.shouldNotBeNull().results.shouldHaveSize(0)

            fixture.viewModel.onEvent(LabResultsUiEvent.UndoDeleteRequested)
            expectMostRecentItem().detail.shouldNotBeNull().results shouldHaveSize 1
        }

        fixture.viewModel.effects.test { awaitItem() shouldBe LabResultsEffect.ShowUndoDelete }
    }
})
