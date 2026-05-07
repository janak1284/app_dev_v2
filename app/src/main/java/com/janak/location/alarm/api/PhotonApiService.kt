package com.janak.location.alarm.api

import com.janak.location.alarm.model.PhotonResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface PhotonApiService {
    @GET("api")
    suspend fun getSuggestions(
        @Query("q") query: String,
        @Query("limit") limit: Int = 10,
        @Query("lat") lat: Double? = null,
        @Query("lon") lon: Double? = null
    ): PhotonResponse

    companion object {
        const val BASE_URL = "https://photon.komoot.io/"
    }
}
