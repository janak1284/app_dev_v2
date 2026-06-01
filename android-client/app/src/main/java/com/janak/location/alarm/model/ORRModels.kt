package com.janak.location.alarm.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ORRRouteResponse(
    @SerialName("paths") val paths: List<ORRPath>
)

@Serializable
data class ORRPath(
    @SerialName("points") val points: String, // The encoded polyline string
    @SerialName("distance") val distanceMeters: Double,
    @SerialName("time") val timeMillis: Long
)
