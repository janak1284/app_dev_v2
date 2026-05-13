package com.janak.location.alarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.janak.location.alarm.model.AlarmSettings
import java.util.Calendar

interface AlarmScheduler {
    fun scheduleBackupAlarm(settings: AlarmSettings)
    fun cancelAlarm()
}

class AlarmSchedulerImpl(private val context: Context) : AlarmScheduler {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    override fun scheduleBackupAlarm(settings: AlarmSettings) {
        if (!settings.isBackupEnabled) {
            cancelAlarm()
            return
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("RINGTONE_URI", settings.ringtoneUri?.toString())
            putExtra("VIBRATE", settings.isVibrateEnabled)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1001, // Constant ID for the single backup alarm
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Calculate trigger time as current time + duration
        val durationMs = (settings.backupHour * 3600000L) + (settings.backupMinute * 60000L)
        val triggerAtMillis = System.currentTimeMillis() + durationMs

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
    }

    override fun cancelAlarm() {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            1001,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
        }
    }
}
