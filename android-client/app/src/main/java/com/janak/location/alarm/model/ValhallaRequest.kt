package com.janak.location.alarm.model

import kotlinx.serialization.Serializable

@Serializable
data class ValhallaRequest(
    val locations: List<ValhallaLocation>,
    val costing: String = "multimodal",
    val costing_options: ValhallaCostingOptions? = null,
    val directions_options: ValhallaDirectionsOptions? = ValhallaDirectionsOptions()
)

@Serializable
data class ValhallaCostingOptions(
    val transit: ValhallaTransitOptions? = null
)

@Serializable
data class ValhallaTransitOptions(
    val use_bus: Double = 0.5,
    val use_rail: Double = 0.5,
    val use_transfers: Double = 0.5
)

@Serializable
data class ValhallaDirectionsOptions(
    val units: String = "kilometers"
)
