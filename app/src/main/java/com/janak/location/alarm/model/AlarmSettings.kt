package com.janak.location.alarm.model

import android.net.Uri

data class AlarmSettings(
    val distanceMeters: Int = 500,
    val isDistanceAlarmEnabled: Boolean = true,
    val timeAlarmHour: Int = 0,
    val timeAlarmMinute: Int = 0,
    val isTimeAlarmEnabled: Boolean = false, // This is the absolute Backup Alarm
    val predictiveMinutes: Int = 10,
    val isPredictiveAlarmEnabled: Boolean = false, // This is the Smart ETA Alarm
    val isVibrateEnabled: Boolean = true,
    val ringtoneUri: Uri? = null,
    val alarmName: String = ""
)
