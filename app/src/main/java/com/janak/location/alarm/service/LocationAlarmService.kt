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
import com.google.android.gms.location.Priority
import com.janak.location.alarm.MainActivity
import com.janak.location.alarm.R
import com.janak.location.alarm.alarm.AlarmEngine
import com.janak.location.alarm.location.LocationTrackingManager
import com.janak.location.alarm.data.AppDatabase
import com.janak.location.alarm.data.entity.JourneyHistoryEntity
import com.janak.location.alarm.data.entity.JourneyLegEntity
import com.janak.location.alarm.data.entity.RouteBreadcrumbEntity
import com.janak.location.alarm.data.entity.SavedRouteEntity
import com.janak.location.alarm.data.repository.HistoryRepository
import com.janak.location.alarm.data.repository.RouteRepository
import com.janak.location.alarm.domain.RouteDistanceEngine
import com.janak.location.alarm.model.JourneyLeg
import com.janak.location.alarm.model.TransportMode
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import kotlinx.coroutines.*
import kotlin.math.roundToInt

class LocationAlarmService : Service() {

    enum class ServiceState {
        IDLE, TRACKING, ALARM_RINGING, ALARM_TRANSFER, WAITING_FOR_CONNECTION, RECALCULATING
    }

    private var currentState = ServiceState.IDLE
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var locationTrackingManager: LocationTrackingManager
    private lateinit var alarmEngine: AlarmEngine
    private lateinit var routeDistanceEngine: RouteDistanceEngine
    private lateinit var routeRepository: RouteRepository
    private lateinit var historyRepository: HistoryRepository
    
    private var trackingJob: Job? = null
    private var deadReckoningJob: Job? = null
    private var lastLocationTime: Long = 0
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
    private var currentLegs: List<JourneyLeg> = emptyList()
    private var currentLegIndex: Int = 0
    private var expectedSpeedMps: Double = 0.0
    private var segmentSpeeds: List<Double> = emptyList()
    private var historyId: Long = -1L

    companion object {
        const val ACTION_STOP_ALARM = "com.janak.location.alarm.ACTION_STOP_ALARM"
        const val ACTION_END_JOURNEY = "com.janak.location.alarm.ACTION_END_JOURNEY"
        const val ACTION_UPDATE_ROUTE = "com.janak.location.alarm.ACTION_UPDATE_ROUTE"
        const val ACTION_RE_ROUTE = "com.janak.location.alarm.ACTION_RE_ROUTE"
        const val JOURNEY_COMPLETED_BROADCAST = "com.janak.location.alarm.JOURNEY_COMPLETED"
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "LocationAlarmChannel"
        const val GPS_LOSS_THRESHOLD_MS = 15000L // 15 seconds
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

        startDeadReckoningWatchdog()
    }

    private fun startDeadReckoningWatchdog() {
        deadReckoningJob?.cancel()
        deadReckoningJob = serviceScope.launch {
            while (isActive) {
                delay(5000)
                if (currentState != ServiceState.IDLE && lastLocationTime > 0) {
                    val timeSinceLastUpdate = System.currentTimeMillis() - lastLocationTime
                    if (timeSinceLastUpdate > GPS_LOSS_THRESHOLD_MS) {
                        performDeadReckoning()
                    }
                }
            }
        }
    }

    private fun performDeadReckoning() {
        if (currentLegs.isNotEmpty() && currentLegIndex in currentLegs.indices) {
            val leg = currentLegs[currentLegIndex]
            // Estimate based on time
            val elapsedInLeg = System.currentTimeMillis() - (leg.departureTime ?: lastLocationTime)
            val progress = if (leg.durationMillis > 0) elapsedInLeg.toDouble() / leg.durationMillis else 0.0
            
            val estimatedRemainingDistance = leg.distanceMeters * (1.0 - progress).coerceAtLeast(0.0)
            val estimatedETA = if (leg.durationMillis > 0) (leg.durationMillis - elapsedInLeg).toDouble() / 60000.0 else 0.0

            android.util.Log.w("LocationAlarmService", "GPS Lost. Dead Reckoning: Dist=${estimatedRemainingDistance.toInt()}m, ETA=${estimatedETA.toInt()}min")
            
            updateNotification("GPS Lost - Estimating...", "Distance: ${formatDistance(estimatedRemainingDistance.toInt())} | ETA: ${estimatedETA.roundToInt()} min")

            if (!isAlarmSilenced) {
                if ((isDistanceAlarmEnabled && estimatedRemainingDistance <= distanceThreshold) || 
                    (isPredictiveAlarmEnabled && estimatedETA <= predictiveMinutesThreshold)) {
                    triggerAlarm(currentLegIndex < currentLegs.size - 1)
                }
            }
        }
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
                val speeds = intent.getDoubleArrayExtra("SEGMENT_SPEEDS")
                val legsJson = intent.getStringExtra("JOURNEY_LEGS_JSON")
                
                if (speeds != null) {
                    segmentSpeeds = speeds.toList()
                }

                if (legsJson != null) {
                    try {
                        currentLegs = kotlinx.serialization.json.Json.decodeFromString(legsJson)
                        currentLegIndex = 0
                    } catch (e: Exception) {}
                }

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
            val speeds = intent.getDoubleArrayExtra("SEGMENT_SPEEDS")
            val legsJson = intent.getStringExtra("JOURNEY_LEGS_JSON")

            if (speeds != null) {
                segmentSpeeds = speeds.toList()
            }

            if (legsJson != null) {
                try {
                    currentLegs = kotlinx.serialization.json.Json.decodeFromString(legsJson)
                    currentLegIndex = 0
                } catch (e: Exception) {}
            }

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
                
                val legEntities = currentLegs.mapIndexed { index, leg ->
                    JourneyLegEntity(
                        sequenceIndex = index,
                        mode = leg.mode,
                        geometry = leg.geometry,
                        startName = leg.startName,
                        endName = leg.endName,
                        startLat = leg.startLat,
                        startLng = leg.startLng,
                        endLat = leg.endLat,
                        endLng = leg.endLng,
                        distanceMeters = leg.distanceMeters,
                        durationMillis = leg.durationMillis,
                        lineName = leg.lineName,
                        color = leg.color
                    )
                }
                
                historyId = historyRepository.saveJourneyLogWithLegs(initialHistory, emptyList(), legEntities)
                android.util.Log.d("LocationAlarmService", "Initial history created with ID: $historyId and ${legEntities.size} legs")
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
            putInt("leg_index", currentLegIndex)
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
        currentLegIndex = prefs.getInt("leg_index", 0)
        
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
        if (location.hasAccuracy() && location.accuracy > 40.0f) {
            android.util.Log.w("LocationAlarmService", "Ignoring inaccurate GPS point (${location.accuracy}m)")
            return
        }
        
        lastLocationTime = System.currentTimeMillis()
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
        
        val userPoint = Point.fromLngLat(location.longitude, location.latitude)
        
        if (currentLegs.isNotEmpty() && currentLegIndex in currentLegs.indices) {
            val leg = currentLegs[currentLegIndex]
            distance = routeDistanceEngine.calculateRemainingDistance(leg, userPoint)
            
            val deviation = routeDistanceEngine.calculateDeviation(leg, userPoint)
            
            // Missed Transfer Detection: 
            // If waiting for connection but moving away from the next leg's starting path
            val isOffTrack = deviation > 150 // Slightly larger threshold for transit
            
            if (isOffTrack) {
                if (currentState != ServiceState.RECALCULATING) {
                    android.util.Log.w("LocationAlarmService", "Missed transfer or deviation detected. Recalculating...")
                    currentState = ServiceState.RECALCULATING
                    sendBroadcast(Intent(ACTION_RE_ROUTE).setPackage(packageName))
                }
            } else if (currentState == ServiceState.RECALCULATING) {
                currentState = ServiceState.TRACKING
            }
            
            routeDistanceEngine.updateAverageSpeed(location.speed.toDouble())
            
            // For transit legs, we might rely on the leg's duration if OSRM segment speeds aren't available
            etaMinutes = routeDistanceEngine.calculateCalibratedETA(
                remainingDistanceMeters = distance,
                globalExpectedSpeedMps = if (leg.durationMillis > 0) leg.distanceMeters / (leg.durationMillis / 1000.0) else expectedSpeedMps,
                currentSegmentSpeedMps = expectedSpeedMps
            )
        } else if (currentRoute != null) {
            val route = currentRoute!!
            distance = routeDistanceEngine.calculateRemainingDistance(route, userPoint)
            
            val deviation = routeDistanceEngine.calculateDeviation(route, userPoint)
            if (deviation > 100) {
                sendBroadcast(Intent(ACTION_RE_ROUTE).setPackage(packageName))
            }
            
            routeDistanceEngine.updateAverageSpeed(location.speed.toDouble())
            
            val currentSegmentSpeed = routeDistanceEngine.getCurrentSegmentSpeed(route, userPoint, segmentSpeeds)
            
            etaMinutes = routeDistanceEngine.calculateCalibratedETA(
                remainingDistanceMeters = distance,
                globalExpectedSpeedMps = expectedSpeedMps,
                currentSegmentSpeedMps = if (currentSegmentSpeed > 0) currentSegmentSpeed else expectedSpeedMps
            )
        } else {
            val results = FloatArray(1)
            Location.distanceBetween(location.latitude, location.longitude, destinationLat, destinationLng, results)
            distance = results[0].toDouble()
        }
        
        updateNotification("Distance Alarm Active", "Distance: ${formatDistance(distance.toInt())}${if (etaMinutes != Double.MAX_VALUE) " | ETA: ${etaMinutes.roundToInt()} min" else ""}")

        adjustPollingInterval(distance)

        // Dynamic Alarm Reset: If alarm was silenced but we are now far from destination/ETA again, reset it.
        if (isAlarmSilenced) {
            val resetDistance = distanceThreshold * 1.5
            val resetTime = predictiveMinutesThreshold * 1.5
            if (distance > resetDistance && (!isPredictiveAlarmEnabled || etaMinutes > resetTime)) {
                isAlarmSilenced = false
                android.util.Log.d("LocationAlarmService", "Alarm reset due to distance/ETA increase.")
            }
        }

        if (distance <= 50) {
            handleArrivalAtPoint()
        }

        if ((currentState == ServiceState.TRACKING || currentState == ServiceState.WAITING_FOR_CONNECTION) && !isAlarmSilenced) {
            val isDistanceTriggered = isDistanceAlarmEnabled && distance <= distanceThreshold
            val isTimeTriggered = isPredictiveAlarmEnabled && etaMinutes <= predictiveMinutesThreshold
            
            if (isDistanceTriggered || isTimeTriggered) {
                val isTransfer = currentLegs.isNotEmpty() && currentLegIndex < currentLegs.size - 1
                triggerAlarm(isTransfer)
            }
        }
    }

    private fun handleArrivalAtPoint() {
        if (currentLegs.isNotEmpty() && currentLegIndex < currentLegs.size - 1) {
            // Arrived at the end of a non-final leg -> Transition to next leg
            android.util.Log.d("LocationAlarmService", "Transitioning from leg $currentLegIndex to ${currentLegIndex + 1}")
            currentLegIndex++
            isAlarmSilenced = false // Allow alarm for the next leg
            currentState = ServiceState.WAITING_FOR_CONNECTION
        } else if (!hasSentArrivalBroadcast) {
            // Final destination
            hasSentArrivalBroadcast = true
            sendBroadcast(Intent(JOURNEY_COMPLETED_BROADCAST).setPackage(packageName))
        }
    }

    private fun adjustPollingInterval(distanceMeters: Double) {
        val mode = if (currentLegs.isNotEmpty() && currentLegIndex in currentLegs.indices) {
            currentLegs[currentLegIndex].mode
        } else {
            TransportMode.ROAD
        }

        val priority = when (mode) {
            TransportMode.TRAIN, TransportMode.SUBWAY -> if (distanceMeters > 5000) Priority.PRIORITY_BALANCED_POWER_ACCURACY else Priority.PRIORITY_HIGH_ACCURACY
            else -> Priority.PRIORITY_HIGH_ACCURACY
        }

        val interval = when {
            distanceMeters > 10000 -> if (mode == TransportMode.TRAIN) 60000L else 30000L 
            distanceMeters > 5000 -> 15000L
            distanceMeters > 2000 -> 5000L
            else -> 2000L
        }
        locationTrackingManager.updateInterval(interval, priority)
    }

    private fun triggerAlarm(isTransfer: Boolean) {
        currentState = if (isTransfer) ServiceState.ALARM_TRANSFER else ServiceState.ALARM_RINGING
        val ringingIntent = Intent(this, com.janak.location.alarm.RingingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("RINGTONE_URI", ringtoneUri)
            putExtra("VIBRATE", isVibrateEnabled)
            putExtra("IS_TRANSFER", isTransfer)
            if (isTransfer && currentLegIndex in currentLegs.indices) {
                putExtra("TRANSFER_NAME", currentLegs[currentLegIndex].endName ?: "Transfer Point")
            }
        }
        try { startActivity(ringingIntent) } catch (e: Exception) {}
        
        val title = if (isTransfer) "Transfer Alert!" else "Arrived!"
        val content = if (isTransfer) {
            "Time to change at ${currentLegs[currentLegIndex].endName ?: "Transfer Point"}"
        } else {
            "You are within ${formatDistance(distanceThreshold.toInt())} of $destinationName"
        }
        updateNotification(title, content)
    }

    private fun stopAlarmRinging() {
        if (currentState == ServiceState.ALARM_RINGING || currentState == ServiceState.ALARM_TRANSFER) {
            val wasTransfer = currentState == ServiceState.ALARM_TRANSFER
            currentState = if (wasTransfer) ServiceState.WAITING_FOR_CONNECTION else ServiceState.TRACKING
            isAlarmSilenced = true
            updateNotification("Distance Alarm Active", "Monitoring progress...")
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
        deadReckoningJob?.cancel()
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
