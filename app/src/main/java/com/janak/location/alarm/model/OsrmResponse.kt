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
    val distance: Double,
    val legs: List<OsrmLeg> = emptyList()
)

@OptIn(InternalSerializationApi::class)
@Serializable
data class OsrmLeg(
    val annotation: OsrmAnnotation? = null,
    val duration: Double,
    val distance: Double,
    val summary: String = ""
)

@OptIn(InternalSerializationApi::class)
@Serializable
data class OsrmAnnotation(
    val speed: List<Double> = emptyList(),
    val duration: List<Double> = emptyList(),
    val distance: List<Double> = emptyList()
)

@OptIn(InternalSerializationApi::class)
@Serializable
data class OsrmGeometry(
    val coordinates: List<List<Double>>, // List of [longitude, latitude]
    val type: String
)
