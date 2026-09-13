// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.uiflows

import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.colman.changes.R
import br.com.colman.changes.feature.backup.BackupRoute
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipFile

/**
 * Fluxo crítico "export" (Seção 11.3): a `BackupRoute` composta direto, com o seletor de documento
 * falso apontando para uma Uri do `FileProvider` do próprio app, produz um zip válido com
 * `manifest.json` (ADR 0009).
 */
@RunWith(AndroidJUnit4::class)
class BackupExportFlowTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun exportingWritesAValidBackupZip() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = fileProviderTarget(context, "export-${uniqueSuffix()}.ttbackup.zip")
        check(file.createNewFile()) { "arquivo de export já existia" }
        val uri = fileProviderUriFor(context, file)

        composeTestRule.setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides FakeActivityResultRegistryOwner(uri)) {
                BackupRoute(onBack = {}, onOpenPreview = {})
            }
        }

        val exportLabel = context.getString(R.string.backup_export_action)
        composeTestRule.onNodeWithText(exportLabel).performClick()

        composeTestRule.waitUntil(WAIT_TIMEOUT_MS) { isValidBackupZip(file) }
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
