package com.janak.location.alarm.model

import kotlinx.serialization.Serializable

@Serializable
data class PhotonResponse(
    val features: List<PhotonFeature>
)

@Serializable
data class PhotonFeature(
    val geometry: PhotonGeometry,
    val properties: PhotonProperties
)

@Serializable
data class PhotonGeometry(
    val coordinates: List<Double> // [longitude, latitude]
)

@Serializable
data class PhotonProperties(
    val name: String? = null,
    val street: String? = null,
    val city: String? = null,
    val country: String? = null,
    val state: String? = null,
    val postcode: String? = null
) {
    val displayName: String
        get() = listOfNotNull(name, street, city, country)
            .distinct()
            .joinToString(", ")
}
