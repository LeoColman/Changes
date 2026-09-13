// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.core.di

import br.com.colman.changes.core.clinical.ClinicalDataset
import br.com.colman.changes.core.clinical.ClinicalLabels
import br.com.colman.changes.core.data.BodyChangeRepository
import br.com.colman.changes.core.data.CalendarRepository
import br.com.colman.changes.core.data.DoseLogRepository
import br.com.colman.changes.core.data.ExerciseRepository
import br.com.colman.changes.core.data.ExpectedChangeRepository
import br.com.colman.changes.core.data.HealthConditionRepository
import br.com.colman.changes.core.data.LabRepository
import br.com.colman.changes.core.data.MeasurementRepository
import br.com.colman.changes.core.data.MediaRepository
import br.com.colman.changes.core.data.MedicationRepository
import br.com.colman.changes.core.data.MoodRepository
import br.com.colman.changes.core.data.ProfileRepository
import br.com.colman.changes.core.data.RegimenRepository
import br.com.colman.changes.core.data.TrashRepository
import br.com.colman.changes.core.data.backup.BackupRepository
import br.com.colman.changes.core.data.backup.BackupRuntime
import br.com.colman.changes.core.db.Seeder
import br.com.colman.changes.core.db.createDatabase
import br.com.colman.changes.core.model.TimeZoneProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.TimeZone
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module
import kotlin.time.Clock

/** Qualifier do dispatcher de I/O. Único lugar do :core onde um `Dispatchers.*` literal aparece. */
public val IoDispatcher: org.koin.core.qualifier.Qualifier = named("io")

/**
 * Dependências do :core. O `SqlDriver` e a implementação de `MediaStorage` vêm do :app
 * (`app.platform`).
 */
public val coreModule: Module = module {
    single<CoroutineDispatcher>(IoDispatcher) { Dispatchers.IO }
    single<Clock> { Clock.System }
    single<TimeZoneProvider> { TimeZoneProvider { TimeZone.currentSystemDefault() } }
    single { ClinicalDataset.load() }
    single { ClinicalLabels.load() }
    single { createDatabase(get()) }
    single { Seeder(get(), get(), get(), get()) }

    single { ProfileRepository(get(), get(IoDispatcher), get()) }
    single { MedicationRepository(get(), get(IoDispatcher), get()) }
    single { RegimenRepository(get(), get(IoDispatcher), get()) }
    single { DoseLogRepository(get(), get(IoDispatcher), get(), get()) }
    single { BodyChangeRepository(get(), get(IoDispatcher), get(), get()) }
    single { MediaRepository(get(), get(IoDispatcher), get(), get(), get()) }
    single { MeasurementRepository(get(), get(IoDispatcher), get(), get(), get()) }
    single { ExerciseRepository(get(), get(IoDispatcher), get(), get()) }
    single { HealthConditionRepository(get(), get(IoDispatcher), get()) }
    single { LabRepository(get(), get(IoDispatcher), get(), get()) }
    single { MoodRepository(get(), get(IoDispatcher), get(), get()) }
    single { CalendarRepository(get(), get(IoDispatcher), get(), get(), get(), get()) }
    single { ExpectedChangeRepository(get(), get(), get(), get(), get(), get(IoDispatcher)) }
    single { TrashRepository(get(), get(), get(), get(IoDispatcher)) }
    single { BackupRuntime(get(), get(IoDispatcher), get()) }
    single { BackupRepository(get(), get(), get(), get(), get()) }
}
