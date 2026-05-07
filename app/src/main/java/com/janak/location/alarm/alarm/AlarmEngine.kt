package com.janak.location.alarm.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * AlarmEngine handles the hardware interactions for the alarm,
 * specifically the looping system sound and the haptic vibration.
 */
class AlarmEngine(private val context: Context) {

    private var ringtone: Ringtone? = null
    
    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    /**
     * Starts looping the default system alarm sound and vibration.
     * @param shouldVibrate Whether the haptic vibration should be active.
     */
    fun start(shouldVibrate: Boolean = true) {
        // 1. Setup and play Sound
        if (ringtone == null) {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            
            ringtone = RingtoneManager.getRingtone(context, alarmUri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    isLooping = true
                }
                play()
            }
        }

        // 2. Setup and start Vibration (if enabled)
        if (shouldVibrate) {
            val pattern = longArrayOf(0, 500, 500) // Start immediately, vibrate 500ms, pause 500ms
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0)) // 0 means loop from index 0
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 0)
            }
        }
    }

    /**
     * Stops both the sound and vibration immediately.
     */
    fun stop() {
        ringtone?.stop()
        ringtone = null
        vibrator.cancel()
    }
}
