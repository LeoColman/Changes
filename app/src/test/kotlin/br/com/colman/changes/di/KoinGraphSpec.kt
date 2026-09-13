// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.di

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import app.cash.sqldelight.db.SqlDriver
import br.com.colman.changes.core.testing.inMemoryDriver
import br.com.colman.changes.feature.body.BodyChangeTypeViewModel
import br.com.colman.changes.feature.body.BodyEntryEditArgs
import br.com.colman.changes.feature.body.BodyEntryEditViewModel
import br.com.colman.changes.feature.body.BodyPhotoIntake
import io.kotest.core.spec.style.FunSpec
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinApplication
import org.koin.test.check.checkModules
import org.koin.test.verify.definition
import org.koin.test.verify.injectedParameters
import org.koin.test.verify.verify
import java.io.File
import java.nio.file.Files
import kotlin.reflect.KClass

/**
 * Koin resolve em runtime: este teste é a verificação estática que o Hilt daria (Seção 11.3).
 * - `verify()` em cada módulo, aceitando como externos só os tipos que outro módulo do grafo declara
 *   e os da plataforma (Context, driver);
 * - `checkModules()` sobre a lista completa, instanciando tudo com um Context falso.
 */
@OptIn(KoinExperimentalAPI::class, KoinInternalApi::class)
class KoinGraphSpec : FunSpec({
    val platformTypes: List<KClass<*>> = listOf(Context::class, SqlDriver::class)
    val providedByGraph: List<KClass<*>> = appModules.flatMap { module ->
        module.mappings.values.flatMap { factory ->
            listOf(factory.beanDefinition.primaryType) + factory.beanDefinition.secondaryTypes
        }
    }

    // Parâmetros entregues na criação (`parametersOf` na Route) não vêm do grafo: declarados aqui.
    val injected = injectedParameters(
        definition<BodyChangeTypeViewModel>(String::class),
        definition<BodyEntryEditViewModel>(BodyEntryEditArgs::class, BodyPhotoIntake::class),
    )

    test("every module verifies against what the rest of the graph provides") {
        appModules.forEach { it.verify(extraTypes = platformTypes + providedByGraph, injections = injected) }
    }

    test("the full module list resolves") {
        val context = FakeContext(Files.createTempDirectory("koin-graph").toFile())
        @Suppress("DEPRECATION")
        koinApplication { modules(appModules) }.checkModules {
            withInstance<Context>(context)
            withInstance<SqlDriver>(inMemoryDriver())
            withParameters<BodyChangeTypeViewModel> { parametersOf("00000000-0000-4000-8000-000000000001") }
            withParameters<BodyEntryEditViewModel> {
                parametersOf(BodyEntryEditArgs(null, null), BodyPhotoIntake { error("not used by the graph check") })
            }
        }
    }
})

/** Context mínimo para instanciar o grafo em JVM: preferências em memória, diretórios temporários. */
private class FakeContext(private val root: File) : ContextWrapper(null) {
    private val preferences = mutableMapOf<String, FakePreferences>()

    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
        preferences.getOrPut(name) { FakePreferences() }

    override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }

    override fun getCacheDir(): File = File(root, "cache").apply { mkdirs() }

    override fun getPackageName(): String = "br.com.colman.changes.test"

    override fun getApplicationContext(): Context = this
}

private class FakePreferences : SharedPreferences, SharedPreferences.Editor {
    private val values = mutableMapOf<String, Any?>()

    override fun getAll(): MutableMap<String, *> = values

    override fun getString(key: String, defValue: String?): String? = values[key] as String? ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        values[key] as MutableSet<String>? ?: defValues

    override fun getInt(key: String, defValue: Int): Int = values[key] as Int? ?: defValue

    override fun getLong(key: String, defValue: Long): Long = values[key] as Long? ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = values[key] as Float? ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as Boolean? ?: defValue

    override fun contains(key: String): Boolean = key in values

    override fun edit(): SharedPreferences.Editor = this

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

    override fun putString(key: String, value: String?) = apply { values[key] = value }

    override fun putStringSet(key: String, values: MutableSet<String>?) = apply { this.values[key] = values }

    override fun putInt(key: String, value: Int) = apply { values[key] = value }

    override fun putLong(key: String, value: Long) = apply { values[key] = value }

    override fun putFloat(key: String, value: Float) = apply { values[key] = value }

    override fun putBoolean(key: String, value: Boolean) = apply { values[key] = value }

    override fun remove(key: String) = apply { values.remove(key) }

    override fun clear() = apply { values.clear() }

    override fun commit(): Boolean = true

    override fun apply() = Unit
}
