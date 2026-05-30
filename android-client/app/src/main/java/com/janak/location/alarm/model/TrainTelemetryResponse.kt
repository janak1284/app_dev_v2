package com.janak.location.alarm.model

import kotlinx.serialization.Serializable

@Serializable
data class TrainTelemetryResponse(
    val trainId: String,
    val etaTime: String,
    val stationSequence: List<String>
)
