package com.janak.location.alarm.viewmodel

import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.janak.location.alarm.location.LocationTrackingManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.janak.location.alarm.alarm.AlarmEngine
import com.janak.location.alarm.alarm.AlarmScheduler
import com.janak.location.alarm.api.PhotonApiService
import com.janak.location.alarm.model.PhotonFeature
import com.janak.location.alarm.service.LocationAlarmService
import org.maplibre.android.geometry.LatLng
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class MapViewModel(
    private val locationTrackingManager: LocationTrackingManager,
    private val alarmEngine: AlarmEngine,
    private val alarmScheduler: AlarmScheduler,
    private val photonApiService: PhotonApiService,
    private val context: Context
) : ViewModel() {

    private val sharedPrefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _userLocation = MutableStateFlow<android.location.Location?>(null)
    val userLocation: StateFlow<android.location.Location?> = _userLocation.asStateFlow()

    private val _destination = MutableStateFlow<LatLng?>(null)
    val destination: StateFlow<LatLng?> = _destination.asStateFlow()

    private val _isAlarmSet = MutableStateFlow(false)
    val isAlarmSet: StateFlow<Boolean> = _isAlarmSet.asStateFlow()

    private val _distanceToDestination = MutableStateFlow<String?>(null)
    val distanceToDestination: StateFlow<String?> = _distanceToDestination.asStateFlow()

    private val _alarmSettings = MutableStateFlow(com.janak.location.alarm.model.AlarmSettings())
    val alarmSettings: StateFlow<com.janak.location.alarm.model.AlarmSettings> = _alarmSettings.asStateFlow()

    // --- Search State ---
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<PhotonFeature>>(emptyList())
    val searchResults: StateFlow<List<PhotonFeature>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    // --- System State ---
    private val _isLocationEnabled = MutableStateFlow(true)
    val isLocationEnabled: StateFlow<Boolean> = _isLocationEnabled.asStateFlow()

    // --- Timer State ---
    private val _remainingSeconds = MutableStateFlow(0L)
    val remainingSeconds: StateFlow<Long> = _remainingSeconds.asStateFlow()

    private val _totalSeconds = MutableStateFlow(0L)
    val totalSeconds: StateFlow<Long> = _totalSeconds.asStateFlow()

    private var countdownJob: kotlinx.coroutines.Job? = null

    // --- Theme State ---
    // 0 = System, 1 = Light, 2 = Dark
    private val _themeMode = MutableStateFlow(sharedPrefs.getInt("theme_mode", 0))
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    // --- Search History ---
    private val _searchHistory = MutableStateFlow<List<PhotonFeature>>(loadSearchHistory())
    val searchHistory: StateFlow<List<PhotonFeature>> = _searchHistory.asStateFlow()

    init {
        checkLocationSettings()
        setupSearchDebounce()
    }

    fun checkLocationSettings() {
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        _isLocationEnabled.value = isGpsEnabled || isNetworkEnabled
    }

    private fun startVisualCountdown(totalSecs: Long) {
        countdownJob?.cancel()
        if (totalSecs <= 0) {
            _remainingSeconds.value = 0
            return
        }

        _totalSeconds.value = totalSecs
        _remainingSeconds.value = totalSecs

        countdownJob = viewModelScope.launch {
            while (_remainingSeconds.value > 0) {
                kotlinx.coroutines.delay(1000)
                _remainingSeconds.value -= 1
            }
        }
    }

    private fun stopCountdown() {
        countdownJob?.cancel()
        _remainingSeconds.value = 0
        _totalSeconds.value = 0
    }

    private fun loadSearchHistory(): List<PhotonFeature> {
        val historyJson = sharedPrefs.getString("search_history", null) ?: return emptyList()
        return try {
            Json.decodeFromString<List<PhotonFeature>>(historyJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveSearchHistory(history: List<PhotonFeature>) {
        val historyJson = Json.encodeToString(history)
        sharedPrefs.edit().putString("search_history", historyJson).apply()
    }

    fun setThemeMode(mode: Int) {
        _themeMode.value = mode
        sharedPrefs.edit().putInt("theme_mode", mode).apply()
    }

    private fun setupSearchDebounce() {
        viewModelScope.launch {
            _searchQuery
                .debounce(500)
                .distinctUntilChanged()
                .collect { query ->
                    if (query.length >= 3) {
                        performSearch(query)
                    } else if (query.isEmpty()) {
                        _searchResults.value = emptyList()
                    }
                }
        }
    }

    private suspend fun performSearch(query: String) {
        _isSearching.value = true
        try {
            // 1. Get remote results
            val response = photonApiService.getSuggestions(
                query = query,
                lat = _userLocation.value?.latitude,
                lon = _userLocation.value?.longitude
            )
            
            // 2. Find local matches from history
            val historyMatches = _searchHistory.value.filter { feature ->
                val name = feature.properties.name ?: ""
                val street = feature.properties.street ?: ""
                name.contains(query, ignoreCase = true) || street.contains(query, ignoreCase = true)
            }

            // 3. Merge: Prioritize history, then remote results, removing duplicates
            val combined = (historyMatches + response.features).distinctBy { 
                "${it.geometry.coordinates[0]},${it.geometry.coordinates[1]}"
            }

            _searchResults.value = combined
        } catch (e: Exception) {
            android.util.Log.e("MapViewModel", "Search failed", e)
            // Fallback to history only if network fails
            _searchResults.value = _searchHistory.value.filter { feature ->
                val name = feature.properties.name ?: ""
                val street = feature.properties.street ?: ""
                name.contains(query, ignoreCase = true) || street.contains(query, ignoreCase = true)
            }
        } finally {
            _isSearching.value = false
        }
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
        saveSearchHistory(emptyList())
    }

    fun removeFromHistory(feature: PhotonFeature) {
        val updated = _searchHistory.value.filter { 
            it.geometry.coordinates != feature.geometry.coordinates 
        }
        _searchHistory.value = updated
        saveSearchHistory(updated)
    }

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun updateAlarmSettings(settings: com.janak.location.alarm.model.AlarmSettings) {
        _alarmSettings.value = settings
        
        // 1. Schedule the exact Time-Based Backup Alarm via Android System
        alarmScheduler.scheduleBackupAlarm(settings)
        
        // 2. Mark the alarm as active
        _isAlarmSet.value = true
        
        // 3. Start Visual Countdown for UI
        if (settings.isBackupEnabled) {
            val totalSecs = (settings.backupHour * 3600L) + (settings.backupMinute * 60L)
            startVisualCountdown(totalSecs)
        } else {
            stopCountdown()
        }
        
        // 4. Start Location Alarm Service
        val dest = _destination.value
        if (dest != null) {
            val serviceIntent = Intent(context, LocationAlarmService::class.java).apply {
                putExtra("DEST_LAT", dest.latitude)
                putExtra("DEST_LNG", dest.longitude)
                putExtra("DISTANCE_THRESHOLD", settings.distanceMeters.toFloat())
                putExtra("RINGTONE_URI", settings.ringtoneUri?.toString())
                putExtra("VIBRATE", settings.isVibrateEnabled)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
        
        _userLocation.value?.let { checkDistance(it) }
        
        // 5. Start Location Tracking logic for UI updates
        startLocationUpdates()
    }

    private var locationJob: kotlinx.coroutines.Job? = null

    fun startLocationUpdates() {
        checkLocationSettings()
        android.util.Log.d("MapViewModel", "startLocationUpdates: Called")
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationTrackingManager.getLocationUpdates().collect { location ->
                android.util.Log.d("MapViewModel", "startLocationUpdates: Received location $location")
                _userLocation.value = location
                checkDistance(location)
            }
        }
    }

    fun refreshLocation() {
        startLocationUpdates()
    }

    fun setDestination(latLng: LatLng) {
        if (!_isAlarmSet.value) {
            _destination.value = latLng
            // Recalculate distance immediately if we have a location
            _userLocation.value?.let { checkDistance(it) }
        }
    }

    fun selectSearchResult(feature: PhotonFeature) {
        // Add to history
        val currentHistory = _searchHistory.value.toMutableList()
        currentHistory.removeAll { 
            it.geometry.coordinates == feature.geometry.coordinates 
        }
        currentHistory.add(0, feature)
        val updatedHistory = currentHistory.take(50) // Keep last 50
        _searchHistory.value = updatedHistory
        saveSearchHistory(updatedHistory)

        val coords = feature.geometry.coordinates
        val latLng = LatLng(coords[1], coords[0])
        setDestination(latLng)
        _searchQuery.value = ""
        _searchResults.value = emptyList()
    }

    fun toggleAlarm() {
        if (_isAlarmSet.value) {
            stopAlarm()
        } else {
            if (_destination.value != null) {
                updateAlarmSettings(_alarmSettings.value)
            }
        }
    }

    private fun stopAlarm() {
        _isAlarmSet.value = false
        _distanceToDestination.value = null
        alarmScheduler.cancelAlarm()
        alarmEngine.stop()
        stopCountdown()
        
        // Stop the service
        val serviceIntent = Intent(context, LocationAlarmService::class.java)
        context.stopService(serviceIntent)
    }

    fun clearDestination() {
        if (!_isAlarmSet.value) {
            _destination.value = null
            _distanceToDestination.value = null
        }
    }

    private fun checkDistance(currentLocation: Location) {
        val dest = _destination.value ?: return

        val results = FloatArray(1)
        Location.distanceBetween(
            currentLocation.latitude,
            currentLocation.longitude,
            dest.latitude,
            dest.longitude,
            results
        )
        val distance = results[0]

        if (_isAlarmSet.value) {
            _distanceToDestination.value = "${distance.roundToInt()}m"
            // Note: Alarm triggering is now handled by LocationAlarmService
        } else {
            _distanceToDestination.value = null
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // We don't stop the service here because we want it to run in background
    }
}

class MapViewModelFactory(
    private val locationTrackingManager: LocationTrackingManager,
    private val alarmEngine: AlarmEngine,
    private val alarmScheduler: AlarmScheduler,
    private val photonApiService: PhotonApiService,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MapViewModel(locationTrackingManager, alarmEngine, alarmScheduler, photonApiService, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
