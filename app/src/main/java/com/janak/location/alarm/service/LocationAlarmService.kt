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
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    private var destinationLat: Double = 0.0
    private var destinationLng: Double = 0.0
    private var distanceThreshold: Float = 500f
    private var ringtoneUri: String? = null
    private var isVibrateEnabled: Boolean = true

    override fun onCreate() {
        super.onCreate()
        locationTrackingManager = LocationTrackingManager(this)
        alarmEngine = AlarmEngine(this)
        createNotificationChannel()
        
        val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
        wakeLock = powerManager.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK,
            "LocationAlarmService::WakeLock"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP_ALARM") {
            stopSelf()
            return START_NOT_STICKY
        }
        
        wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes timeout

        destinationLat = intent?.getDoubleExtra("DEST_LAT", 0.0) ?: 0.0
        destinationLng = intent?.getDoubleExtra("DEST_LNG", 0.0) ?: 0.0
        distanceThreshold = intent?.getFloatExtra("DISTANCE_THRESHOLD", 500f) ?: 500f
        ringtoneUri = intent?.getStringExtra("RINGTONE_URI")
        isVibrateEnabled = intent?.getBooleanExtra("VIBRATE", true) ?: true

        startForeground(1, createNotification("Guardian Active", "Monitoring distance to destination..."))
        startLocationTracking()

        return START_STICKY
    }

    private fun startLocationTracking() {
        // Check for location permissions
        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            // Permission missing, stop service
            stopSelf()
            return
        }

        trackingJob?.cancel()
        trackingJob = serviceScope.launch {
            locationTrackingManager.getLocationUpdates().collect { location ->
                checkDistance(location)
            }
        }
    }

    private fun checkDistance(location: Location) {
        // Extend wake lock on each update
        wakeLock?.acquire(10 * 60 * 1000L)
        val results = FloatArray(1)
        Location.distanceBetween(
            location.latitude, location.longitude,
            destinationLat, destinationLng,
            results
        )
        val distance = results[0]
        
        updateNotification("Guardian Active", "Distance to target: ${distance.roundToInt()}m")

        if (distance <= distanceThreshold) {
            triggerAlarm()
            stopSelf()
        }
    }

    private fun triggerAlarm() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        val channelId = "AlarmRingingChannel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Alarm Triggered",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val ringingIntent = Intent(this, com.janak.location.alarm.RingingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("RINGTONE_URI", ringtoneUri)
            putExtra("VIBRATE", isVibrateEnabled)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            1003,
            ringingIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Destination Reached!")
            .setContentText("Wake up! You are within ${distanceThreshold.roundToInt()}m of your destination.")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        notificationManager.notify(1001, notification)

        try {
            startActivity(ringingIntent)
        } catch (e: Exception) {
            // Handled by fullScreenIntent
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
