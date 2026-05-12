package com.janak.location.alarm.model

import android.net.Uri

data class AlarmSettings(
    val distanceMeters: Int = 500,
    val backupHour: Int = 0,
    val backupMinute: Int = 0,
    val isBackupEnabled: Boolean = false,
    val isVibrateEnabled: Boolean = true,
    val ringtoneUri: Uri? = null,
    val alarmName: String = ""
)
