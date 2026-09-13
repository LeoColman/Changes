// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

@file:OptIn(ExperimentalTestApi::class)

package br.com.colman.changes.uiflows

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.colman.changes.R
import br.com.colman.changes.core.data.BodyChangeRepository
import br.com.colman.changes.core.data.MediaRepository
import br.com.colman.changes.core.media.MediaStorage
import br.com.colman.changes.core.model.MediaOwnerType
import br.com.colman.changes.di.graphGet
import br.com.colman.changes.feature.body.BodyEntryEditRoute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Clock
import kotlin.uuid.Uuid

/**
 * Fluxo crítico "criar entrada de mudança com foto" (Seção 11.3, critério 7.3.1): `BodyEntryEditRoute`
 * de um tipo builtin, foto escolhida da galeria (seletor falso), nota única, salvar. Confere pelo
 * grafo que a entrada existe com um anexo e que o arquivo de mídia existe de verdade.
 */
@RunWith(AndroidJUnit4::class)
class BodyEntryPhotoFlowTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun creatingAnEntryWithAPhotoAttachesItAndSavesTheFile() {
        val bodyChangeRepository = graphGet<BodyChangeRepository>()
        val typeId = firstBuiltinBodyChangeTypeId(bodyChangeRepository)
        val note = uniqueText("Nota de teste")

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val photoFile = fileProviderTarget(context, "photo-${uniqueSuffix()}.jpg")
        writeSampleJpeg(photoFile)
        val photoUri = fileProviderUriFor(context, photoFile)

        composeTestRule.setContent {
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides FakeActivityResultRegistryOwner(photoUri)
            ) {
                BodyEntryEditRoute(typeId = typeId.toString(), entryId = null, onSaved = {}, onBack = {})
            }
        }

        fillDate(context)
        fillTime(context)
        attachPhotoFromGallery(context)
        composeTestRule.onNodeWithText(context.getString(R.string.body_entry_edit_notes_label))
            .performScrollTo()
            .performTextInput(note)
        composeTestRule.onNodeWithText(context.getString(R.string.action_save)).performClick()

        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) { entryWasSavedWithAPhoto(bodyChangeRepository, typeId, note) }
    }

    private fun fillDate(context: Context) {
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.field_pick_date)).performClick()
        composeTestRule.onNode(isDayCell(todayDayOfMonth())).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.action_ok)).performClick()
    }

    private fun fillTime(context: Context) {
        composeTestRule.onNodeWithContentDescription(context.getString(R.string.field_pick_time)).performClick()
        composeTestRule.onNodeWithText(context.getString(R.string.action_ok)).performClick()
    }

    private fun attachPhotoFromGallery(context: Context) {
        composeTestRule.onNodeWithText(context.getString(R.string.body_entry_edit_add_gallery_action))
            .performScrollTo()
            .performClick()
        val removePhotoDescription = context.getString(R.string.body_entry_edit_remove_photo_action)
        composeTestRule.waitUntilAtLeastOneExists(hasContentDescription(removePhotoDescription), WAIT_TIMEOUT_MS)
    }

    /** O seed do catálogo (Seção 8) roda em paralelo à abertura do processo: espera até existir um tipo. */
    private fun firstBuiltinBodyChangeTypeId(repository: BodyChangeRepository): Uuid {
        var found: Uuid? = null
        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) {
            found = runBlocking { repository.observeAllTypes().first() }.firstOrNull { it.isBuiltin }?.id
            found != null
        }
        return checkNotNull(found) { "nenhum tipo builtin foi semeado" }
    }

    private fun todayDayOfMonth(): String =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.day.toString()

    /**
     * A célula de dia do `DatePicker` do Material 3 expõe como texto a data por extenso ("Sunday,
     * September 13, 2026"), não só o número. O dia entra como número inteiro para "1" não casar com
     * "13" nem com o ano.
     */
    private fun isDayCell(day: String): SemanticsMatcher {
        val wholeNumber = Regex("\\b$day\\b")
        val textHasDay = SemanticsMatcher("text contains the day $day") { node ->
            node.config.getOrNull(SemanticsProperties.Text).orEmpty().any { wholeNumber.containsMatchIn(it.text) }
        }
        return hasClickAction() and textHasDay
    }

    private fun entryWasSavedWithAPhoto(repository: BodyChangeRepository, typeId: Uuid, note: String): Boolean {
        val entry = runBlocking { repository.observeEntriesByType(typeId).first() }.firstOrNull { it.notes == note }
            ?: return false
        val mediaRepository = graphGet<MediaRepository>()
        val attachment = runBlocking {
            mediaRepository.observeByOwner(MediaOwnerType.BODY_CHANGE_ENTRY, entry.id).first()
        }.firstOrNull() ?: return false
        return graphGet<MediaStorage>().exists(attachment.relativePath)
    }

    private companion object {
        const val WAIT_TIMEOUT_MS = 10_000L
    }
}
