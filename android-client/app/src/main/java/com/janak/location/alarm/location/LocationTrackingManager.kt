package com.janak.location.alarm.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.HandlerThread
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
    context: Context
) {
    private val client: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    
    // Background thread for handling location callbacks
    private val handlerThread = HandlerThread("LocationHandlerThread").apply { start() }
    private val backgroundLooper = handlerThread.looper
    
    private var currentInterval = 5000L
    private var currentPriority = Priority.PRIORITY_HIGH_ACCURACY
    private var currentLocationCallback: LocationCallback? = null

    @SuppressLint("MissingPermission") // Permissions are handled in UI
    fun getLocationUpdates(): Flow<Location> = callbackFlow {
        android.util.Log.d("LocationTracker", "getLocationUpdates: Starting flow")
        val request = LocationRequest.Builder(currentPriority, currentInterval)
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
        
        currentLocationCallback = callback

        try {
            android.util.Log.d("LocationTracker", "requestLocationUpdates: Requesting updates...")
            client.requestLocationUpdates(request, callback, backgroundLooper)
        } catch (e: Exception) {
            android.util.Log.e("LocationTracker", "requestLocationUpdates Error", e)
            close(e)
        }

        awaitClose {
            android.util.Log.d("LocationTracker", "awaitClose: Removing updates")
            client.removeLocationUpdates(callback)
            currentLocationCallback = null
        }
    }

    @SuppressLint("MissingPermission")
    fun updateInterval(intervalMillis: Long, priority: Int = Priority.PRIORITY_HIGH_ACCURACY) {
        if (currentInterval == intervalMillis && currentPriority == priority) return
        currentInterval = intervalMillis
        currentPriority = priority
        val callback = currentLocationCallback ?: return
        
        android.util.Log.d("LocationTracker", "updateInterval: New interval $intervalMillis ms, Priority: $priority")
        val request = LocationRequest.Builder(currentPriority, currentInterval)
            .setMinUpdateDistanceMeters(if (intervalMillis > 10000L) 100f else 10f)
            .build()
            
        try {
            client.requestLocationUpdates(request, callback, backgroundLooper)
        } catch (e: Exception) {
            android.util.Log.e("LocationTracker", "updateInterval Error", e)
        }
    }
}
