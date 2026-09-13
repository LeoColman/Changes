// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.feature.body

import br.com.colman.changes.core.data.BodyChangeRepository
import br.com.colman.changes.core.data.MediaRepository
import br.com.colman.changes.core.data.ProfileRepository
import br.com.colman.changes.core.model.BodyChangeType
import br.com.colman.changes.core.testing.FileMediaStorage
import br.com.colman.changes.core.testing.FixedClock
import br.com.colman.changes.core.testing.FixedTimeZoneProvider
import br.com.colman.changes.core.testing.testDatabase
import br.com.colman.changes.core.testing.testLabels
import io.kotest.core.listeners.AfterSpecListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import java.nio.file.Files
import java.util.Locale

/**
 * Repositórios reais sobre um banco de teste, para os specs de ViewModel de `feature.body` (Seção
 * "Testes" do guia de código: sem Android, `UnconfinedTestDispatcher` como dispatcher de I/O).
 * Os specs registram [PtBrDefaultLocale], porque `BodyLabels` resolve com `Locale.getDefault()`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BodyTestEnvironment(
    val clock: FixedClock = FixedClock(),
    val timeZones: FixedTimeZoneProvider = FixedTimeZoneProvider(),
) {
    private val io = UnconfinedTestDispatcher()
    private val mediaDir = Files.createTempDirectory("body-media").toFile()

    val database = testDatabase(clock)
    val bodyChangeRepository = BodyChangeRepository(database, io, clock, timeZones)
    val mediaRepository = MediaRepository(database, io, clock, timeZones, FileMediaStorage(mediaDir))
    val profileRepository = ProfileRepository(database, io, clock)
    val bodyLabels = BodyLabels(profileRepository, testLabels)

    suspend fun typeByCode(code: String): BodyChangeType =
        bodyChangeRepository.observeAllTypes().first().first { it.code == code }
}

/**
 * Fixa pt-BR como locale padrão durante um spec e devolve o anterior no fim: o teste não depende do
 * locale da máquina e não vaza a troca para os specs seguintes da mesma JVM.
 */
class PtBrDefaultLocale : BeforeSpecListener, AfterSpecListener {
    private var previous: Locale = Locale.getDefault()

    override suspend fun beforeSpec(spec: Spec) {
        previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("pt-BR"))
    }

    override suspend fun afterSpec(spec: Spec) {
        Locale.setDefault(previous)
    }
}

/** Bytes mínimos usados como "foto" nos testes; `PhotoSanitizer` nunca é exercitado aqui. */
fun fakePhotoBytes(): ByteArray = byteArrayOf(1, 2, 3, 4)
