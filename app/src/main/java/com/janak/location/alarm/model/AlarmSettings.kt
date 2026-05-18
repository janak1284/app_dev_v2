package com.janak.location.alarm.model

import android.net.Uri

data class AlarmSettings(
    val distanceMeters: Int = 500,
    val timeAlarmHour: Int = 0,
    val timeAlarmMinute: Int = 0,
    val isTimeAlarmEnabled: Boolean = false,
    val isVibrateEnabled: Boolean = true,
    val ringtoneUri: Uri? = null,
    val alarmName: String = ""
)
