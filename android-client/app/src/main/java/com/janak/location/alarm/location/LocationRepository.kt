package com.janak.location.alarm.location

import android.location.Location
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    fun getLocationUpdates(): Flow<Location>
    fun updateInterval(intervalMillis: Long, priority: Int)
}
