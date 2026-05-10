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
import kotlinx.coroutines.*
import kotlin.math.roundToInt

class LocationAlarmService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var locationTrackingManager: LocationTrackingManager
    private lateinit var alarmEngine: AlarmEngine
    private var trackingJob: Job? = null

    private var destinationLat: Double = 0.0
    private var destinationLng: Double = 0.0
    private var distanceThreshold: Float = 0f

    override fun onCreate() {
        super.onCreate()
        locationTrackingManager = LocationTrackingManager(this)
        alarmEngine = AlarmEngine(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_ALARM") {
            stopSelf()
            return START_NOT_STICKY
        }

        destinationLat = intent?.getDoubleExtra("DEST_LAT", 0.0) ?: 0.0
        destinationLng = intent?.getDoubleExtra("DEST_LNG", 0.0) ?: 0.0
        distanceThreshold = intent?.getFloatExtra("DISTANCE_THRESHOLD", 500f) ?: 500f

        startForeground(1, createNotification("Guardian Active", "Monitoring distance to destination..."))
        startLocationTracking()

        return START_REDELIVER_INTENT
    }

    private fun startLocationTracking() {
        trackingJob?.cancel()
        trackingJob = serviceScope.launch {
            locationTrackingManager.getLocationUpdates().collect { location ->
                checkDistance(location)
            }
        }
    }

    private fun checkDistance(location: Location) {
        val results = FloatArray(1)
        Location.distanceBetween(
            location.latitude, location.longitude,
            destinationLat, destinationLng,
            results
        )
        val distance = results[0]
        
        updateNotification("Guardian Active", "Distance to target: ${distance.roundToInt()}m")

        if (distance <= distanceThreshold) {
            alarmEngine.start(true)
            
            // Launch RingingActivity
            val ringingIntent = Intent(this, com.janak.location.alarm.RingingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            startActivity(ringingIntent)
            
            stopSelf()
        }
    }

    private fun updateNotification(title: String, content: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(1, createNotification(title, content))
    }

    private fun createNotification(title: String, content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, "LocationAlarmChannel")
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "LocationAlarmChannel",
                "Location Alarm Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        trackingJob?.cancel()
        serviceScope.cancel()
        alarmEngine.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
