package com.janak.location.alarm.api

import com.janak.location.alarm.model.ValhallaResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface ValhallaApiService {
    companion object {
        const val BASE_URL = "https://valhalla1.openstreetmap.de/"
    }

    @GET("route")
    suspend fun getRoute(
        @Query("json") jsonRequest: String
    ): Response<ValhallaResponse>
}
