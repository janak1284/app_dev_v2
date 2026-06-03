package com.janak.location.alarm.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "route_breadcrumbs",
    foreignKeys = [
        ForeignKey(
            entity = JourneyHistoryEntity::class,
            parentColumns = ["historyId"],
            childColumns = ["historyId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SavedRouteEntity::class,
            parentColumns = ["routeId"],
            childColumns = ["routeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("historyId"), Index("routeId")]
)
data class RouteBreadcrumbEntity(
    @PrimaryKey(autoGenerate = true)
    val pointId: Long = 0,
    val historyId: Long? = null,
    val routeId: Long? = null,
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    val timestamp: Long
)
