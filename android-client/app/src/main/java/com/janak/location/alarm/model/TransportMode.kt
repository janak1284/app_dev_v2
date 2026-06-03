package com.janak.location.alarm.model

import kotlinx.serialization.Serializable

@Serializable
enum class TransportMode {
    ROAD,
    WALK,
    BUS,
    TRAIN,
    SUBWAY,
    TRAM,
    FERRY
}
