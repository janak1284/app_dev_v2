package com.janak.location.alarm.model

import kotlinx.serialization.Serializable

@Serializable
data class OsrmTableResponse(
    val code: String,
    val durations: List<List<Double>>? = null,
    val distances: List<List<Double>>? = null,
    val sources: List<OsrmWaypoint>? = null,
    val destinations: List<OsrmWaypoint>? = null
)

@Serializable
data class OsrmWaypoint(
    val hint: String? = null,
    val distance: Double? = null,
    val name: String? = null,
    val location: List<Double>? = null // [longitude, latitude]
)
