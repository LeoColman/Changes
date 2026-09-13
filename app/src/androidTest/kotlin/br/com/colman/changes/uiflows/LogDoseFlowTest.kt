// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.uiflows

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.colman.changes.MainActivity
import br.com.colman.changes.R
import br.com.colman.changes.core.data.DoseLogRepository
import br.com.colman.changes.core.data.MedicationRepository
import br.com.colman.changes.core.data.NewRegimen
import br.com.colman.changes.core.data.RegimenRepository
import br.com.colman.changes.core.model.Dose
import br.com.colman.changes.core.model.DoseUnit
import br.com.colman.changes.core.model.Route
import br.com.colman.changes.core.model.Schedule
import br.com.colman.changes.core.model.getOrNull
import br.com.colman.changes.di.graphGet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * Fluxo crítico "registrar dose" (Seção 11.3): um regime ativo com hora marcada, tocar em
 * "registrar dose" no cartão da próxima dose na aba Hoje, e o registro aparece no
 * [DoseLogRepository] do grafo.
 */
@RunWith(AndroidJUnit4::class)
class LogDoseFlowTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Test
    fun tappingRegisterDoseLogsItForTheActiveRegimen() {
        markOnboardingDone()
        val regimenRepository = graphGet<RegimenRepository>()
        retireOtherActiveRegimens(regimenRepository)

        val medicationId = createTestMedication(graphGet<MedicationRepository>())
        val regimenId = createTestRegimen(regimenRepository, medicationId)
        val doseLogRepository = graphGet<DoseLogRepository>()

        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val logDoseLabel = targetContext.getString(R.string.today_log_dose_action)

        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
                composeTestRule.onAllNodesWithText(logDoseLabel).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText(logDoseLabel).performClick()

            composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
                doseWasLogged(doseLogRepository, medicationId, regimenId)
            }
        }
    }

    /** Regimes ativos de execuções anteriores (os dados persistem, Seção 11.3) ambiguariam o cartão. */
    private fun retireOtherActiveRegimens(regimenRepository: RegimenRepository) = runBlocking {
        val yesterday = today().minus(1, DateTimeUnit.DAY)
        regimenRepository.observeActive().first().forEach { regimenRepository.end(it.id, yesterday) }
    }

    private fun createTestMedication(repository: MedicationRepository): Uuid {
        val medication = runBlocking {
            repository.createCustom(uniqueText("Medicação de teste"), null, Route.ORAL, null).getOrNull()
        }
        return checkNotNull(medication) { "createCustom deveria ter tido sucesso" }.id
    }

    private fun createTestRegimen(repository: RegimenRepository, medicationId: Uuid): Uuid {
        val newRegimen = NewRegimen(
            medicationId = medicationId,
            dose = Dose(1.0, DoseUnit.MG),
            route = Route.ORAL,
            schedule = Schedule.IntervalDays(1),
            timeOfDay = LocalTime(9, 0),
            startDate = today(),
            endDate = null,
            notes = null,
        )
        val regimen = runBlocking { repository.create(newRegimen).getOrNull() }
        return checkNotNull(regimen) { "create deveria ter tido sucesso" }.id
    }

    private fun doseWasLogged(repository: DoseLogRepository, medicationId: Uuid, regimenId: Uuid): Boolean {
        val history = runBlocking {
            repository.observeHistory(medicationId, Instant.DISTANT_PAST, Instant.DISTANT_FUTURE).first()
        }
        return history.any { it.regimenId == regimenId }
    }

    private fun today(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    private companion object {
        const val WAIT_TIMEOUT_MS = 5_000L
    }
}
