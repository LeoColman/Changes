// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import br.com.colman.changes.core.data.TrashRepository
import br.com.colman.changes.core.data.backup.BackupRepository
import br.com.colman.changes.core.db.Seeder
import br.com.colman.changes.core.di.IoDispatcher
import br.com.colman.changes.di.appModules
import br.com.colman.changes.platform.AppLock
import br.com.colman.changes.platform.reminders.ReminderNotifier
import br.com.colman.changes.platform.reminders.ReminderResyncer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ChangesApplication : Application() {
    /** Escopo da vida do processo, para trabalho de inicialização que não pertence a uma tela. */
    private val applicationScope by lazy { CoroutineScope(SupervisorJob() + get<CoroutineDispatcher>(IoDispatcher)) }

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ChangesApplication)
            modules(appModules)
        }
        ProcessLifecycleOwner.get().lifecycle.addObserver(get<AppLock>())
        get<ReminderNotifier>().ensureChannel()
        // Mídias em staging de um import interrompido (ADR 0009), seed idempotente dos catálogos e do
        // dataset clínico (Seção 8), depois os alarmes passam a acompanhar os dados (Seção 7.9). As
        // telas observam o banco e se atualizam quando o seed termina.
        applicationScope.launch {
            get<BackupRepository>().recoverInterruptedImports()
            get<TrashRepository>().purgeExpired()
            get<Seeder>().seed()
            get<ReminderResyncer>().start(applicationScope)
        }
    }
}
