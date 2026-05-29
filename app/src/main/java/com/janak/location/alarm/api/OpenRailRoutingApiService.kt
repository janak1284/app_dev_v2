package com.janak.location.alarm.api

import com.janak.location.alarm.model.GraphHopperResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * OpenRailRouting API (GraphHopper-based) for railway routing.
 * Hosted at https://routing.openrailrouting.org/
 */
interface OpenRailRoutingApiService {

    companion object {
        const val BASE_URL = "https://routing.openrailrouting.org/"
    }

    @GET("route")
    suspend fun getRoute(
        @Query("point") points: List<String>, // Format: "lat,lon"
        @Query("profile") profile: String = "all_tracks",
        @Query("points_encoded") pointsEncoded: Boolean = false,
        @Query("type") type: String = "json",
        @Query("instructions") instructions: Boolean = true,
        @Query("locale") locale: String = "en"
    ): Response<GraphHopperResponse>
}
