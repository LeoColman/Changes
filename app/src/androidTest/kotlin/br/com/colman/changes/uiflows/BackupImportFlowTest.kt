// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

@file:OptIn(ExperimentalTestApi::class)

package br.com.colman.changes.uiflows

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.colman.changes.R
import br.com.colman.changes.feature.backup.BackupRoute
import br.com.colman.changes.feature.backup.ImportPreviewRoute
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

/**
 * Fluxo crítico "import" (Seção 11.3, ADR 0009): exporta para um arquivo, escolhe o mesmo arquivo,
 * escolhe Mesclar, segue para a prévia (`ImportPreviewRoute` na mesma composição, trocando por
 * estado, exatamente como o grafo real liga as duas telas em `SettingsGraphs.kt`), confirma e confere
 * o resumo final na tela.
 */
@RunWith(AndroidJUnit4::class)
class BackupImportFlowTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun mergingAnExportedBackupEndsWithTheFinalSummary() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = fileProviderTarget(context, "import-${uniqueSuffix()}.ttbackup.zip")
        check(file.createNewFile()) { "arquivo de import já existia" }
        val uri = fileProviderUriFor(context, file)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides FakeActivityResultRegistryOwner(uri)) {
                var showPreview by remember { mutableStateOf(false) }
                if (showPreview) {
                    ImportPreviewRoute(onBack = { showPreview = false }, onDone = { showPreview = false })
                } else {
                    BackupRoute(onBack = {}, onOpenPreview = { showPreview = true })
                }
            }
        }

        val exportLabel = context.getString(R.string.backup_export_action)
        val importLabel = context.getString(R.string.backup_import_action)
        val mergeTitleLabel = context.getString(R.string.backup_import_mode_merge_title)
        val mergeActionLabel = context.getString(R.string.backup_action_merge)
        val previewTitleLabel = context.getString(R.string.backup_preview_title)
        val doneLabel = context.getString(R.string.backup_action_done)

        composeTestRule.onNodeWithText(exportLabel).performClick()
        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) { isValidBackupZip(file) }

        composeTestRule.onNodeWithText(importLabel).performClick()
        composeTestRule.waitUntilAtLeastOneExists(hasText(mergeTitleLabel), WAIT_TIMEOUT_MS)
        composeTestRule.onNodeWithText(mergeTitleLabel).performClick()

        composeTestRule.waitUntilAtLeastOneExists(hasText(previewTitleLabel), WAIT_TIMEOUT_MS)
        // O botão fica no fim da prévia, abaixo das contagens por tabela: rola até ele antes do toque.
        composeTestRule.onNodeWithText(mergeActionLabel).performScrollTo().performClick()
        composeTestRule.onNode(hasText(mergeActionLabel) and hasAnyAncestor(isDialog())).performClick()

        composeTestRule.waitUntilAtLeastOneExists(hasText(doneLabel), WAIT_TIMEOUT_MS)
    }

    private fun isValidBackupZip(file: File): Boolean {
        if (!file.isFile || file.length() == 0L) return false
        return runCatching { ZipFile(file).use { zip -> zip.getEntry(MANIFEST_ENTRY) != null } }.getOrDefault(false)
    }

    private companion object {
        const val MANIFEST_ENTRY = "manifest.json"
        const val WAIT_TIMEOUT_MS = 10_000L
    }
}
