package com.janak.location.alarm.api

import com.janak.location.alarm.model.TrainTelemetryResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * API service for the Hugging Face Railway Telemetry microservice.
 */
interface RailwayTelemetryApiService {

    companion object {
        // Placeholder for the Hugging Face Space URL
        const val BASE_URL = "https://your-huggingface-space-url.hf.space/"
    }

    @GET("api/track")
    suspend fun getTrainTelemetry(
        @Query("train") trainId: String
    ): Response<TrainTelemetryResponse>
}
