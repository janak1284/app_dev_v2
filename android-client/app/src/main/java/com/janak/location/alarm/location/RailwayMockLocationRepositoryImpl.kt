package com.janak.location.alarm.location

import android.content.Context
import android.location.Location
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.math.abs

class RailwayMockLocationRepositoryImpl(
    private val context: Context
) : LocationRepository {

    override fun getLocationUpdates(): Flow<Location> = flow {
        val prefs = context.getSharedPreferences("service_state", Context.MODE_PRIVATE)
        val destLat = prefs.getFloat("dest_lat", 18.9696f).toDouble()
        val destLon = prefs.getFloat("dest_lng", 72.8193f).toDouble()

        // Start ~15 km away to simulate approaching train
        var currentLat = destLat - 0.12
        var currentLon = destLon - 0.12

        while (true) {
            val location = Location("RailwayDemoMockProvider").apply {
                latitude = currentLat
                longitude = currentLon
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                accuracy = 5.0f
                speed = 35.0f // ~126 km/h simulation
            }
            emit(location)

            // Move closer towards destLat, destLon in increments (time compressed demo)
            val step = 0.003 // moves ~400m per second for rapid testing
            if (abs(destLat - currentLat) > step) {
                currentLat += if (destLat > currentLat) step else -step
            } else {
                currentLat = destLat
            }
            if (abs(destLon - currentLon) > step) {
                currentLon += if (destLon > currentLon) step else -step
            } else {
                currentLon = destLon
            }

            delay(1000L)
        }
    }

    override fun updateInterval(intervalMillis: Long, priority: Int) {
        // No-op for demo
    }
}
