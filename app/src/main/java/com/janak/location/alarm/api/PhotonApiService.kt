package com.janak.location.alarm.api

import com.janak.location.alarm.model.OsrmResponse
import com.janak.location.alarm.model.PhotonResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for both Photon (search) and OSRM (routing) APIs.
 */
interface PhotonApiService {

    companion object {
        const val BASE_URL = "http://router.project-osrm.org/"
    }

    // OSRM Routing
    @GET("route/v1/driving/{coordinates}")
    suspend fun getRoute(
        @Path("coordinates") coordinates: String,
        @Query("overview") overview: String = "full",
        @Query("geometries") geometries: String = "geojson"
    ): Response<OsrmResponse>

    // Photon Search (Placeholder, needs correct base URL if different)
    @GET("https://photon.komoot.io/api/")
    suspend fun getSuggestions(
        @Query("q") query: String,
        @Query("lat") lat: Double?,
        @Query("lon") lon: Double?,
        @Query("limit") limit: Int = 5
    ): Response<PhotonResponse>
}
