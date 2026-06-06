package com.janak.location.alarm.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "railway_track_cache")
data class RailwayTrackCacheEntity(
    @PrimaryKey
    val segmentKey: String, // format: "START_CODE-END_CODE" (alphabetical to handle both directions if needed, or directional)
    val polyline: String,   // Encoded polyline from ORR
    val distance: Double,
    val duration: Double,
    val timestamp: Long = System.currentTimeMillis()
)
