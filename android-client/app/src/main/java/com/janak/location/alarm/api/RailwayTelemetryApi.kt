package com.janak.location.alarm.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class TelemetryResponse(
    @SerialName("train_number") val trainNumber: String,
    @SerialName("eta_string") val etaString: String? = null,
    @SerialName("station_sequence") val stationSequence: List<StationSequenceItem>,
    @SerialName("cache_hit") val cacheHit: Boolean
)

@Serializable
data class StationSequenceItem(
    @SerialName("station_code") val stationCode: String,
    @SerialName("sequence_index") val sequenceIndex: Int,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null
)

interface RailwayTelemetryApi {
    companion object {
        const val BASE_URL = "https://coil-pledge-dares.ngrok-free.dev/"
    }

    @GET("api/v4/train/track")
    suspend fun getTrainTelemetry(
        @Query("train_number") trainNumber: String
    ): Response<TelemetryResponse>
}
