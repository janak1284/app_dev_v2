package com.janak.location.alarm.model

import kotlinx.serialization.Serializable

@Serializable
data class GraphHopperResponse(
    val paths: List<GraphHopperPath> = emptyList()
)

@Serializable
data class GraphHopperPath(
    val points: GraphHopperPoints,
    val distance: Double,
    val time: Long,
    val instructions: List<GraphHopperInstruction> = emptyList()
)

@Serializable
data class GraphHopperPoints(
    val coordinates: List<List<Double>> // [[lon, lat], ...]
)

@Serializable
data class GraphHopperInstruction(
    val text: String,
    val distance: Double,
    val time: Long
)
