package com.janak.location.alarm.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.InternalSerializationApi

@OptIn(InternalSerializationApi::class)
@Serializable
data class OsrmResponse(
    val routes: List<OsrmRoute>,
    val code: String
)

@OptIn(InternalSerializationApi::class)
@Serializable
data class OsrmRoute(
    val geometry: OsrmGeometry,
    val duration: Double,
    val distance: Double
)

@OptIn(InternalSerializationApi::class)
@Serializable
data class OsrmGeometry(
    val coordinates: List<List<Double>>, // List of [longitude, latitude]
    val type: String
)
