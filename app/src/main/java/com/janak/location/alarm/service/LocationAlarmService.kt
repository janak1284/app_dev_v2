package com.janak.location.alarm.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.janak.location.alarm.MainActivity
import com.janak.location.alarm.R
import com.janak.location.alarm.alarm.AlarmEngine
import com.janak.location.alarm.location.LocationTrackingManager
import com.janak.location.alarm.data.AppDatabase
import com.janak.location.alarm.data.entity.RouteBreadcrumbEntity
import com.janak.location.alarm.data.entity.SavedRouteEntity
import com.janak.location.alarm.data.repository.RouteRepository
import com.janak.location.alarm.domain.RouteDistanceEngine
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import kotlinx.coroutines.*
import kotlin.math.roundToInt

class LocationAlarmService : Service() {

    enum class ServiceState {
        IDLE, TRACKING, ALARM_RINGING
    }

    private var currentState = ServiceState.IDLE
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var locationTrackingManager: LocationTrackingManager
    private lateinit var alarmEngine: AlarmEngine
    private lateinit var routeDistanceEngine: RouteDistanceEngine
    private lateinit var routeRepository: RouteRepository
    
    private var trackingJob: Job? = null
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    private var destinationLat: Double = 0.0
    private var destinationLng: Double = 0.0
    private var destinationName: String = "Unknown Destination"
    private var distanceThreshold: Float = 500f
    private var ringtoneUri: String? = null
    private var isVibrateEnabled: Boolean = true
    
    private var currentRoute: LineString? = null
    private val locationBuffer = mutableListOf<Location>()

    companion object {
        const val ACTION_STOP_ALARM = "com.janak.location.alarm.ACTION_STOP_ALARM"
        const val ACTION_END_JOURNEY = "com.janak.location.alarm.ACTION_END_JOURNEY"
        const val ACTION_UPDATE_ROUTE = "com.janak.location.alarm.ACTION_UPDATE_ROUTE"
        const val ACTION_RE_ROUTE = "com.janak.location.alarm.ACTION_RE_ROUTE"
        const val JOURNEY_COMPLETED_BROADCAST = "com.janak.location.alarm.JOURNEY_COMPLETED"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "LocationAlarmChannel"
    }

    override fun onCreate() {
        super.onCreate()
        locationTrackingManager = LocationTrackingManager(this)
        alarmEngine = AlarmEngine(this)
        routeDistanceEngine = RouteDistanceEngine()
        val database = AppDatabase.getDatabase(this)
        routeRepository = RouteRepository(database)
        
        createNotificationChannels()
        
        val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = powerManager.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK,
            "LocationAlarmService::WakeLock"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ALARM -> {
                stopAlarmRinging()
                return START_STICKY
            }
            ACTION_END_JOURNEY, "STOP_ALARM" -> {
                // Signal journey completion for UI dialog
                sendBroadcast(Intent(JOURNEY_COMPLETED_BROADCAST))
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_ROUTE -> {
                val routeGeoJson = intent.getStringExtra("ROUTE_GEOJSON")
                if (routeGeoJson != null) {
                    try {
                        currentRoute = LineString.fromJson(routeGeoJson)
                        android.util.Log.d("LocationAlarmService", "Route updated successfully")
                    } catch (e: Exception) {
                        android.util.Log.e("LocationAlarmService", "Failed to parse updated GeoJSON", e)
                    }
                }
                return START_STICKY
            }
        }
        
        wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes timeout

        destinationLat = intent?.getDoubleExtra("DEST_LAT", 0.0) ?: 0.0
        destinationLng = intent?.getDoubleExtra("DEST_LNG", 0.0) ?: 0.0
        destinationName = intent?.getStringExtra("DEST_NAME") ?: "Unknown Destination"
        distanceThreshold = intent?.getFloatExtra("DISTANCE_THRESHOLD", 500f) ?: 500f
        ringtoneUri = intent?.getStringExtra("RINGTONE_URI")
        isVibrateEnabled = intent?.getBooleanExtra("VIBRATE", true) ?: true
        
        val routeGeoJson = intent?.getStringExtra("ROUTE_GEOJSON")
        if (routeGeoJson != null) {
            try {
                currentRoute = LineString.fromJson(routeGeoJson)
            } catch (e: Exception) {
                android.util.Log.e("LocationAlarmService", "Failed to parse GeoJSON", e)
            }
        }

        currentState = ServiceState.TRACKING
        startForeground(NOTIFICATION_ID, createNotification("Distance Alarm Active", "Monitoring distance to destination..."))
        startLocationTracking()

        return START_STICKY
    }

    private fun startLocationTracking() {
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        trackingJob?.cancel()
        trackingJob = serviceScope.launch {
            locationTrackingManager.getLocationUpdates().collect { location ->
                processLocationUpdate(location)
            }
        }
    }

    private fun processLocationUpdate(location: Location) {
        // Extend wake lock on each update
        wakeLock?.acquire(10 * 60 * 1000L)
        
        // Buffer the location for breadcrumbs
        locationBuffer.add(location)
        
        var distance: Double
        var etaMinutes: Double = Double.MAX_VALUE
        
        val route = currentRoute
        if (route != null) {
            val userPoint = Point.fromLngLat(location.longitude, location.latitude)
            
            // 1. Calculate route-based distance
            distance = routeDistanceEngine.calculateRemainingDistance(route, userPoint)
            
            // 2. Check for route deviation (> 100m)
            val deviation = routeDistanceEngine.calculateDeviation(route, userPoint)
            if (deviation > 100) {
                android.util.Log.w("LocationAlarmService", "Route deviation detected: ${deviation}m. Requesting re-route.")
                val reRouteIntent = Intent(ACTION_RE_ROUTE)
                sendBroadcast(reRouteIntent)
            }
            
            // 3. Update speed and calculate ETA
            routeDistanceEngine.updateAverageSpeed(location.speed.toDouble())
            etaMinutes = routeDistanceEngine.calculateDynamicETA(distance)
            
        } else {
            // Fallback to Haversine if no route is available
            val results = FloatArray(1)
            Location.distanceBetween(
                location.latitude, location.longitude,
                destinationLat, destinationLng,
                results
            )
            distance = results[0].toDouble()
        }
        
        val etaText = if (etaMinutes != Double.MAX_VALUE) {
            " | ETA: ${etaMinutes.roundToInt()} min"
        } else ""
        
        val notificationContent = "Distance: ${formatDistance(distance.toInt())}$etaText"
        updateNotification("Distance Alarm Active", notificationContent)

        if (currentState == ServiceState.TRACKING && distance <= distanceThreshold) {
            triggerAlarm()
        }
    }

    private fun triggerAlarm() {
        currentState = ServiceState.ALARM_RINGING
        
        val ringingIntent = Intent(this, com.janak.location.alarm.RingingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("RINGTONE_URI", ringtoneUri)
            putExtra("VIBRATE", isVibrateEnabled)
        }

        try {
            startActivity(ringingIntent)
        } catch (e: Exception) {
            android.util.Log.e("LocationAlarmService", "Failed to start RingingActivity", e)
        }

        updateNotification("Arrived!", "You are within ${formatDistance(distanceThreshold.toInt())} of $destinationName")
    }

    private fun stopAlarmRinging() {
        if (currentState == ServiceState.ALARM_RINGING) {
            currentState = ServiceState.TRACKING
            updateNotification("Distance Alarm Active", "Monitoring after arrival...")
        }
    }

    private fun updateNotification(title: String, content: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(title, content))
    }

    private fun createNotification(title: String, content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, LocationAlarmService::class.java).apply {
            action = ACTION_END_JOURNEY
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_location_pin, "End Journey", stopPendingIntent)
            .build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Location Alarm Service",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(serviceChannel)

            val alarmChannel = NotificationChannel(
                "AlarmRingingChannel",
                "Alarm Triggered",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            manager.createNotificationChannel(alarmChannel)
        }
    }

    private fun formatDistance(meters: Int): String {
        return if (meters >= 1000) {
            String.format("%.1fkm", meters / 1000f)
        } else {
            "${meters}m"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        trackingJob?.cancel()
        saveJourneyAndCleanup()
        serviceScope.cancel()
        alarmEngine.stop()
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    private fun saveJourneyAndCleanup() {
        if (locationBuffer.isEmpty()) return

        val finalLocations = locationBuffer.toList()
        
        serviceScope.launch(NonCancellable) {
            try {
                val routeMetadata = SavedRouteEntity(
                    destinationName = destinationName,
                    dateSaved = System.currentTimeMillis()
                )
                
                val breadcrumbs = finalLocations.map { loc ->
                    RouteBreadcrumbEntity(
                        routeId = 0,
                        latitude = loc.latitude,
                        longitude = loc.longitude,
                        speed = loc.speed,
                        timestamp = loc.time
                    )
                }
                
                routeRepository.saveJourney(routeMetadata, breadcrumbs)
                android.util.Log.d("LocationAlarmService", "Journey saved successfully: ${breadcrumbs.size} points")
            } catch (e: Exception) {
                android.util.Log.e("LocationAlarmService", "Failed to save journey", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
