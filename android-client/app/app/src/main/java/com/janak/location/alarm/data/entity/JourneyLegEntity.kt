package com.janak.location.alarm.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.janak.location.alarm.model.TransportMode

@Entity(
    tableName = "journey_legs",
    foreignKeys = [
        ForeignKey(
            entity = SavedRouteEntity::class,
            parentColumns = ["routeId"],
            childColumns = ["routeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = JourneyHistoryEntity::class,
            parentColumns = ["historyId"],
            childColumns = ["historyId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("routeId"), Index("historyId")]
)
data class JourneyLegEntity(
    @PrimaryKey(autoGenerate = true)
    val legId: Long = 0,
    val routeId: Long? = null,
    val historyId: Long? = null,
    val sequenceIndex: Int, // Order of the leg in the itinerary
    val mode: TransportMode,
    val geometry: String,
    val startName: String?,
    val endName: String?,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val distanceMeters: Double,
    val durationMillis: Long,
    val lineName: String? = null,
    val color: String? = null
)
