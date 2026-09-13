// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.di

import br.com.colman.changes.core.di.coreModule
import br.com.colman.changes.feature.backup.backupModule
import br.com.colman.changes.feature.body.bodyModule
import br.com.colman.changes.feature.calendar.calendarModule
import br.com.colman.changes.feature.expected.expectedModule
import br.com.colman.changes.feature.health.healthModule
import br.com.colman.changes.feature.medication.medicationModule
import br.com.colman.changes.feature.mood.moodModule
import br.com.colman.changes.feature.settings.settingsModule
import br.com.colman.changes.feature.today.todayModule
import br.com.colman.changes.feature.trash.trashModule
import br.com.colman.changes.feature.vitals.vitalsModule
import br.com.colman.changes.platform.platformModule
import org.koin.core.module.Module
import org.koin.dsl.module

/** Módulo raiz do app. Cada pacote de feature contribui com o seu `Module` nesta lista. */
val appModule: Module = module { }

val appModules: List<Module> = listOf(
    coreModule,
    platformModule,
    appModule,
    todayModule,
    bodyModule,
    medicationModule,
    vitalsModule,
    moodModule,
    backupModule,
    settingsModule,
    healthModule,
    expectedModule,
    calendarModule,
    trashModule,
)
