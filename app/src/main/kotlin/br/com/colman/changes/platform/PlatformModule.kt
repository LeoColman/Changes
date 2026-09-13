// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.platform

import app.cash.sqldelight.db.SqlDriver
import br.com.colman.changes.BuildConfig
import br.com.colman.changes.core.data.backup.AppInfo
import br.com.colman.changes.core.db.ScratchDriverFactory
import br.com.colman.changes.core.di.IoDispatcher
import br.com.colman.changes.core.media.MediaStorage
import br.com.colman.changes.platform.reminders.AlarmGateway
import br.com.colman.changes.platform.reminders.AndroidAlarmGateway
import br.com.colman.changes.platform.reminders.PlannedUpcomingReminders
import br.com.colman.changes.platform.reminders.PreferencesScheduledRegistry
import br.com.colman.changes.platform.reminders.ReminderNotifier
import br.com.colman.changes.platform.reminders.ReminderResyncer
import br.com.colman.changes.platform.reminders.ReminderSync
import br.com.colman.changes.platform.reminders.ScheduledRegistry
import br.com.colman.changes.platform.reminders.SettingsUpcomingReminders
import br.com.colman.changes.platform.reminders.UpcomingReminders
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

/** Implementações Android das dependências que o :core declara, mais serviços de plataforma. */
val platformModule: Module = module {
    single<SqlDriver> { androidDriver(androidContext()) }
    single<MediaStorage> { AndroidMediaStorage(File(androidContext().filesDir, MEDIA_DIR), get(IoDispatcher)) }
    single { PhotoSanitizer(androidContext(), get(IoDispatcher)) }
    single<DocumentStreams> { AndroidDocumentStreams(androidContext(), get(IoDispatcher)) }
    single<ScratchDriverFactory> { AndroidScratchDriverFactory(androidContext()) }
    single { AppInfo(BuildConfig.VERSION_NAME) }
    single<SettingsStore> { SharedPreferencesSettingsStore(androidContext()) }
    single { AppLock(get()) }
    single<BiometricAvailability> { AndroidBiometricAvailability(androidContext()) }
    single { BiometricUnlocker(get(), get(), get()) }
    single<AlarmGateway> { AndroidAlarmGateway(androidContext()) }
    single<ScheduledRegistry> { PreferencesScheduledRegistry(androidContext()) }
    single<UpcomingReminders> {
        SettingsUpcomingReminders(
            settings = get(),
            timeZones = get(),
            others = PlannedUpcomingReminders(get(), get(), get(), get()),
        )
    }
    single { ReminderNotifier(androidContext(), get()) }
    single { ReminderSync(get(), get(), get(), get()) }
    single { ReminderResyncer(get(), get(), get(), get()) }
}

/** Raiz da mídia, relativa a `filesDir`. */
const val MEDIA_DIR = "media"
