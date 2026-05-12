package com.janak.location.alarm.viewmodel

import android.location.Location
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
import org.maplibre.android.geometry.LatLng
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class MapViewModel(
    private val locationTrackingManager: LocationTrackingManager,
    private val alarmEngine: AlarmEngine,
    private val alarmScheduler: AlarmScheduler,
    private val photonApiService: PhotonApiService,
    context: android.content.Context
) : ViewModel() {

    private val sharedPrefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)

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

    // --- Theme State ---
    // 0 = System, 1 = Light, 2 = Dark
    private val _themeMode = MutableStateFlow(sharedPrefs.getInt("theme_mode", 0))
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    // --- Search History ---
    private val _searchHistory = MutableStateFlow<List<PhotonFeature>>(loadSearchHistory())
    val searchHistory: StateFlow<List<PhotonFeature>> = _searchHistory.asStateFlow()

    init {
        setupSearchDebounce()
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

    fun onSearchQueryChange(newQuery: String) {
        _searchQuery.value = newQuery
    }

    fun updateAlarmSettings(settings: com.janak.location.alarm.model.AlarmSettings) {
        _alarmSettings.value = settings
        
        // 1. Schedule the exact Time-Based Backup Alarm via Android System
        alarmScheduler.scheduleBackupAlarm(settings)
        
        // 2. Mark the alarm as active
        _isAlarmSet.value = true
        _userLocation.value?.let { checkDistance(it) }
        
        // 3. Start Location Tracking logic for distance threshold
        startLocationUpdates()
    }

    private var locationJob: kotlinx.coroutines.Job? = null

    fun startLocationUpdates() {
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
        val updatedHistory = currentHistory.take(10) // Keep last 10
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
                _isAlarmSet.value = true
                _userLocation.value?.let { checkDistance(it) }
                startLocationUpdates()
            }
        }
    }

    private fun stopAlarm() {
        _isAlarmSet.value = false
        _distanceToDestination.value = null
        alarmScheduler.cancelAlarm()
        alarmEngine.stop()
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
            if (distance <= _alarmSettings.value.distanceMeters) {
                alarmEngine.start(shouldVibrate = _alarmSettings.value.isVibrateEnabled)
            }
        } else {
            _distanceToDestination.value = null
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        alarmEngine.stop()
    }
}

class MapViewModelFactory(
    private val locationTrackingManager: LocationTrackingManager,
    private val alarmEngine: AlarmEngine,
    private val alarmScheduler: AlarmScheduler,
    private val photonApiService: PhotonApiService,
    private val context: android.content.Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MapViewModel(locationTrackingManager, alarmEngine, alarmScheduler, photonApiService, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
