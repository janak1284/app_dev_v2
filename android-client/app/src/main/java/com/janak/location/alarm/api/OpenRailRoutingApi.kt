package com.janak.location.alarm.api

import com.janak.location.alarm.model.ORRRouteResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenRailRoutingApi {
    @GET("route")
    suspend fun getTrackGeometry(
        @Query("point") points: List<String>, // format: ["lat,lon", "lat,lon", ...]
        @Query("profile") profile: String = "all_tracks",
        @Query("points_encoded") encoded: Boolean = true,
        @Query("type") type: String = "json"
    ): Response<ORRRouteResponse>

    @GET("nearest")
    suspend fun getNearestPoint(
        @Query("point") point: String, // format: "lat,lon"
        @Query("profile") profile: String = "all_tracks"
    ): Response<ORRNearestResponse>
}

@Serializable
data class ORRNearestResponse(
    @SerialName("coordinates") val coordinates: List<Double>, // [lon, lat]
    @SerialName("distance") val distance: Double
)
