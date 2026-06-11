package com.janak.location.alarm.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Query
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import com.janak.location.alarm.BuildConfig

@Serializable
data class TelemetryResponse(
    @SerialName("train_number") val trainNumber: String,
    @SerialName("eta_string") val etaString: String? = null,
    @SerialName("station_sequence") val stationSequence: List<StationSequenceItem>,
    @SerialName("cache_hit") val cacheHit: Boolean,
    @SerialName("timestamp_fetched") val timestampFetched: Long? = null,
    @SerialName("server_time") val serverTime: Long? = null,
    @SerialName("last_updated_website_ms") val lastUpdatedWebsiteMs: Long? = null
)

@Serializable
data class StationSequenceItem(
    @SerialName("station_name") val stationName: String? = null,
    @SerialName("station_code") val stationCode: String,
    @SerialName("sequence_index") val sequenceIndex: Int,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("arrival") val arrival: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("has_departed") val hasDeparted: Boolean = false
)

interface RailwayTelemetryApi {
    companion object {
        const val BASE_URL = BuildConfig.BASE_URL
    }

    @GET("api/v4/train/track")
    suspend fun getTrainTelemetry(
        @Query("train_number") trainNumber: String,
        @Query("force_refresh") forceRefresh: Boolean = false,
        @Query("ttl_mins") ttlMins: Int? = null
    ): Response<TelemetryResponse>

    @GET("api/v4/train/route/cache")
    suspend fun getGlobalRouteCache(
        @Query("segment_key") segmentKey: String
    ): Response<RouteResponse>

    @POST("api/v4/train/route/cache")
    suspend fun saveGlobalRouteCache(
        @Body request: RouteCacheSaveRequest
    ): Response<CorrectionResponse>

    @POST("api/v4/stations/correct")
    suspend fun reportCoordinateCorrection(
        @Body correction: CoordinateCorrection
    ): Response<CorrectionResponse>
}

@Serializable
data class RouteCacheSaveRequest(
    @SerialName("segment_key") val segmentKey: String,
    @SerialName("polyline") val polyline: String,
    @SerialName("distance") val distance: Double,
    @SerialName("duration") val duration: Double
)

@Serializable
data class RouteResponse(
    @SerialName("points") val points: String,
    @SerialName("distance") val distance: Double,
    @SerialName("time") val time: Long,
    @SerialName("cache_hit") val cacheHit: Boolean
)

@Serializable
data class CoordinateCorrection(
    @SerialName("station_code") val stationCode: String,
    @SerialName("latitude") val latitude: Double,
    @SerialName("longitude") val longitude: Double
)

@Serializable
data class CorrectionResponse(
    @SerialName("success") val success: Boolean,
    @SerialName("message") val message: String
)
