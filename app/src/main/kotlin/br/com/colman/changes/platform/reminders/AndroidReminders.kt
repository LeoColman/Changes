// SPDX-License-Identifier: AGPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Leonardo Colman Lopes

package br.com.colman.changes.platform.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import br.com.colman.changes.MainActivity
import br.com.colman.changes.R
import br.com.colman.changes.platform.SettingsStore

/** AlarmManager: exato quando o sistema permite, inexato (mas ainda em modo ocioso) quando não. */
class AndroidAlarmGateway(private val context: Context) : AlarmGateway {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun schedule(requestCode: Int, reminder: ScheduledReminder) {
        val intent = pendingIntent(requestCode, reminder)
        val at = reminder.at.toEpochMilliseconds()
        if (ReminderPermissions.canScheduleExact(context)) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    override fun cancel(requestCode: Int) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pending != null) alarms.cancel(pending)
    }

    private fun pendingIntent(requestCode: Int, reminder: ScheduledReminder): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra(ReminderReceiver.EXTRA_KIND, reminder.kind.name)
            .putExtra(ReminderReceiver.EXTRA_CODE, requestCode)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

class PreferencesScheduledRegistry(context: Context) : ScheduledRegistry {
    private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    override fun load(): Set<Int> = prefs.getStringSet(
        KEY,
        emptySet()
    ).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()

    override fun save(requestCodes: Set<Int>) {
        prefs.edit().putStringSet(KEY, requestCodes.map { it.toString() }.toSet()).apply()
    }

    private companion object {
        const val FILE = "scheduled_reminders"
        const val KEY = "codes"
    }
}

/**
 * Notificação discreta (Seção 7.9): texto configurável, padrão "Lembrete", nunca menciona medicação
 * nem transição, e fica oculta na tela bloqueada (`VISIBILITY_SECRET`, no canal e na notificação).
 */
class ReminderNotifier(private val context: Context, private val settings: SettingsStore) {
    fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT
        )
            .apply {
                description = context.getString(R.string.reminder_channel_description)
                lockscreenVisibility = NotificationCompat.VISIBILITY_SECRET
                setShowBadge(false)
            }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun notify(requestCode: Int) {
        if (!ReminderPermissions.canPostNotifications(context)) return
        ensureChannel()
        val open = PendingIntent.getActivity(
            context,
            requestCode,
            Intent(
                context,
                MainActivity::class.java
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = settings.settings.value.reminderText?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.mood_reminder_default_text)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        @Suppress("MissingPermission") // checado em canPostNotifications
        NotificationManagerCompat.from(context).notify(requestCode, notification)
    }

    companion object {
        const val CHANNEL_ID: String = "reminders"
    }
}

/** Estado das permissões de lembrete, com caminho de degradação quando negadas (Seção 7.9). */
object ReminderPermissions {
    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        val state = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        return state == PackageManager.PERMISSION_GRANTED
    }

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    /** Tela do sistema para liberar alarmes exatos (Android 12+). */
    @RequiresApi(Build.VERSION_CODES.S)
    fun exactAlarmSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri())
}
