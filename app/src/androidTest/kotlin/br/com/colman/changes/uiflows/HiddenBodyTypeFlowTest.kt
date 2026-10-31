// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

@file:OptIn(ExperimentalTestApi::class)

package br.com.colman.changes.uiflows

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.colman.changes.R
import br.com.colman.changes.core.data.BodyChangeRepository
import br.com.colman.changes.core.model.BodyChangeCategory
import br.com.colman.changes.core.model.BodyChangeType
import br.com.colman.changes.core.model.Result
import br.com.colman.changes.di.graphGet
import br.com.colman.changes.feature.body.BodyChangeTypeRoute
import br.com.colman.changes.feature.body.BodyHomeRoute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Ocultar um tipo (ADR 0013) não pode custar o acesso aos registros dele: a tela inicial é a única porta
 * para a tela do tipo. Ocultar pede confirmação, o tipo sai das seções e continua abrindo pela lista de
 * ocultos, de onde volta para as seções.
 */
@RunWith(AndroidJUnit4::class)
class HiddenBodyTypeFlowTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository = graphGet<BodyChangeRepository>()
    private var openTypeId by mutableStateOf<String?>(null)

    @Test
    fun aHiddenTypeStaysReachableAndComesBackToTheList() {
        val type = createType()
        val label = checkNotNull(type.customLabel)
        showBodyScreens()

        scrollToAndClick(label)
        hideOpenType()
        composeTestRule.runOnIdle { openTypeId = null }

        val header = hiddenHeader()
        scrollTo(header)
        composeTestRule.onAllNodesWithText(label).assertCountEquals(0)
        composeTestRule.onNodeWithText(header).performClick()

        scrollToAndClick(label)
        composeTestRule.waitUntilAtLeastOneExists(
            hasContentDescription(context.getString(R.string.body_type_show_action)),
            WAIT_TIMEOUT_MS,
        )
        composeTestRule.runOnIdle { openTypeId = null }

        val showDescription = context.getString(R.string.body_home_show_type_description, label)
        waitForList()
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasContentDescription(showDescription))
        composeTestRule.onNodeWithContentDescription(showDescription).performClick()

        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) { !isHidden(type) }
        runBlocking { repository.deleteType(type.id) }
    }

    private fun showBodyScreens() {
        composeTestRule.setContent {
            val typeId = openTypeId
            if (typeId == null) {
                BodyHomeRoute(
                    onOpenExpectedChanges = {},
                    onOpenMeasurements = {},
                    onOpenChangeType = { openTypeId = it },
                )
            } else {
                BodyChangeTypeRoute(
                    typeId = typeId,
                    onBack = { openTypeId = null },
                    onAddEntry = {},
                    onEditEntry = { _, _ -> },
                )
            }
        }
    }

    private fun createType(): BodyChangeType {
        val result = runBlocking {
            repository.createCustomType(uniqueText("Tipo de teste"), BodyChangeCategory.SEXUAL_REPRODUCTIVE, null)
        }
        return (result as Result.Success).value
    }

    private fun hideOpenType() {
        val hideAction = context.getString(R.string.body_type_hide_action)
        composeTestRule.waitUntilAtLeastOneExists(hasContentDescription(hideAction), WAIT_TIMEOUT_MS)
        composeTestRule.onNodeWithContentDescription(hideAction).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.body_type_hide_confirm_text)).assertExists()
        composeTestRule.onNodeWithText(context.getString(R.string.body_type_hide_confirm_action)).performClick()
        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) { hiddenCount() > 0 }
    }

    private fun hiddenHeader(): String {
        val count = hiddenCount()
        return context.resources.getQuantityString(R.plurals.body_home_hidden_types_title, count, count)
    }

    private fun scrollToAndClick(text: String) {
        scrollTo(text)
        composeTestRule.onNodeWithText(text).performClick()
    }

    private fun scrollTo(text: String) {
        waitForList()
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    private fun waitForList() {
        composeTestRule.waitUntilAtLeastOneExists(hasScrollAction(), WAIT_TIMEOUT_MS)
    }

    private fun hiddenCount(): Int = runBlocking { repository.observeAllTypes().first() }.count { it.isHidden }

    private fun isHidden(type: BodyChangeType): Boolean = runBlocking { repository.getType(type.id) }?.isHidden == true

    private companion object {
        const val WAIT_TIMEOUT_MS = 10_000L
    }
}
