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
        const val BASE_URL = "https://photon.komoot.io/"
        const val OSRM_BASE_URL = "https://router.project-osrm.org/"
    }

    // OSRM Routing
    @GET("route/v1/driving/{coordinates}")
    suspend fun getRoute(
        @Path("coordinates") coordinates: String,
        @Query("overview") overview: String = "full",
        @Query("geometries") geometries: String = "geojson",
        @Query("annotations") annotations: String = "duration,distance,speed"
    ): Response<OsrmResponse>

    // Photon Search
    @GET("api/")
    suspend fun getSuggestions(
        @Query("q") query: String,
        @Query("lat") lat: Double?,
        @Query("lon") lon: Double?,
        @Query("limit") limit: Int = 5
    ): Response<PhotonResponse>
}
