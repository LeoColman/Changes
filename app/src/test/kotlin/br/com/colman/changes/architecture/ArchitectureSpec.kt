// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import com.lemonappdev.konsist.api.verify.assertTrue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import java.io.File

/** As quatro fronteiras da Seção 4.2, mais as regras de licença e de DI. */
class ArchitectureSpec : FunSpec({
    val root = "br.com.colman.changes"

    fun inPackage(pkg: String?, vararg prefixes: String) =
        pkg != null && prefixes.any { pkg == it || pkg.startsWith("$it.") }

    test(":core does not import android, androidx or coroutines-android") {
        Konsist.scopeFromModule("core").imports.assertFalse { import ->
            import.name.startsWith("android.") ||
                import.name.startsWith("androidx.") ||
                import.name.startsWith("kotlinx.coroutines.android")
        }
    }

    test("a feature package never imports another feature package") {
        val featureRoot = "$root.feature."
        fun featureOf(name: String) = name.removePrefix(featureRoot).substringBefore('.')
            .takeIf { name.startsWith(featureRoot) }

        Konsist.scopeFromModule("app").files
            .filter { it.packagee?.name?.startsWith(featureRoot) == true }
            .assertTrue { file ->
                val own = featureOf(file.packagee!!.name)
                file.imports.none { import -> featureOf(import.name)?.let { it != own } ?: false }
            }
    }

    test("only core.data, core.db and app.platform import SQLDelight") {
        Konsist.scopeFromProduction().files
            .filter { file -> file.imports.any { it.name.startsWith("app.cash.sqldelight") } }
            .assertTrue { inPackage(it.packagee?.name, "$root.core.data", "$root.core.db", "$root.platform") }
    }

    test("only app.platform imports Context, AlarmManager, ExifInterface or BiometricPrompt") {
        val platformOnly = setOf(
            "android.content.Context",
            "android.app.AlarmManager",
            "androidx.exifinterface.media.ExifInterface",
            "androidx.biometric.BiometricPrompt",
        )
        Konsist.scopeFromProduction(moduleName = "app").files
            .filter { file -> file.imports.any { it.name in platformOnly } }
            .assertTrue { inPackage(it.packagee?.name, "$root.platform") }
    }

    test("Koin service locator is only used in app.di") {
        val locator = setOf(
            "org.koin.core.component.KoinComponent",
            "org.koin.core.component.inject",
            "org.koin.core.component.get",
        )
        Konsist.scopeFromProduction().files
            .filter { file -> file.imports.any { it.name in locator } }
            .assertTrue { inPackage(it.packagee?.name, "$root.di") }
    }

    test("Dispatchers are only referenced in Koin module files") {
        Konsist.scopeFromProduction().files
            .filter { file -> file.imports.any { it.name == "kotlinx.coroutines.Dispatchers" } }
            .assertTrue { it.name.endsWith("Module") }
    }

    test("every Kotlin file starts with the SPDX header") {
        Konsist.scopeFromProject().files
            .filter { it.path.endsWith(".kt") }
            .assertTrue { file ->
                val lines = file.text.lines()
                lines.getOrNull(0) == "// SPDX-License-Identifier: AGPL-3.0-or-later" &&
                    lines.getOrNull(1)?.startsWith("// SPDX-FileCopyrightText: ") == true
            }
    }

    test("every SQLDelight file starts with the SPDX header") {
        val header = "-- SPDX-License-Identifier: AGPL-3.0-or-later"
        val sqlRoot = File("../core/src/main/sqldelight")
        val offenders = sqlRoot.walkTopDown()
            .filter { it.isFile && (it.extension == "sq" || it.extension == "sqm") }
            .filterNot { it.useLines { lines -> lines.firstOrNull() == header } }
            .map { it.path }
            .toList()
        offenders.shouldBeEmpty()
    }
})
