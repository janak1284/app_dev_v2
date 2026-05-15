package com.janak.location.alarm.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_routes")
data class SavedRouteEntity(
    @PrimaryKey(autoGenerate = true)
    val routeId: Long = 0,
    val destinationName: String,
    val targetTime: Long? = null, // Store as timestamp or minutes, keeping it flexible
    val dateSaved: Long = System.currentTimeMillis()
)
