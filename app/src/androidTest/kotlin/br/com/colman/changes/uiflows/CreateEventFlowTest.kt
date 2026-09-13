// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

@file:OptIn(ExperimentalTestApi::class)

package br.com.colman.changes.uiflows

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.colman.changes.MainActivity
import br.com.colman.changes.R
import br.com.colman.changes.core.data.CalendarRepository
import br.com.colman.changes.core.model.AgendaItem
import br.com.colman.changes.di.graphGet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock

/**
 * Fluxo crítico "criar evento" (Seção 11.3, 7.9): aba Calendário, botão de novo evento, título único
 * e salvar. O evento volta a aparecer na tela e no [CalendarRepository] do grafo.
 */
@RunWith(AndroidJUnit4::class)
class CreateEventFlowTest {

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    @Test
    fun creatingAnEventShowsItOnTheScreenAndInTheRepository() {
        markOnboardingDone()
        val calendarRepository = graphGet<CalendarRepository>()
        val eventTitle = uniqueText("Evento de teste")

        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val calendarTabLabel = targetContext.getString(R.string.tab_calendar)
        val newEventDescription = targetContext.getString(R.string.calendar_action_new_event)
        val titleLabel = targetContext.getString(R.string.calendar_field_title)
        val saveLabel = targetContext.getString(R.string.action_save)

        ActivityScenario.launch(MainActivity::class.java).use {
            composeTestRule.waitUntilAtLeastOneExists(hasText(calendarTabLabel), WAIT_TIMEOUT_MS)
            composeTestRule.onNodeWithText(calendarTabLabel).performClick()

            composeTestRule.waitUntilAtLeastOneExists(hasContentDescription(newEventDescription), WAIT_TIMEOUT_MS)
            composeTestRule.onNodeWithContentDescription(newEventDescription).performClick()

            composeTestRule.waitUntilAtLeastOneExists(hasText(titleLabel), WAIT_TIMEOUT_MS)
            composeTestRule.onNodeWithText(titleLabel).performTextInput(eventTitle)
            // Salvar fica no fim do formulário, abaixo da recorrência: rola até ele antes do toque.
            composeTestRule.onNodeWithText(saveLabel).performScrollTo().performClick()

            // O título digitado já existe no próprio campo: só vale depois que o formulário fecha.
            composeTestRule.waitUntilDoesNotExist(hasText(titleLabel), WAIT_TIMEOUT_MS)
            composeTestRule.waitUntilAtLeastOneExists(hasText(eventTitle), WAIT_TIMEOUT_MS)
        }

        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) { eventIsInRepository(calendarRepository, eventTitle) }
    }

    private fun eventIsInRepository(repository: CalendarRepository, title: String): Boolean {
        val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
        val agenda = runBlocking { repository.observeAgenda(today, today, includeMilestones = false).first() }
        return agenda.filterIsInstance<AgendaItem.Event>().any { it.event.title == title }
    }

    private companion object {
        const val WAIT_TIMEOUT_MS = 5_000L
    }
}
