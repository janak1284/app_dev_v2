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
import com.janak.location.alarm.data.entity.JourneyHistoryEntity
import com.janak.location.alarm.data.entity.RouteBreadcrumbEntity
import com.janak.location.alarm.data.entity.SavedRouteEntity
import com.janak.location.alarm.data.repository.HistoryRepository
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
    private lateinit var historyRepository: HistoryRepository
    
    private var trackingJob: Job? = null
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    private var destinationLat: Double = 0.0
    private var destinationLng: Double = 0.0
    private var destinationName: String = "Unknown Destination"
    private var distanceThreshold: Float = 500f
    private var isDistanceAlarmEnabled: Boolean = true
    private var predictiveMinutesThreshold: Int = 10
    private var isPredictiveAlarmEnabled: Boolean = false
    private var ringtoneUri: String? = null
    private var isVibrateEnabled: Boolean = true
    private var hasSentArrivalBroadcast = false
    private var isAlarmSilenced = false
    private var startTimeMillis: Long = 0
    
    private var currentRoute: LineString? = null
    private var expectedSpeedMps: Double = 0.0
    private var historyId: Long = -1L

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
        historyRepository = HistoryRepository(database)
        
        createNotificationChannels()
        
        val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = powerManager.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK,
            "LocationAlarmService::WakeLock"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // Attempt to recover state from preferences or stop
            val sharedPrefs = getSharedPreferences("service_state", MODE_PRIVATE)
            val isTracking = sharedPrefs.getBoolean("is_tracking", false)
            if (isTracking && currentState == ServiceState.IDLE) {
                recoverState(sharedPrefs)
                return START_STICKY
            }
            if (currentState == ServiceState.IDLE) stopSelf()
            return START_STICKY
        }

        when (intent.action) {
            ACTION_STOP_ALARM -> {
                stopAlarmRinging()
                return START_STICKY
            }
            ACTION_END_JOURNEY, "STOP_ALARM" -> {
                android.util.Log.d("LocationAlarmService", "Action End Journey received. Stopping service.")
                sendBroadcast(Intent(JOURNEY_COMPLETED_BROADCAST).setPackage(packageName))
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_ROUTE -> {
                val routeGeoJson = intent.getStringExtra("ROUTE_GEOJSON")
                val duration = intent.getDoubleExtra("EXPECTED_DURATION", 0.0)
                val distance = intent.getDoubleExtra("EXPECTED_DISTANCE", 0.0)
                
                if (routeGeoJson != null) {
                    try {
                        currentRoute = LineString.fromJson(routeGeoJson)
                        if (duration > 0 && distance > 0) {
                            expectedSpeedMps = distance / duration
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("LocationAlarmService", "Failed to parse updated GeoJSON", e)
                    }
                }
                return START_STICKY
            }
        }
        
        // Initializing Journey (Only if not already tracking)
        if (currentState == ServiceState.IDLE) {
            wakeLock?.acquire(30 * 60 * 1000L)

            destinationLat = intent.getDoubleExtra("DEST_LAT", 0.0)
            destinationLng = intent.getDoubleExtra("DEST_LNG", 0.0)
            val passedName = intent.getStringExtra("DEST_NAME")
            destinationName = if (!passedName.isNullOrBlank()) passedName else "Unknown Destination"
            
            distanceThreshold = intent.getFloatExtra("DISTANCE_THRESHOLD", 500f)
            isDistanceAlarmEnabled = intent.getBooleanExtra("DISTANCE_ALARM_ENABLED", true)
            predictiveMinutesThreshold = intent.getIntExtra("PREDICTIVE_MINUTES", 10)
            isPredictiveAlarmEnabled = intent.getBooleanExtra("PREDICTIVE_ALARM_ENABLED", false)
            ringtoneUri = intent.getStringExtra("RINGTONE_URI")
            isVibrateEnabled = intent.getBooleanExtra("VIBRATE", true)
            isAlarmSilenced = false
            
            val routeGeoJson = intent.getStringExtra("ROUTE_GEOJSON")
            val initialDuration = intent.getDoubleExtra("EXPECTED_DURATION", 0.0)
            val initialDistance = intent.getDoubleExtra("EXPECTED_DISTANCE", 0.0)

            if (routeGeoJson != null) {
                try {
                    currentRoute = LineString.fromJson(routeGeoJson)
                    if (initialDuration > 0 && initialDistance > 0) {
                        expectedSpeedMps = initialDistance / initialDuration
                    }
                } catch (e: Exception) {}
            }

            currentState = ServiceState.TRACKING
            startTimeMillis = System.currentTimeMillis()
            
            saveStateToPrefs()
            
            // Create an initial history entry to get an ID for breadcrumbs
            serviceScope.launch {
                val initialHistory = JourneyHistoryEntity(
                    routeId = null,
                    startingPointLat = 0.0, // Will be updated on first location
                    startingPointLng = 0.0,
                    destinationLat = destinationLat,
                    destinationLng = destinationLng,
                    destinationName = destinationName,
                    mapDestinationName = null,
                    alarmConfigAtTime = com.janak.location.alarm.model.AlarmSettings(
                        distanceMeters = distanceThreshold.toInt(),
                        isDistanceAlarmEnabled = isDistanceAlarmEnabled,
                        predictiveMinutes = predictiveMinutesThreshold,
                        isPredictiveAlarmEnabled = isPredictiveAlarmEnabled,
                        ringtoneUri = ringtoneUri?.let { try { android.net.Uri.parse(it) } catch(e: Exception) { null } },
                        isVibrateEnabled = isVibrateEnabled
                    ),
                    timestamp = startTimeMillis
                )
                historyId = historyRepository.saveJourneyLog(initialHistory, emptyList())
                android.util.Log.d("LocationAlarmService", "Initial history created with ID: $historyId")
            }

            startForeground(NOTIFICATION_ID, createNotification("Distance Alarm Active", "Monitoring distance to destination..."))
            startLocationTracking()
        } else {
            // Update settings
            distanceThreshold = intent.getFloatExtra("DISTANCE_THRESHOLD", distanceThreshold)
            isDistanceAlarmEnabled = intent.getBooleanExtra("DISTANCE_ALARM_ENABLED", isDistanceAlarmEnabled)
            predictiveMinutesThreshold = intent.getIntExtra("PREDICTIVE_MINUTES", predictiveMinutesThreshold)
            isPredictiveAlarmEnabled = intent.getBooleanExtra("PREDICTIVE_ALARM_ENABLED", isPredictiveAlarmEnabled)
            saveStateToPrefs()
        }

        return START_STICKY
    }

    private fun saveStateToPrefs() {
        getSharedPreferences("service_state", MODE_PRIVATE).edit().apply {
            putBoolean("is_tracking", true)
            putLong("start_time", startTimeMillis)
            putFloat("dest_lat", destinationLat.toFloat())
            putFloat("dest_lng", destinationLng.toFloat())
            putString("dest_name", destinationName)
            putFloat("dist_threshold", distanceThreshold)
            putBoolean("dist_enabled", isDistanceAlarmEnabled)
            putInt("pred_threshold", predictiveMinutesThreshold)
            putBoolean("pred_enabled", isPredictiveAlarmEnabled)
            putString("ringtone", ringtoneUri)
            putBoolean("vibrate", isVibrateEnabled)
            putLong("history_id", historyId)
            apply()
        }
    }

    private fun recoverState(prefs: android.content.SharedPreferences) {
        startTimeMillis = prefs.getLong("start_time", System.currentTimeMillis())
        destinationLat = prefs.getFloat("dest_lat", 0f).toDouble()
        destinationLng = prefs.getFloat("dest_lng", 0f).toDouble()
        destinationName = prefs.getString("dest_name", "Unknown") ?: "Unknown"
        distanceThreshold = prefs.getFloat("dist_threshold", 500f)
        isDistanceAlarmEnabled = prefs.getBoolean("dist_enabled", true)
        predictiveMinutesThreshold = prefs.getInt("pred_threshold", 10)
        isPredictiveAlarmEnabled = prefs.getBoolean("pred_enabled", false)
        ringtoneUri = prefs.getString("ringtone", null)
        isVibrateEnabled = prefs.getBoolean("vibrate", true)
        historyId = prefs.getLong("history_id", -1L)
        
        currentState = ServiceState.TRACKING
        startForeground(NOTIFICATION_ID, createNotification("Distance Alarm Active", "Recovered tracking..."))
        startLocationTracking()
    }

    private fun startLocationTracking() {
        trackingJob?.cancel()
        trackingJob = serviceScope.launch {
            locationTrackingManager.getLocationUpdates().collect { location ->
                processLocationUpdate(location)
            }
        }
    }

    private fun processLocationUpdate(location: Location) {
        wakeLock?.acquire(60 * 60 * 1000L)
        
        // Persist breadcrumb immediately to database
        if (historyId != -1L) {
            serviceScope.launch {
                historyRepository.saveJourneyLog(
                    id = historyId,
                    breadcrumb = RouteBreadcrumbEntity(
                        historyId = historyId,
                        latitude = location.latitude,
                        longitude = location.longitude,
                        speed = location.speed,
                        timestamp = location.time
                    )
                )
            }
        }
        
        var distance: Double
        var etaMinutes: Double = Double.MAX_VALUE
        
        val route = currentRoute
        if (route != null) {
            val userPoint = Point.fromLngLat(location.longitude, location.latitude)
            distance = routeDistanceEngine.calculateRemainingDistance(route, userPoint)
            
            val deviation = routeDistanceEngine.calculateDeviation(route, userPoint)
            if (deviation > 100) {
                sendBroadcast(Intent(ACTION_RE_ROUTE).setPackage(packageName))
            }
            
            routeDistanceEngine.updateAverageSpeed(location.speed.toDouble())
            etaMinutes = routeDistanceEngine.calculateCalibratedETA(distance, expectedSpeedMps)
        } else {
            val results = FloatArray(1)
            Location.distanceBetween(location.latitude, location.longitude, destinationLat, destinationLng, results)
            distance = results[0].toDouble()
        }
        
        updateNotification("Distance Alarm Active", "Distance: ${formatDistance(distance.toInt())}${if (etaMinutes != Double.MAX_VALUE) " | ETA: ${etaMinutes.roundToInt()} min" else ""}")

        if (distance <= 50 && !hasSentArrivalBroadcast) {
            hasSentArrivalBroadcast = true
            sendBroadcast(Intent(JOURNEY_COMPLETED_BROADCAST).setPackage(packageName))
        }

        if (currentState == ServiceState.TRACKING && !isAlarmSilenced) {
            if ((isDistanceAlarmEnabled && distance <= distanceThreshold) || 
                (isPredictiveAlarmEnabled && etaMinutes <= predictiveMinutesThreshold)) {
                triggerAlarm()
            }
        }
    }

    private fun triggerAlarm() {
        currentState = ServiceState.ALARM_RINGING
        val ringingIntent = Intent(this, com.janak.location.alarm.RingingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("RINGTONE_URI", ringtoneUri)
            putExtra("VIBRATE", isVibrateEnabled)
        }
        try { startActivity(ringingIntent) } catch (e: Exception) {}
        updateNotification("Arrived!", "You are within ${formatDistance(distanceThreshold.toInt())} of $destinationName")
    }

    private fun stopAlarmRinging() {
        if (currentState == ServiceState.ALARM_RINGING) {
            currentState = ServiceState.TRACKING
            isAlarmSilenced = true
            updateNotification("Distance Alarm Active", "Monitoring after arrival...")
        }
    }

    private fun updateNotification(title: String, content: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(title, content))
    }

    private fun createNotification(title: String, content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stopIntent = Intent(this, LocationAlarmService::class.java).apply { action = ACTION_END_JOURNEY }
        val stopPendingIntent = PendingIntent.getService(this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title).setContentText(content).setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent).setOngoing(true)
            .addAction(R.drawable.ic_location_pin, "End Journey", stopPendingIntent).build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Location Alarm Service", NotificationManager.IMPORTANCE_LOW))
            manager.createNotificationChannel(NotificationChannel("AlarmRingingChannel", "Alarm Triggered", NotificationManager.IMPORTANCE_HIGH).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            })
        }
    }

    private fun formatDistance(meters: Int): String {
        return if (meters >= 1000) String.format("%.1fkm", meters / 1000f) else "${meters}m"
    }

    override fun onDestroy() {
        super.onDestroy()
        trackingJob?.cancel()
        saveFinalSummary()
        getSharedPreferences("service_state", MODE_PRIVATE).edit().clear().apply()
        serviceScope.cancel()
        alarmEngine.stop()
        wakeLock?.let { if (it.isHeld) it.release() }
    }

    private fun saveFinalSummary() {
        if (historyId == -1L) return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Fetch all breadcrumbs for this journey to calculate final distance and generate GeoJSON
                historyRepository.getBreadcrumbsForHistorySync(historyId).let { breadcrumbs ->
                    if (breadcrumbs.isEmpty()) return@let
                    
                    val locations = breadcrumbs.map { 
                        Location("").apply { 
                            latitude = it.latitude
                            longitude = it.longitude
                            speed = it.speed
                            time = it.timestamp
                        }
                    }
                    
                    val actualDistance = routeDistanceEngine.calculateTotalDistance(locations)
                    val durationMillis = if (startTimeMillis > 0) System.currentTimeMillis() - startTimeMillis else 0
                    
                    val actualRouteGeoJson = if (locations.size >= 2) {
                        try {
                            LineString.fromLngLats(locations.map { Point.fromLngLat(it.longitude, it.latitude) }).toJson()
                        } catch (e: Exception) { null }
                    } else null

                    historyRepository.updateJourneySummary(
                        id = historyId,
                        startLat = locations.first().latitude,
                        startLng = locations.first().longitude,
                        distance = actualDistance,
                        duration = durationMillis,
                        geoJson = actualRouteGeoJson
                    )
                    android.util.Log.d("LocationAlarmService", "Final journey summary updated for ID: $historyId")
                }
            } catch (e: Exception) {
                android.util.Log.e("LocationAlarmService", "Failed to save final summary", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun Double.roundTo(decimals: Int): Double {
        var multiplier = 1.0
        repeat(decimals) { multiplier *= 10 }
        return kotlin.math.round(this * multiplier) / multiplier
    }
}
