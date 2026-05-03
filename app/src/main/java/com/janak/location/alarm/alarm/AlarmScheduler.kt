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

        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, settings.backupHour)
            set(Calendar.MINUTE, settings.backupMinute)
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
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
