package com.janak.location.alarm.viewmodel

import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.janak.location.alarm.alarm.AlarmEngine
import com.janak.location.alarm.alarm.AlarmScheduler
import com.janak.location.alarm.api.PhotonApiService
import com.janak.location.alarm.location.LocationTrackingManager
import com.janak.location.alarm.model.PhotonFeature
import com.janak.location.alarm.service.LocationAlarmService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.roundToInt

import com.janak.location.alarm.data.repository.RouteRepository
import com.janak.location.alarm.data.entity.SavedRouteEntity

class MapViewModel(
    private val locationTrackingManager: LocationTrackingManager,
    private val alarmEngine: AlarmEngine,
    private val alarmScheduler: AlarmScheduler,
    private val photonApiService: PhotonApiService,
    private val osrmApiService: PhotonApiService,
    private val routeRepository: RouteRepository,
    private val context: Context
) : ViewModel() {
    
    val savedRoutes = routeRepository.allSavedRoutes

    private val sharedPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _userLocation = MutableStateFlow<Location?>(null)
    val userLocation: StateFlow<Location?> = _userLocation.asStateFlow()

    private val _routeLine = MutableStateFlow<LineString?>(null)
    val routeLine: StateFlow<LineString?> = _routeLine.asStateFlow()

    private val _destination = MutableStateFlow<LatLng?>(null)
    val destination: StateFlow<LatLng?> = _destination.asStateFlow()

    private val _destinationName = MutableStateFlow<String?>(null)
    val destinationName: StateFlow<String?> = _destinationName.asStateFlow()

    private val _currentRouteGeoJson = MutableStateFlow<String?>(null)
    val currentRouteGeoJson: StateFlow<String?> = _currentRouteGeoJson.asStateFlow()

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

    private val reRouteReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == LocationAlarmService.ACTION_RE_ROUTE) {
                android.util.Log.d("MapViewModel", "Re-route broadcast received")
                val start = _userLocation.value
                val end = _destination.value
                if (start != null && end != null) {
                    fetchRoute(start, end, pushToService = true)
                }
            }
        }
    }

    private val journeyCompletedReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            if (intent?.action == LocationAlarmService.JOURNEY_COMPLETED_BROADCAST) {
                _journeyCompleted.value = true
                stopAlarm()
            }
        }
    }

    init {
        checkLocationSettings()
        setupSearchDebounce()
        
        val reRouteFilter = android.content.IntentFilter(LocationAlarmService.ACTION_RE_ROUTE)
        val journeyFilter = android.content.IntentFilter(LocationAlarmService.JOURNEY_COMPLETED_BROADCAST)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(reRouteReceiver, reRouteFilter, Context.RECEIVER_NOT_EXPORTED)
            context.registerReceiver(journeyCompletedReceiver, journeyFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(reRouteReceiver, reRouteFilter)
            context.registerReceiver(journeyCompletedReceiver, journeyFilter)
        }
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
            val response = photonApiService.getSuggestions(
                query = query,
                lat = _userLocation.value?.latitude,
                lon = _userLocation.value?.longitude
            )
            
            val historyMatches = _searchHistory.value.filter { feature ->
                val name = feature.properties.name ?: ""
                val street = feature.properties.street ?: ""
                name.contains(query, ignoreCase = true) || street.contains(query, ignoreCase = true)
            }

            val remoteFeatures = response.body()?.features ?: emptyList()
            val combined = (historyMatches + remoteFeatures).distinctBy { 
                "${it.geometry.coordinates[0]},${it.geometry.coordinates[1]}"
            }

            _searchResults.value = combined
        } catch (e: Exception) {
            android.util.Log.e("MapViewModel", "Search failed", e)
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
        alarmScheduler.scheduleBackupAlarm(settings)
        _isAlarmSet.value = true
        
        if (settings.isTimeAlarmEnabled) {
            val totalSecs = (settings.timeAlarmHour * 3600L) + (settings.timeAlarmMinute * 60L)
            startVisualCountdown(totalSecs)
        } else {
            stopCountdown()
        }
        
        val dest = _destination.value
        val destName = _destinationName.value ?: "Unknown Destination"
        val routeJson = _currentRouteGeoJson.value
        if (dest != null) {
            val serviceIntent = Intent(context, LocationAlarmService::class.java).apply {
                putExtra("DEST_LAT", dest.latitude)
                putExtra("DEST_LNG", dest.longitude)
                putExtra("DEST_NAME", destName)
                putExtra("ROUTE_GEOJSON", routeJson)
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
        startLocationUpdates()
    }

    private var locationJob: kotlinx.coroutines.Job? = null

    fun startLocationUpdates() {
        checkLocationSettings()
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            locationTrackingManager.getLocationUpdates().collect { location ->
                _userLocation.value = location
                checkDistance(location)
            }
        }
    }

    fun refreshLocation() {
        startLocationUpdates()
    }

    fun setDestination(latLng: LatLng, name: String? = null) {
        if (!_isAlarmSet.value) {
            _destination.value = latLng
            _destinationName.value = name
            _userLocation.value?.let { 
                checkDistance(it)
                fetchRoute(it, latLng)
            }
        }
    }

    private fun fetchRoute(start: Location, end: LatLng, pushToService: Boolean = false) {
        viewModelScope.launch {
            try {
                val coords = "${start.longitude},${start.latitude};${end.longitude},${end.latitude}"
                val response = osrmApiService.getRoute(coords)
                if (response.isSuccessful) {
                    val root = response.body()
                    val routes = root?.get("routes")?.jsonArray
                    val firstRoute = routes?.getOrNull(0)?.jsonObject
                    val geometry = firstRoute?.get("geometry")
                    
                    if (geometry != null) {
                        val geoJson = geometry.toString()
                        _currentRouteGeoJson.value = geoJson
                        
                        // Also update _routeLine for map rendering
                        val coordinates = firstRoute.get("geometry")?.jsonObject?.get("coordinates")?.jsonArray?.map {
                            val point = it.jsonArray
                            Point.fromLngLat(point[0].toString().toDouble(), point[1].toString().toDouble())
                        }
                        if (coordinates != null) {
                            _routeLine.value = LineString.fromLngLats(coordinates)
                        }

                        if (pushToService && _isAlarmSet.value) {
                            val updateIntent = Intent(context, LocationAlarmService::class.java).apply {
                                action = LocationAlarmService.ACTION_UPDATE_ROUTE
                                putExtra("ROUTE_GEOJSON", geoJson)
                            }
                            context.startService(updateIntent)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MapViewModel", "Failed to fetch route", e)
            }
        }
    }

    fun selectSearchResult(feature: PhotonFeature) {
        val currentHistory = _searchHistory.value.toMutableList()
        currentHistory.removeAll { it.geometry.coordinates == feature.geometry.coordinates }
        currentHistory.add(0, feature)
        val updatedHistory = currentHistory.take(50)
        _searchHistory.value = updatedHistory
        saveSearchHistory(updatedHistory)

        val coords = feature.geometry.coordinates
        val latLng = LatLng(coords[1], coords[0])
        val name = feature.properties.name ?: feature.properties.street ?: "Search Result"
        setDestination(latLng, name)
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

    private val _journeyCompleted = MutableStateFlow<Boolean>(false)
    val journeyCompleted: StateFlow<Boolean> = _journeyCompleted.asStateFlow()
    
    fun resetJourneyCompleted() {
        _journeyCompleted.value = false
    }

    fun stopAlarm() {
        _isAlarmSet.value = false
        _distanceToDestination.value = null
        _destinationName.value = null
        _currentRouteGeoJson.value = null
        _routeLine.value = null
        alarmScheduler.cancelAlarm()
        alarmEngine.stop()
        stopCountdown()
        
        val serviceIntent = Intent(context, LocationAlarmService::class.java)
        context.stopService(serviceIntent)
    }

    fun clearDestination() {
        if (!_isAlarmSet.value) {
            _destination.value = null
            _destinationName.value = null
            _distanceToDestination.value = null
            _currentRouteGeoJson.value = null
            _routeLine.value = null
        }
    }

    private fun checkDistance(currentLocation: Location) {
        val dest = _destination.value ?: return
        val results = FloatArray(1)
        Location.distanceBetween(
            currentLocation.latitude, currentLocation.longitude,
            dest.latitude, dest.longitude,
            results
        )
        val distance = results[0]

        if (_isAlarmSet.value) {
            _distanceToDestination.value = "${distance.roundToInt()}m"
        } else {
            _distanceToDestination.value = null
        }
    }
    
    fun saveRoute(destinationName: String, breadcrumbs: List<com.janak.location.alarm.data.entity.RouteBreadcrumbEntity>) {
        viewModelScope.launch {
            val route = SavedRouteEntity(
                destinationName = destinationName,
                dateSaved = System.currentTimeMillis()
            )
            routeRepository.saveJourney(route, breadcrumbs)
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            context.unregisterReceiver(reRouteReceiver)
            context.unregisterReceiver(journeyCompletedReceiver)
        } catch (e: Exception) {}
    }
}

class MapViewModelFactory(
    private val locationTrackingManager: LocationTrackingManager,
    private val alarmEngine: AlarmEngine,
    private val alarmScheduler: AlarmScheduler,
    private val photonApiService: PhotonApiService,
    private val osrmApiService: PhotonApiService,
    private val routeRepository: RouteRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MapViewModel(
                locationTrackingManager, alarmEngine, alarmScheduler, 
                photonApiService, osrmApiService, routeRepository, context
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
