package com.janak.location.alarm.model

import kotlinx.serialization.Serializable

@Serializable
data class TransitItinerary(
    val legs: List<JourneyLeg>,
    val totalDistanceMeters: Double,
    val totalDurationMillis: Long,
    val startTime: Long? = null,
    val endTime: Long? = null
)
