package com.janak.location.alarm.model

import kotlinx.serialization.Serializable

@Serializable
data class OrsResponse(
    val features: List<OrsFeature> = emptyList()
)

@Serializable
data class OrsFeature(
    val geometry: OrsGeometry,
    val properties: OrsProperties
)

@Serializable
data class OrsGeometry(
    val coordinates: List<List<Double>> // [[lon, lat], ...]
)

@Serializable
data class OrsProperties(
    val summary: OrsSummary
)

@Serializable
data class OrsSummary(
    val distance: Double,
    val duration: Double
)
