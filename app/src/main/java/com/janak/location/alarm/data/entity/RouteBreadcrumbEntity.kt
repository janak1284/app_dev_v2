package com.janak.location.alarm.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "route_breadcrumbs",
    foreignKeys = [
        ForeignKey(
            entity = SavedRouteEntity::class,
            parentColumns = ["routeId"],
            childColumns = ["routeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("routeId")]
)
data class RouteBreadcrumbEntity(
    @PrimaryKey(autoGenerate = true)
    val pointId: Long = 0,
    val routeId: Long,
    val latitude: Double,
    val longitude: Double,
    val speed: Float,
    val timestamp: Long
)
