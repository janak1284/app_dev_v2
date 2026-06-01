package com.janak.location.alarm.api

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

object RetrofitClient {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("bypass-tunnel-reminders", "true")
                .build()
            chain.proceed(request)
        }
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val photonApiService: PhotonApiService by lazy {
        Retrofit.Builder()
            .baseUrl(PhotonApiService.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PhotonApiService::class.java)
    }
    
    val osrmApiService: PhotonApiService by lazy {
        Retrofit.Builder()
            .baseUrl(PhotonApiService.OSRM_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(PhotonApiService::class.java)
    }

    val openRailRoutingApi: OpenRailRoutingApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://routing.openrailrouting.org/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenRailRoutingApi::class.java)
    }

    val railwayTelemetryApi: RailwayTelemetryApi by lazy {
        Retrofit.Builder()
            .baseUrl(RailwayTelemetryApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(RailwayTelemetryApi::class.java)
    }
}
