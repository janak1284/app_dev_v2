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
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000L)
            .setMinUpdateDistanceMeters(10f)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let {
                    trySend(it)
                }
            }
        }

        client.requestLocationUpdates(request, callback, Looper.getMainLooper())

        awaitClose {
            client.removeLocationUpdates(callback)
        }
    }
}
