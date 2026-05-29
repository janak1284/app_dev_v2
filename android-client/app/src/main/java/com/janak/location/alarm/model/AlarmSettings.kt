package com.janak.location.alarm.model

import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class AlarmSettings(
    val distanceMeters: Int = 500,
    val isDistanceAlarmEnabled: Boolean = true,
    val predictiveMinutes: Int = 10,
    val isPredictiveAlarmEnabled: Boolean = false, // This is the Smart ETA Alarm
    val isVibrateEnabled: Boolean = true,
    @Transient val ringtoneUri: Uri? = null,
    val alarmName: String = ""
)
