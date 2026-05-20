package com.janak.location.alarm.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.janak.location.alarm.model.AlarmSettings

@Entity(tableName = "journey_history")
data class JourneyHistoryEntity(
    @PrimaryKey(autoGenerate = true) val historyId: Long = 0,
    val routeId: Long?, 
    val startingPointLat: Double,
    val startingPointLng: Double,
    val destinationLat: Double,
    val destinationLng: Double,
    val destinationName: String,
    val mapDestinationName: String?,
    val alarmConfigAtTime: AlarmSettings,
    val timestamp: Long,
    val durationMillis: Long = 0,
    val actualDistanceMeters: Double = 0.0,
    val actualRouteGeoJson: String? = null
)
