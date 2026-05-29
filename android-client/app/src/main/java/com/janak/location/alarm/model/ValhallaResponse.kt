package com.janak.location.alarm.model

import kotlinx.serialization.Serializable

@Serializable
data class ValhallaResponse(
    val trip: ValhallaTrip
)

@Serializable
data class ValhallaTrip(
    val legs: List<ValhallaLeg>,
    val summary: ValhallaSummary,
    val locations: List<ValhallaLocation>
)

@Serializable
data class ValhallaLeg(
    val shape: String, // Encoded polyline
    val summary: ValhallaSummary,
    val maneuvers: List<ValhallaManeuver>
)

@Serializable
data class ValhallaSummary(
    val length: Double, // in kilometers
    val time: Double, // in seconds
    val cost: Double? = null
)

@Serializable
data class ValhallaManeuver(
    val type: Int,
    val instruction: String,
    val travel_mode: String,
    val length: Double,
    val time: Double,
    val cost: Double? = null,
    val begin_shape_index: Int,
    val end_shape_index: Int,
    val transit_info: ValhallaTransitInfo? = null
)

@Serializable
data class ValhallaTransitInfo(
    val onestop_id: String? = null,
    val short_name: String? = null,
    val long_name: String? = null,
    val headsign: String? = null,
    val color: Int? = null,
    val text_color: Int? = null,
    val description: String? = null,
    val travel_mode: String? = null
)

@Serializable
data class ValhallaLocation(
    val lat: Double,
    val lon: Double,
    val type: String? = null,
    val name: String? = null
)
