package com.janak.location.alarm.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.janak.location.alarm.alarm.AlarmEngine
import com.janak.location.alarm.api.RailwayTelemetryApiService
import com.janak.location.alarm.data.AppDatabase
import com.janak.location.alarm.data.repository.RouteRepository
import com.janak.location.alarm.domain.RouteDistanceEngine
import com.janak.location.alarm.location.LocationTrackingManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Service for handling rail-based journey tracking via the Railway Telemetry microservice.
 */
class RailwayTrackingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var locationTrackingManager: LocationTrackingManager
    private lateinit var routeDistanceEngine: RouteDistanceEngine
    private lateinit var routeRepository: RouteRepository
    private lateinit var telemetryApiService: RailwayTelemetryApiService
    private lateinit var alarmEngine: AlarmEngine

    override fun onCreate() {
        super.onCreate()
        locationTrackingManager = LocationTrackingManager(this)
        routeDistanceEngine = RouteDistanceEngine()
        val database = AppDatabase.getDatabase(this)
        routeRepository = RouteRepository(database)
        telemetryApiService = com.janak.location.alarm.api.RetrofitClient.railwayTelemetryApiService
        alarmEngine = AlarmEngine(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val routeGeoJson = intent?.getStringExtra("ROUTE_GEOJSON")
        val distanceThreshold = intent?.getFloatExtra("DISTANCE_THRESHOLD", 500f) ?: 500f
        if (routeGeoJson != null) {
            val route = com.mapbox.geojson.LineString.fromJson(routeGeoJson)
            startTracking(route, distanceThreshold.toDouble())
        }
        return START_STICKY
    }

    private fun startTracking(route: com.mapbox.geojson.LineString, threshold: Double) {
        serviceScope.launch {
            locationTrackingManager.getLocationUpdates().collect { location ->
                val userPoint = com.mapbox.geojson.Point.fromLngLat(location.longitude, location.latitude)
                
                // Snap, Slice, and Measure using the existing engine
                val remainingDistance = routeDistanceEngine.calculateRemainingDistance(route, userPoint)
                
                if (remainingDistance <= threshold) {
                    alarmEngine.start()
                }
            }
        }
    }


    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
