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
import com.janak.location.alarm.api.PhotonApiService
import com.janak.location.alarm.location.LocationTrackingManager
import com.janak.location.alarm.model.PhotonFeature
import com.janak.location.alarm.service.LocationAlarmService
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.maplibre.android.geometry.LatLng
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.roundToInt

import com.janak.location.alarm.data.repository.RouteRepository
import com.janak.location.alarm.data.repository.HistoryRepository
import com.janak.location.alarm.data.entity.SavedRouteEntity
import androidx.core.content.edit
import com.janak.location.alarm.domain.RouteDistanceEngine

class MapViewModel(
    private val locationTrackingManager: LocationTrackingManager,
    private val alarmEngine: AlarmEngine,
    private val photonApiService: PhotonApiService,
    private val osrmApiService: PhotonApiService,
    private val routeRepository: RouteRepository,
    private val historyRepository: HistoryRepository,
    private val context: Context
) : ViewModel() {

    private val routeDistanceEngine = RouteDistanceEngine()

    private fun org.maplibre.geojson.Point.toMapbox(): com.mapbox.geojson.Point = 
        com.mapbox.geojson.Point.fromLngLat(longitude(), latitude())

    private fun org.maplibre.geojson.LineString.toMapbox(): com.mapbox.geojson.LineString =
        com.mapbox.geojson.LineString.fromLngLats(coordinates().map { it.toMapbox() })

    private fun com.mapbox.geojson.LineString.toMapLibre(): org.maplibre.geojson.LineString =
        org.maplibre.geojson.LineString.fromLngLats(coordinates().map { 
            org.maplibre.geojson.Point.fromLngLat(it.longitude(), it.latitude())
        })
    
    val savedRoutes = routeRepository.allSavedRoutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val journeyHistory = historyRepository.allHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun logJourney(
        journey: com.janak.location.alarm.data.entity.JourneyHistoryEntity,
        breadcrumbs: List<com.janak.location.alarm.data.entity.RouteBreadcrumbEntity>
    ) {
        viewModelScope.launch {
            historyRepository.saveJourneyLog(journey, breadcrumbs)
        }
    }

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

    private val _expectedDuration = MutableStateFlow(0.0)
    private val _expectedDistance = MutableStateFlow(0.0)

    private val _isAlarmSet = MutableStateFlow(false)
    val isAlarmSet: StateFlow<Boolean> = _isAlarmSet.asStateFlow()

    private val _distanceToDestination = MutableStateFlow<String?>(null)
    val distanceToDestination: StateFlow<String?> = _distanceToDestination.asStateFlow()

    private val _remainingEta = MutableStateFlow<Int?>(null)
    val remainingEta: StateFlow<Int?> = _remainingEta.asStateFlow()

    private var fullRouteLine: com.mapbox.geojson.LineString? = null

    private val _alarmSettings = MutableStateFlow(com.janak.location.alarm.model.AlarmSettings())
    val alarmSettings: StateFlow<com.janak.location.alarm.model.AlarmSettings> = _alarmSettings.asStateFlow()

    // --- Phase 6: Preview State ---
    private val _isPreviewMode = MutableStateFlow(false)
    val isPreviewMode: StateFlow<Boolean> = _isPreviewMode.asStateFlow()

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
        sharedPrefs.edit { putString("search_history", historyJson) }
    }

    fun setThemeMode(mode: Int) {
        _themeMode.value = mode
        sharedPrefs.edit { putInt("theme_mode", mode) }
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

    fun startAlarm() {
        val settings = _alarmSettings.value
        _isAlarmSet.value = true
        _isPreviewMode.value = false
        
        val dest = _destination.value
        val destName = _destinationName.value ?: "Unknown Destination"
        val routeJson = _currentRouteGeoJson.value
        val duration = _expectedDuration.value
        val distance = _expectedDistance.value

        if (dest != null) {
            val serviceIntent = Intent(context, LocationAlarmService::class.java).apply {
                putExtra("DEST_LAT", dest.latitude)
                putExtra("DEST_LNG", dest.longitude)
                putExtra("DEST_NAME", destName)
                putExtra("ROUTE_GEOJSON", routeJson)
                putExtra("EXPECTED_DURATION", duration)
                putExtra("EXPECTED_DISTANCE", distance)
                putExtra("DISTANCE_THRESHOLD", settings.distanceMeters.toFloat())
                putExtra("DISTANCE_ALARM_ENABLED", settings.isDistanceAlarmEnabled)
                putExtra("PREDICTIVE_MINUTES", settings.predictiveMinutes)
                putExtra("PREDICTIVE_ALARM_ENABLED", settings.isPredictiveAlarmEnabled)
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

    fun updateAlarmSettings(settings: com.janak.location.alarm.model.AlarmSettings) {
        _alarmSettings.value = settings
        if (_isAlarmSet.value || _isPreviewMode.value) {
            startAlarm()
        }
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
            _isPreviewMode.value = true
            android.util.Log.d("MapViewModel", "setDestination: $name, location: ${_userLocation.value}")
            _userLocation.value?.let { 
                checkDistance(it)
                fetchRoute(it, latLng)
            } ?: android.util.Log.d("MapViewModel", "setDestination: Waiting for user location to fetch route")
        }
    }

    private fun fetchRoute(start: Location, end: LatLng, pushToService: Boolean = false) {
        android.util.Log.d("MapViewModel", "fetchRoute: start=$start, end=$end")
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
                        
                        // Extract duration and distance
                        val distance = firstRoute.get("distance")?.toString()?.toDoubleOrNull() ?: 0.0
                        _expectedDuration.value = firstRoute.get("duration")?.toString()?.toDoubleOrNull() ?: 0.0
                        _expectedDistance.value = distance
                        android.util.Log.d("MapViewModel", "fetchRoute: duration=${_expectedDuration.value}, distance=${_expectedDistance.value}")

                        // Update search history with road distance if this destination was just selected
                        val currentDest = _destination.value
                        if (currentDest != null) {
                            val updatedHistory = _searchHistory.value.map { feature ->
                                val coords = feature.geometry.coordinates
                                if (coords[1] == currentDest.latitude && coords[0] == currentDest.longitude) {
                                    feature.copy(properties = feature.properties.copy(roadDistance = distance))
                                } else {
                                    feature
                                }
                            }
                            _searchHistory.value = updatedHistory
                            saveSearchHistory(updatedHistory)
                        }

                        // Also update _routeLine for map rendering
                        val coordinates = firstRoute.get("geometry")?.jsonObject?.get("coordinates")?.jsonArray?.map {
                            val point = it.jsonArray
                            Point.fromLngLat(point[0].toString().toDouble(), point[1].toString().toDouble())
                        }
                        if (coordinates != null) {
                            val mbRoute = com.mapbox.geojson.LineString.fromLngLats(coordinates.map { 
                                com.mapbox.geojson.Point.fromLngLat(it.longitude(), it.latitude())
                            })
                            fullRouteLine = mbRoute
                            _routeLine.value = LineString.fromLngLats(coordinates)
                            android.util.Log.d("MapViewModel", "fetchRoute: routeLine updated, size=${coordinates.size}")
                        }

                        if (pushToService && _isAlarmSet.value) {
                            val updateIntent = Intent(context, LocationAlarmService::class.java).apply {
                                action = LocationAlarmService.ACTION_UPDATE_ROUTE
                                putExtra("ROUTE_GEOJSON", geoJson)
                                putExtra("EXPECTED_DURATION", _expectedDuration.value)
                                putExtra("EXPECTED_DISTANCE", _expectedDistance.value)
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
                startAlarm()
            }
        }
    }

    private val _journeyCompleted = MutableStateFlow<Boolean>(false)
    val journeyCompleted: StateFlow<Boolean> = _journeyCompleted.asStateFlow()
    
    private var hasTriggeredArrival = false

    fun resetJourneyCompleted() {
        _journeyCompleted.value = false
        hasTriggeredArrival = false
    }

    fun stopAlarm() {
        _isAlarmSet.value = false
        _isPreviewMode.value = false
        _distanceToDestination.value = null
        _remainingEta.value = null
        _destinationName.value = null
        _currentRouteGeoJson.value = null
        _routeLine.value = null
        fullRouteLine = null
        routeDistanceEngine.resetStats()
        alarmEngine.stop()
        
        val serviceIntent = Intent(context, LocationAlarmService::class.java)
        context.stopService(serviceIntent)
    }

    fun clearDestination() {
        if (!_isAlarmSet.value) {
            _destination.value = null
            _destinationName.value = null
            _isPreviewMode.value = false
            _distanceToDestination.value = null
            _remainingEta.value = null
            _currentRouteGeoJson.value = null
            _routeLine.value = null
            fullRouteLine = null
            routeDistanceEngine.resetStats()
        }
    }

    private fun checkDistance(currentLocation: Location) {
        val dest = _destination.value ?: return
        
        // 1. If in Preview Mode, prioritize the OSRM expected distance (FULL ROAD DISTANCE)
        if (_isPreviewMode.value && _expectedDistance.value > 0) {
            _distanceToDestination.value = formatDistance(_expectedDistance.value.toInt())
            _remainingEta.value = (_expectedDuration.value / 60.0).roundToInt()
            return
        }

        // 2. If Alarm is Active, use high-precision road-snapping logic
        val route = fullRouteLine
        if (route != null && (_isAlarmSet.value || _isPreviewMode.value)) {
            val userPoint = com.mapbox.geojson.Point.fromLngLat(currentLocation.longitude, currentLocation.latitude)
            
            // Snapping & Slicing
            val feature = com.mapbox.turf.TurfMisc.nearestPointOnLine(userPoint, route.coordinates())
            val snappedPoint = feature.geometry() as? com.mapbox.geojson.Point
            
            if (snappedPoint != null) {
                val slicedLine = com.mapbox.turf.TurfMisc.lineSlice(snappedPoint, route.coordinates().last(), route)
                
                // Update map route line (sliced version)
                _routeLine.value = slicedLine.toMapLibre()
                
                // Calculate distance along route
                val distance = com.mapbox.turf.TurfMeasurement.length(slicedLine, com.mapbox.turf.TurfConstants.UNIT_METERS)
                _distanceToDestination.value = formatDistance(distance.toInt())
                
                // Update speed and calculate ETA
                routeDistanceEngine.updateAverageSpeed(currentLocation.speed.toDouble())
                val expectedSpeed = if (_expectedDuration.value > 0) _expectedDistance.value / _expectedDuration.value else 0.0
                
                val etaMinutes = routeDistanceEngine.calculateCalibratedETA(
                    remainingDistanceMeters = distance,
                    expectedSpeedMps = expectedSpeed
                )
                
                if (etaMinutes != Double.MAX_VALUE) {
                    _remainingEta.value = etaMinutes.roundToInt()
                    android.util.Log.d("MapViewModel", "checkDistance: distance=${distance.toInt()}m, eta=${etaMinutes.roundToInt()}min, speed=${currentLocation.speed}mps")
                } else {
                    _remainingEta.value = null
                    android.util.Log.d("MapViewModel", "checkDistance: distance=${distance.toInt()}m, eta=WAITING_FOR_SPEED")
                }
                return
            } else {
                android.util.Log.w("MapViewModel", "checkDistance: Could not snap user to route line")
            }
        }

        // 3. Fallback to Haversine (ONLY if OSRM is unavailable)
        val results = FloatArray(1)
        Location.distanceBetween(
            currentLocation.latitude, currentLocation.longitude,
            dest.latitude, dest.longitude,
            results
        )
        val distance = results[0]

        _distanceToDestination.value = if (_isAlarmSet.value || _isPreviewMode.value) {
            formatDistance(distance.toInt())
        } else {
            null
        }

        // Auto-arrival detection (50m threshold)
        if (distance <= 50 && !hasTriggeredArrival && (_isAlarmSet.value || _isPreviewMode.value)) {
            android.util.Log.d("MapViewModel", "Arrival detected (within 50m). Triggering completion.")
            hasTriggeredArrival = true
            _journeyCompleted.value = true
            stopAlarm()
        }

        _remainingEta.value = null
    }

    private fun formatDistance(meters: Int): String {
        return if (meters >= 1000) String.format("%.1fkm", meters / 1000f) else "${meters}m"
    }
    
    fun saveRoute(destinationName: String, breadcrumbs: List<com.janak.location.alarm.data.entity.RouteBreadcrumbEntity>, alarmSettings: com.janak.location.alarm.model.AlarmSettings) {
        val dest = _destination.value ?: return
        viewModelScope.launch {
            val route = SavedRouteEntity(
                destinationName = destinationName.ifBlank { "Unknown Destination" },
                mapDestinationName = null,
                destinationLat = dest.latitude,
                destinationLng = dest.longitude,
                alarmSettings = alarmSettings,
                dateSaved = System.currentTimeMillis(),
                lastTakenTimestamp = System.currentTimeMillis()
            )
            routeRepository.saveJourney(route, breadcrumbs)
        }
    }

    fun updateRoute(route: SavedRouteEntity) {
        viewModelScope.launch {
            routeRepository.updateSavedRoute(route)
        }
    }

    fun deleteJourneys(journeys: List<com.janak.location.alarm.data.entity.JourneyHistoryEntity>) {
        viewModelScope.launch {
            historyRepository.deleteJourneys(journeys)
        }
    }

    fun deleteRoutes(routes: List<SavedRouteEntity>) {
        viewModelScope.launch {
            routes.forEach { route ->
                routeRepository.deleteRoute(route)
            }
        }
    }

    fun deleteRoute(route: SavedRouteEntity) {
        viewModelScope.launch {
            routeRepository.deleteRoute(route)
        }
    }

    fun clearSavedRoutes() {
        viewModelScope.launch {
            routeRepository.deleteAllRoutes()
        }
    }

    fun clearJourneyHistory() {
        viewModelScope.launch {
            historyRepository.clearAllHistory()
        }
    }

    fun getBreadcrumbsForHistory(historyId: Long): kotlinx.coroutines.flow.Flow<List<com.janak.location.alarm.data.entity.RouteBreadcrumbEntity>> {
        return historyRepository.getBreadcrumbsForHistory(historyId)
    }

    fun startJourneyDirect(lat: Double, lng: Double, name: String, settings: com.janak.location.alarm.model.AlarmSettings) {
        _destination.value = LatLng(lat, lng)
        _destinationName.value = name
        _alarmSettings.value = settings
        _isPreviewMode.value = true
        
        _userLocation.value?.let { 
            checkDistance(it)
            fetchRoute(it, LatLng(lat, lng))
        }
    }

    fun startJourneyFromSavedRoute(route: SavedRouteEntity) {
        startJourneyDirect(route.destinationLat, route.destinationLng, route.destinationName, route.alarmSettings)
    }

    fun startJourneyFromHistory(history: com.janak.location.alarm.data.entity.JourneyHistoryEntity) {
        startJourneyDirect(history.destinationLat, history.destinationLng, history.destinationName, history.alarmConfigAtTime)
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
    private val photonApiService: PhotonApiService,
    private val osrmApiService: PhotonApiService,
    private val routeRepository: RouteRepository,
    private val historyRepository: com.janak.location.alarm.data.repository.HistoryRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MapViewModel(
                locationTrackingManager, alarmEngine, 
                photonApiService, osrmApiService, routeRepository, historyRepository, context
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
