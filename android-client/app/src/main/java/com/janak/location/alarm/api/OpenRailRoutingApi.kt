package com.janak.location.alarm.api

import com.janak.location.alarm.model.ORRRouteResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface OpenRailRoutingApi {
    @GET("route")
    suspend fun getTrackGeometry(
        @Query("point") startPoint: String, // format: "lat,lon"
        @Query("point") endPoint: String,   // format: "lat,lon"
        @Query("profile") profile: String = "all_tracks",
        @Query("points_encoded") encoded: Boolean = true,
        @Query("type") type: String = "json"
    ): Response<ORRRouteResponse>
}
