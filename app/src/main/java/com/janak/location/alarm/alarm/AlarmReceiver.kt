package com.janak.location.alarm.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.janak.location.alarm.R
import com.janak.location.alarm.RingingActivity

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ringtoneUri = intent.getStringExtra("RINGTONE_URI")
        val vibrate = intent.getBooleanExtra("VIBRATE", true)

        showRingingNotification(context, ringtoneUri, vibrate)
    }

    private fun showRingingNotification(context: Context, ringtoneUri: String?, vibrate: Boolean) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "AlarmRingingChannel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Alarm Triggered",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager.createNotificationChannel(channel)
        }

        val ringingIntent = Intent(context, RingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("RINGTONE_URI", ringtoneUri)
            putExtra("VIBRATE", vibrate)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            1002,
            ringingIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Guardian Alarm!")
            .setContentText("Wake up! You've reached your destination or time is up.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        notificationManager.notify(1001, notification)
        
        // Also try starting the activity directly as a fallback
        try {
            context.startActivity(ringingIntent)
        } catch (e: Exception) {
            // Activity launch might be blocked, notification handles it
        }
    }
}
