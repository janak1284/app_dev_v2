package com.janak.location.alarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.janak.location.alarm.RingingActivity

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val ringtoneUri = intent.getStringExtra("RINGTONE_URI")
        val vibrate = intent.getBooleanExtra("VIBRATE", true)

        // Launch the activity that will actually play the sound and wake the screen
        val ringingIntent = Intent(context, RingingActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("RINGTONE_URI", ringtoneUri)
            putExtra("VIBRATE", vibrate)
        }
        context.startActivity(ringingIntent)
    }
}
