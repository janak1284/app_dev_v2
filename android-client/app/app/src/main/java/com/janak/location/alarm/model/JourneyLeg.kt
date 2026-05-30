package com.janak.location.alarm.model

import kotlinx.serialization.Serializable

@Serializable
data class JourneyLeg(
    val mode: TransportMode,
    val geometry: String, // GeoJSON LineString
    val startName: String? = null,
    val endName: String? = null,
    val startLat: Double,
    val startLng: Double,
    val endLat: Double,
    val endLng: Double,
    val distanceMeters: Double,
    val durationMillis: Long,
    val departureTime: Long? = null,
    val arrivalTime: Long? = null,
    val headsign: String? = null,
    val lineName: String? = null,
    val color: String? = null // Hex color for UI
)
