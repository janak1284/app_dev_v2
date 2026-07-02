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
     * @param forceMaxVolume If true, overrides system volume to maximum for this alarm.
     */
    fun start(shouldVibrate: Boolean = true, forceMaxVolume: Boolean = false) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        
        if (forceMaxVolume) {
            val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, maxVolume, 0)
        }

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

    companion object {
        const val RAILWAY_BULLETPROOF_THRESHOLD_METERS = 2000.0

        /**
         * Pure evaluation function to determine if an alarm should trigger.
         */
        fun shouldTriggerAlarm(
            distanceMeters: Double,
            distanceThresholdMeters: Double,
            isDistanceAlarmEnabled: Boolean,
            etaMinutes: Double,
            predictiveMinutesThreshold: Int,
            isPredictiveAlarmEnabled: Boolean,
            isRailway: Boolean
        ): Boolean {
            // Bulletproof 2km safety trigger for trains regardless of user settings
            if (isRailway && distanceMeters <= RAILWAY_BULLETPROOF_THRESHOLD_METERS) {
                return true
            }
            val isDistanceTriggered = isDistanceAlarmEnabled && distanceMeters <= distanceThresholdMeters
            val isTimeTriggered = isPredictiveAlarmEnabled && etaMinutes <= predictiveMinutesThreshold
            return isDistanceTriggered || isTimeTriggered
        }
    }
}
