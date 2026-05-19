package com.janak.location.alarm.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.janak.location.alarm.model.AlarmSettings

@Entity(tableName = "saved_routes")
data class SavedRouteEntity(
    @PrimaryKey(autoGenerate = true)
    val routeId: Long = 0,
    val destinationName: String,
    val mapDestinationName: String?,
    val destinationLat: Double,
    val destinationLng: Double,
    val targetTime: Long? = null,
    val dateSaved: Long = System.currentTimeMillis(),
    val lastTakenTimestamp: Long = System.currentTimeMillis(),
    val alarmSettings: AlarmSettings
)
