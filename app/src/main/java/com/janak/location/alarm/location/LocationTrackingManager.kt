package com.janak.location.alarm.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class LocationTrackingManager(
    private val context: Context
) {
    private val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission") // Permissions are handled in UI
    fun getLocationUpdates(): Flow<Location> = callbackFlow {
        android.util.Log.d("LocationTracker", "getLocationUpdates: Starting flow")
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateDistanceMeters(10f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    android.util.Log.d("LocationTracker", "onLocationResult: Lat=${it.latitude}, Lon=${it.longitude}")
                    trySend(it)
                }
            }
        }

        try {
            android.util.Log.d("LocationTracker", "requestLocationUpdates: Requesting updates...")
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: Exception) {
            android.util.Log.e("LocationTracker", "requestLocationUpdates Error", e)
            close(e)
        }

        awaitClose {
            android.util.Log.d("LocationTracker", "awaitClose: Removing updates")
            client.removeLocationUpdates(callback)
        }
    }
}
