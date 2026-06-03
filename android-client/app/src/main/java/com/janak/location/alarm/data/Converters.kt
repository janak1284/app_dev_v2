package com.janak.location.alarm.data

import androidx.room.TypeConverter
import com.janak.location.alarm.model.AlarmSettings
import com.janak.location.alarm.model.TransportMode
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun fromAlarmSettings(value: AlarmSettings): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toAlarmSettings(value: String): AlarmSettings {
        return Json.decodeFromString(value)
    }

    @TypeConverter
    fun fromTransportMode(value: TransportMode): String {
        return value.name
    }

    @TypeConverter
    fun toTransportMode(value: String): TransportMode {
        return TransportMode.valueOf(value)
    }
}
