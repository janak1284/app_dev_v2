package com.janak.location.alarm.viewmodel

import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.janak.location.alarm.alarm.AlarmEngine
import com.janak.location.alarm.api.OpenRailRoutingApiService
import com.janak.location.alarm.api.PhotonApiService
import com.janak.location.alarm.location.LocationTrackingManager
import com.janak.location.alarm.model.*
import com.janak.location.alarm.service.LocationAlarmService
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

import org.maplibre.android.geometry.LatLng
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import kotlin.math.roundToInt

import com.janak.location.alarm.data.repository.RouteRepository
import com.janak.location.alarm.data.repository.HistoryRepository
import com.janak.location.alarm.data.entity.SavedRouteEntity
import androidx.core.content.edit
import androidx.core.content.ContextCompat
import com.janak.location.alarm.domain.RouteDistanceEngine
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.mapbox.turf.TurfMeasurement
import com.mapbox.turf.TurfConstants
import android.content.BroadcastReceiver
import android.content.IntentFilter
import com.janak.location.alarm.data.entity.JourneyHistoryEntity
import com.janak.location.alarm.data.entity.RouteBreadcrumbEntity
import java.util.Locale

@OptIn(FlowPreview::class)
class MapViewModel(
    private val locationTrackingManager: LocationTrackingManager,
    private val alarmEngine: AlarmEngine,
    private val photonApiService: PhotonApiService,
    private val osrmApiService: PhotonApiService,
    private val openRailRoutingApiService: OpenRailRoutingApiService,
    private val routeRepository: RouteRepository,
    private val historyRepository: HistoryRepository,
    private val context: Context
) : ViewModel() {

    private val routeDistanceEngine = RouteDistanceEngine()
    private val maxStationSearchRadiusKm = 30.0

    // Timing Constants for Multi-Modal Realism
    private val STATION_OVERHEAD_SEC = 300.0 // 5 mins to reach platform/exit station
    private val TRANSIT_WAIT_BUFFER_SEC = 600.0 // 10 mins average wait for next vehicle
    private val RAIL_SPEED_CALIBRATION_FACTOR = 0.8 // Assume 20% overhead for stops/track geometry mismatches

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }


    private fun Point.toMapbox(): com.mapbox.geojson.Point =
        com.mapbox.geojson.Point.fromLngLat(longitude(), latitude())

    private fun LineString.toMapbox(): com.mapbox.geojson.LineString =
        com.mapbox.geojson.LineString.fromLngLats(coordinates().map { it.toMapbox() })

    private fun com.mapbox.geojson.LineString.toMapLibre(): LineString =
        LineString.fromLngLats(coordinates().map {
            Point.fromLngLat(it.longitude(), it.latitude())
        })
    
    val savedRoutes = routeRepository.allSavedRoutes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val journeyHistory = historyRepository.allHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun logJourney(
        journey: JourneyHistoryEntity,
        breadcrumbs: List<RouteBreadcrumbEntity>
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

    private val _journeyLegs = MutableStateFlow<List<JourneyLeg>>(emptyList())
    val journeyLegs: StateFlow<List<JourneyLeg>> = _journeyLegs.asStateFlow()

    private val _expectedDuration = MutableStateFlow(0.0)
    private val _expectedDistance = MutableStateFlow(0.0)
    private val _segmentSpeeds = MutableStateFlow<List<Double>>(emptyList())

    private val _isAlarmSet = MutableStateFlow(false)
    val isAlarmSet: StateFlow<Boolean> = _isAlarmSet.asStateFlow()

    private val _distanceToDestination = MutableStateFlow<String?>(null)
    val distanceToDestination: StateFlow<String?> = _distanceToDestination.asStateFlow()

    private val _remainingEta = MutableStateFlow<Int?>(null)
    val remainingEta: StateFlow<Int?> = _remainingEta.asStateFlow()

    private val _isRouting = MutableStateFlow(false)
    val isRouting: StateFlow<Boolean> = _isRouting.asStateFlow()

    private var fullRouteLine: com.mapbox.geojson.LineString? = null

    private val _alarmSettings = MutableStateFlow(AlarmSettings())
    val alarmSettings: StateFlow<AlarmSettings> = _alarmSettings.asStateFlow()

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
    private val _searchHistory = MutableStateFlow(loadSearchHistory())
    val searchHistory: StateFlow<List<PhotonFeature>> = _searchHistory.asStateFlow()

    private val locationSettingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                checkLocationSettings()
            }
        }
    }

    private var lastReRouteTime = 0L
    private var lastCheckedLocation: Location? = null
    private var needsInitialCalculation: Boolean = true

    private val reRouteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationAlarmService.ACTION_RE_ROUTE) {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastReRouteTime < 60000L) {
                    android.util.Log.d("MapViewModel", "Re-route request ignored (Debounce active)")
                    return
                }
                lastReRouteTime = currentTime

                android.util.Log.d("MapViewModel", "Re-route broadcast received")
                val start = _userLocation.value
                val end = _destination.value
                if (start != null && end != null) {
                    fetchRoute(start, end, pushToService = true)
                }
            }
        }
    }

    private val journeyCompletedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationAlarmService.JOURNEY_COMPLETED_BROADCAST) {
                _journeyCompleted.value = true
                stopAlarm()
            }
        }
    }

    init {
        checkLocationSettings()
        setupSearchDebounce()
        
        val reRouteFilter = IntentFilter(LocationAlarmService.ACTION_RE_ROUTE)
        val journeyFilter = IntentFilter(LocationAlarmService.JOURNEY_COMPLETED_BROADCAST)
        val locationSettingsFilter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        
        ContextCompat.registerReceiver(context, reRouteReceiver, reRouteFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(context, journeyCompletedReceiver, journeyFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
        context.registerReceiver(locationSettingsReceiver, locationSettingsFilter)
    }

    fun checkLocationSettings() {
        try {
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            _isLocationEnabled.value = isGpsEnabled || isNetworkEnabled
        } catch (e: Exception) {
            android.util.Log.e("MapViewModel", "Error checking location settings", e)
            _isLocationEnabled.value = false
        }
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
        val legsJson = if (_journeyLegs.value.isNotEmpty()) Json.encodeToString(_journeyLegs.value) else null
        val duration = _expectedDuration.value
        val distance = _expectedDistance.value
        val speeds = _segmentSpeeds.value

        if (dest != null) {
            val serviceIntent = Intent(context, LocationAlarmService::class.java).apply {
                putExtra("DEST_LAT", dest.latitude)
                putExtra("DEST_LNG", dest.longitude)
                putExtra("DEST_NAME", destName)
                putExtra("ROUTE_GEOJSON", routeJson)
                putExtra("JOURNEY_LEGS_JSON", legsJson)
                putExtra("EXPECTED_DURATION", duration)
                putExtra("EXPECTED_DISTANCE", distance)
                putExtra("SEGMENT_SPEEDS", speeds.toDoubleArray())
                putExtra("DISTANCE_THRESHOLD", settings.distanceMeters.toFloat())
                putExtra("DISTANCE_ALARM_ENABLED", settings.isDistanceAlarmEnabled)
                putExtra("PREDICTIVE_MINUTES", settings.predictiveMinutes)
                putExtra("PREDICTIVE_ALARM_ENABLED", settings.isPredictiveAlarmEnabled)
                putExtra("RINGTONE_URI", settings.ringtoneUri?.toString())
                putExtra("VIBRATE", settings.isVibrateEnabled)
            }
            context.startForegroundService(serviceIntent)
        }
        
        _userLocation.value?.let { checkDistance(it) }
        startLocationUpdates()
    }

    fun updateAlarmSettings(settings: AlarmSettings) {
        val oldMode = _alarmSettings.value.transportMode
        _alarmSettings.value = settings
        
        if (settings.transportMode != oldMode) {
            val start = _userLocation.value
            val end = _destination.value
            if (start != null && end != null) {
                fetchRoute(start, end, pushToService = _isAlarmSet.value)
            }
        } else if (_isAlarmSet.value || _isPreviewMode.value) {
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
            resetRouteState()
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
        val mode = _alarmSettings.value.transportMode
        if (mode == TransportMode.ROAD) {
            fetchRoadRoute(start, end, pushToService)
        } else {
            fetchTransitRoute(start, end, pushToService)
        }
    }

    private fun fetchRoadRoute(start: Location, end: LatLng, pushToService: Boolean = false) {
        android.util.Log.d("MapViewModel", "fetchRoadRoute: start=$start, end=$end")
        _isRouting.value = true
        viewModelScope.launch {
            try {
                val coords = "${start.longitude},${start.latitude};${end.longitude},${end.latitude}"
                val response = osrmApiService.getRoute(coords)
                if (response.isSuccessful) {
                    val root = response.body() ?: return@launch
                    val firstRoute = root.routes.getOrNull(0) ?: return@launch
                    
                    val geoJson = firstRoute.geometry.coordinates.let { coords ->
                        val pointList = coords.map { Point.fromLngLat(it[0], it[1]) }
                        val mbLine = com.mapbox.geojson.LineString.fromLngLats(pointList.map { 
                            com.mapbox.geojson.Point.fromLngLat(it.longitude(), it.latitude())
                        })
                        mbLine.toJson()
                    }
                    
                    _currentRouteGeoJson.value = geoJson
                    _journeyLegs.value = emptyList()
                    _expectedDuration.value = firstRoute.duration
                    _expectedDistance.value = firstRoute.distance
                    
                    // Extract segment speeds from annotations
                    val speeds = firstRoute.legs.firstOrNull()?.annotation?.speed ?: emptyList()
                    _segmentSpeeds.value = speeds
                    
                    processRouteSuccess(geoJson, firstRoute.distance, pushToService, speeds)
                }
            } catch (e: Exception) {
                android.util.Log.e("MapViewModel", "Failed to fetch road route", e)
            } finally {
                _isRouting.value = false
            }
        }
    }

    private data class EvaluatedMultiModalRoute(
        val firstMile: RouteSegment,
        val railLeg: RouteSegment,
        val lastMile: RouteSegment,
        val totalDuration: Double,
        val weightedTotalCost: Double,
        val startStation: LatLng,
        val endStation: LatLng
    )

    private val routingSemaphore = Semaphore(3)

    private fun fetchTransitRoute(start: Location, end: LatLng, pushToService: Boolean = false) {
        android.util.Log.d("MapViewModel", "fetchTransitRoute (Global Matrix Optimized): start=${start.latitude},${start.longitude}, end=${end.latitude},${end.longitude}")
        _isRouting.value = true
        viewModelScope.launch {
            try {
                coroutineScope {
                    // 1. Find candidate stations near origin and destination
                    android.util.Log.d("MapViewModel", "Step 1: Searching and culling candidate stations...")
                    val startCandidates = findCandidateStations(start.latitude, start.longitude)
                    val endCandidates = findCandidateStations(end.latitude, end.longitude)

                    if (startCandidates.isEmpty() || endCandidates.isEmpty()) {
                        android.util.Log.w("MapViewModel", "Transit routing aborted: No viable stations within threshold ($maxStationSearchRadiusKm km). Falling back to Road route.")
                        fetchRoadRoute(start, end, pushToService)
                        return@coroutineScope
                    }

                    // 2. Pre-fetch OSRM segments for all candidates (Decoupled Evaluation)
                    android.util.Log.d("MapViewModel", "Step 2: Pre-fetching OSRM segments for all candidates...")
                    val firstMileResults = startCandidates.map { station ->
                        async { 
                            val segment = routingSemaphore.withPermit { 
                                fetchOsrmSegment(start.latitude, start.longitude, station.latitude, station.longitude)
                            }
                            if (segment != null) station to segment else null
                        }
                    }.awaitAll().filterNotNull().toMap()

                    val lastMileResults = endCandidates.map { station ->
                        async {
                            val segment = routingSemaphore.withPermit {
                                fetchOsrmSegment(station.latitude, station.longitude, end.latitude, end.longitude)
                            }
                            if (segment != null) station to segment else null
                        }
                    }.awaitAll().filterNotNull().toMap()

                    if (firstMileResults.isEmpty() || lastMileResults.isEmpty()) {
                        throw Exception("No road paths found to/from nearby stations.")
                    }

                    // 3. Concurrent Matrix Evaluation for Rail Segments
                    android.util.Log.d("MapViewModel", "Step 3: Evaluating Rail Matrix (N x M)...")
                    val matrixEvaluations = mutableListOf<kotlinx.coroutines.Deferred<EvaluatedMultiModalRoute?>>()

                    for ((sStation, fMile) in firstMileResults) {
                        for ((eStation, lMile) in lastMileResults) {
                            matrixEvaluations.add(async {
                                val rLeg = routingSemaphore.withPermit {
                                    fetchRailSegment(sStation.latitude, sStation.longitude, eStation.latitude, eStation.longitude)
                                }
                                
                                if (rLeg != null) {
                                    // Apply realism calibrations
                                    val railDurationCalibrated = rLeg.duration / RAIL_SPEED_CALIBRATION_FACTOR
                                    val stationOverhead = STATION_OVERHEAD_SEC * 2 // Access/Exit time
                                    val waitBuffer = TRANSIT_WAIT_BUFFER_SEC
                                    
                                    val totalDuration = fMile.duration + railDurationCalibrated + lMile.duration + stationOverhead + waitBuffer
                                    
                                    // Weighted Cost Function: Penalize Road/Walk (1.5x) vs Transit (1.0x)
                                    val weightedCost = (fMile.duration * 1.5) + (railDurationCalibrated * 1.0) + (lMile.duration * 1.5) + 
                                                      (stationOverhead * 1.2) + (waitBuffer * 1.1)
                                    
                                    EvaluatedMultiModalRoute(
                                        firstMile = fMile,
                                        railLeg = rLeg.copy(duration = railDurationCalibrated), // Use calibrated duration
                                        lastMile = lMile,
                                        totalDuration = totalDuration,
                                        weightedTotalCost = weightedCost,
                                        startStation = sStation,
                                        endStation = eStation
                                    )
                                } else null
                            })
                        }
                    }

                    val evaluatedRoutes = matrixEvaluations.awaitAll().filterNotNull()
                    
                    // 4. Global Minima Selection (Weighted)
                    val globalWinner = evaluatedRoutes.minByOrNull { it.weightedTotalCost }
                        ?: throw Exception("No valid railway path found between any candidate stations.")

                    android.util.Log.d("MapViewModel", "Global Winner Found: Total Dur=${globalWinner.totalDuration}s, Weighted Cost=${globalWinner.weightedTotalCost}")

                    // --- Hysteresis Check (Switching Penalty) ---
                    if (_isAlarmSet.value && _currentRouteGeoJson.value != null) {
                        val currentRemainingSeconds = (_remainingEta.value?.toDouble() ?: (_expectedDuration.value / 60.0)) * 60.0
                        val timeSavings = currentRemainingSeconds - globalWinner.weightedTotalCost
                        
                        if (timeSavings < 180.0) { // Penalty: 3 minutes
                            android.util.Log.d("MapViewModel", "New route discarded due to switching penalty. Savings (${timeSavings.toInt()}s) < 180s")
                            return@coroutineScope
                        }
                        android.util.Log.d("MapViewModel", "Switching penalty cleared. Time savings: ${timeSavings.toInt()}s")
                    }

                    // 5. Stitching & State Update
                    val legs = mutableListOf<JourneyLeg>()
                    
                    // Road: First Mile
                    val firstMileGeoJson = LineString.fromLngLats(globalWinner.firstMile.coordinates).toJson()
                    legs.add(JourneyLeg(
                        mode = TransportMode.ROAD,
                        geometry = firstMileGeoJson,
                        distanceMeters = globalWinner.firstMile.distance,
                        durationMillis = (globalWinner.firstMile.duration * 1000).toLong(),
                        startLat = start.latitude,
                        startLng = start.longitude,
                        endLat = globalWinner.startStation.latitude,
                        endLng = globalWinner.startStation.longitude,
                        endName = "Start Station"
                    ))

                    // Rail: Long Haul
                    val railGeoJson = LineString.fromLngLats(globalWinner.railLeg.coordinates).toJson()
                    legs.add(JourneyLeg(
                        mode = TransportMode.TRAIN,
                        geometry = railGeoJson,
                        distanceMeters = globalWinner.railLeg.distance,
                        durationMillis = (globalWinner.railLeg.duration * 1000).toLong(),
                        startLat = globalWinner.startStation.latitude,
                        startLng = globalWinner.startStation.longitude,
                        endLat = globalWinner.endStation.latitude,
                        endLng = globalWinner.endStation.longitude,
                        endName = "Destination Station"
                    ))

                    // Road: Last Mile
                    val lastMileGeoJson = LineString.fromLngLats(globalWinner.lastMile.coordinates).toJson()
                    legs.add(JourneyLeg(
                        mode = TransportMode.ROAD,
                        geometry = lastMileGeoJson,
                        distanceMeters = globalWinner.lastMile.distance,
                        durationMillis = (globalWinner.lastMile.duration * 1000).toLong(),
                        startLat = globalWinner.endStation.latitude,
                        startLng = globalWinner.endStation.longitude,
                        endLat = end.latitude,
                        endLng = end.longitude,
                        endName = destinationName.value ?: "Destination"
                    ))

                    _journeyLegs.value = legs

                    // Combine all coordinates for a single path
                    val combinedCoords = mutableListOf<Point>()
                    combinedCoords.addAll(globalWinner.firstMile.coordinates)
                    combinedCoords.addAll(globalWinner.railLeg.coordinates)
                    combinedCoords.addAll(globalWinner.lastMile.coordinates)

                    val mbLine = com.mapbox.geojson.LineString.fromLngLats(combinedCoords.map { 
                        com.mapbox.geojson.Point.fromLngLat(it.longitude(), it.latitude())
                    })
                    val geoJson = mbLine.toJson()

                    val totalDistance = globalWinner.firstMile.distance + globalWinner.railLeg.distance + globalWinner.lastMile.distance

                    _currentRouteGeoJson.value = geoJson
                    _expectedDuration.value = globalWinner.totalDuration
                    _expectedDistance.value = totalDistance
                    
                    val combinedSpeeds = mutableListOf<Double>()
                    combinedSpeeds.addAll(globalWinner.firstMile.speeds)
                    
                    // Calibrate rail average speed for ETA performance ratio
                    val railAvgSpeedMps = if (globalWinner.railLeg.duration > 0) {
                        globalWinner.railLeg.distance / globalWinner.railLeg.duration
                    } else 0.0
                    combinedSpeeds.addAll(globalWinner.railLeg.coordinates.map { railAvgSpeedMps })
                    
                    combinedSpeeds.addAll(globalWinner.lastMile.speeds)

                    _segmentSpeeds.value = combinedSpeeds
                    processRouteSuccess(geoJson, totalDistance, pushToService, combinedSpeeds)

                    launch(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Global optimal route calculated", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MapViewModel", "Failed to fetch Multi-modal route", e)
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, e.message ?: "Railway path unreachable.", android.widget.Toast.LENGTH_LONG).show()
                }
            } finally {
                _isRouting.value = false
            }
        }
    }

    private data class RouteSegment(val coordinates: List<Point>, val distance: Double, val duration: Double, val speeds: List<Double>)

    private suspend fun fetchOsrmSegment(sLat: Double, sLon: Double, eLat: Double, eLon: Double): RouteSegment? {
        try {
            val coords = "$sLon,$sLat;$eLon,$eLat"
            val response = osrmApiService.getRoute(coords)
            if (response.isSuccessful) {
                val route = response.body()?.routes?.firstOrNull() ?: return null
                val points = route.geometry.coordinates.map { Point.fromLngLat(it[0], it[1]) }
                val speeds = route.legs.firstOrNull()?.annotation?.speed ?: emptyList()
                return RouteSegment(points, route.distance, route.duration, speeds)
            }
        } catch (e: Exception) {
            android.util.Log.e("MapViewModel", "OSRM segment failed", e)
        }
        return null
    }

    private suspend fun fetchRailSegment(sLat: Double, sLon: Double, eLat: Double, eLon: Double): RouteSegment? {
        try {
            val response = openRailRoutingApiService.getRoute(points = listOf("$sLat,$sLon", "$eLat,$eLon"))
            if (response.isSuccessful) {
                val path = response.body()?.paths?.firstOrNull() ?: return null
                val points = path.points.coordinates.map { Point.fromLngLat(it[0], it[1]) }
                return RouteSegment(points, path.distance, path.time.toDouble() / 1000.0, emptyList())
            }
        } catch (e: Exception) {
            android.util.Log.e("MapViewModel", "Rail segment failed", e)
        }
        return null
    }

    private fun attemptOpenRailRoute(startLat: Double, startLon: Double, endLat: Double, endLon: Double, pushToService: Boolean): Boolean {
        return false // Optimized multi-modal handles all rail routing
    }

    private suspend fun findCandidateStations(lat: Double, lon: Double): List<LatLng> {
        try {
            val response = photonApiService.getSuggestions(
                query = "station",
                lat = lat,
                lon = lon,
                limit = 10
            )
            if (response.isSuccessful) {
                val features = response.body()?.features ?: return emptyList()
                val origin = Point.fromLngLat(lon, lat)

                // Filter for railway stations, calculate Haversine distance, and take top 5 within radius
                return features.asSequence().filter {
                    it.properties.osmKey == "railway" || it.properties.name?.lowercase()?.contains("station") == true
                }.map { feature ->
                    val stationPoint = Point.fromLngLat(feature.geometry.coordinates[0], feature.geometry.coordinates[1])
                    val distance = TurfMeasurement.distance(origin.toMapbox(), stationPoint.toMapbox(), TurfConstants.UNIT_KILOMETERS)
                    feature to distance
                }.filter { it.second <= maxStationSearchRadiusKm } // Strict distance gate
                .sortedBy { it.second } // Sort by Haversine distance ascending
                .take(5)
                .map { (feature, _) ->
                    LatLng(feature.geometry.coordinates[1], feature.geometry.coordinates[0])
                }.toList()
            }
        } catch (e: Exception) {
            android.util.Log.e("MapViewModel", "Failed to find candidate stations", e)
        }
        return emptyList()
    }

    private suspend fun findNearestStation(lat: Double, lon: Double): LatLng? {
        return findCandidateStations(lat, lon).firstOrNull()
    }

    private fun processRouteSuccess(geoJson: String, distance: Double, pushToService: Boolean, speeds: List<Double>) {
        // Update search history with road distance if this destination was just selected
        val currentDest = _destination.value
        if (currentDest != null) {
            val updatedHistory = _searchHistory.value.map { feature ->
                val c = feature.geometry.coordinates
                if (c[1] == currentDest.latitude && c[0] == currentDest.longitude) {
                    feature.copy(properties = feature.properties.copy(roadDistance = distance))
                } else {
                    feature
                }
            }
            _searchHistory.value = updatedHistory
            saveSearchHistory(updatedHistory)
        }

        // Also update _routeLine for map rendering
        val mbRoute = com.mapbox.geojson.LineString.fromJson(geoJson)
        val simplifiedPoints = routeDistanceEngine.simplifyPolyline(mbRoute.coordinates(), 5.0)
        val simplifiedRoute = com.mapbox.geojson.LineString.fromLngLats(simplifiedPoints)
        fullRouteLine = simplifiedRoute
        _routeLine.value = simplifiedRoute.toMapLibre()

        if (pushToService && _isAlarmSet.value) {
            val legsJson = if (_journeyLegs.value.isNotEmpty()) Json.encodeToString(_journeyLegs.value) else null
            val updateIntent = Intent(context, LocationAlarmService::class.java).apply {
                action = LocationAlarmService.ACTION_UPDATE_ROUTE
                putExtra("ROUTE_GEOJSON", geoJson)
                putExtra("JOURNEY_LEGS_JSON", legsJson)
                putExtra("EXPECTED_DURATION", _expectedDuration.value)
                putExtra("EXPECTED_DISTANCE", _expectedDistance.value)
                putExtra("SEGMENT_SPEEDS", speeds.toDoubleArray())
            }
            context.startService(updateIntent)
        }
        
        // Ensure distance and ETA are calculated immediately after the route is ready
        needsInitialCalculation = true
        _userLocation.value?.let { checkDistance(it) }
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

    private val _journeyCompleted = MutableStateFlow(false)
    val journeyCompleted: StateFlow<Boolean> = _journeyCompleted.asStateFlow()
    
    private var hasTriggeredArrival = false

    fun resetRouteState() {
        android.util.Log.d("MapViewModel", "resetRouteState: Clearing all routing and ETA states")
        _destination.value = null
        _destinationName.value = null
        _distanceToDestination.value = null
        _remainingEta.value = null
        _currentRouteGeoJson.value = null
        _journeyLegs.value = emptyList()
        _routeLine.value = null
        fullRouteLine = null
        _expectedDistance.value = 0.0
        _expectedDuration.value = 0.0
        _segmentSpeeds.value = emptyList()
        routeDistanceEngine.resetStats()
        lastCheckedLocation = null
        needsInitialCalculation = true
    }

    fun resetJourneyCompleted() {
        _journeyCompleted.value = false
        hasTriggeredArrival = false
        resetRouteState()
    }

    fun stopAlarm() {
        _isAlarmSet.value = false
        _isPreviewMode.value = false
        // Keep destinationName and distance for the summary sheet

        routeDistanceEngine.resetStats()
        alarmEngine.stop()

        val serviceIntent = Intent(context, LocationAlarmService::class.java)
        context.stopService(serviceIntent)
    }

    fun clearDestination() {
        if (!_isAlarmSet.value) {
            resetRouteState()
            _isPreviewMode.value = false
        }
    }

    private fun checkDistance(currentLocation: Location) {
        val dest = _destination.value ?: return

        // Fast straight-line distance calculation to destination
        val results = FloatArray(1)
        Location.distanceBetween(
            currentLocation.latitude, currentLocation.longitude,
            dest.latitude, dest.longitude,
            results
        )
        val straightLineDistance = results[0]
        
        // Distance-based throttling (5m) to reduce CPU overhead, bypassed within 100m of destination or on first calculation
        val lastLoc = lastCheckedLocation
        if (!needsInitialCalculation && lastLoc != null && straightLineDistance > 100.0 && currentLocation.distanceTo(lastLoc) < 5.0) {
            android.util.Log.d("MapViewModel", "checkDistance: Throttling calculations, distance moved < 5m")
            return
        }
        lastCheckedLocation = currentLocation
        needsInitialCalculation = false

        // 1. If in Preview Mode, prioritize the expected distance
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
                try {
                    val slicedLine = com.mapbox.turf.TurfMisc.lineSlice(snappedPoint, route.coordinates().last(), route)
                    
                    // Update map route line (sliced version) if it has at least 2 points
                    if (slicedLine.coordinates().size >= 2) {
                        _routeLine.value = slicedLine.toMapLibre()
                    }
                    
                    // Calculate distance along route
                    val distance = TurfMeasurement.length(slicedLine, TurfConstants.UNIT_METERS)
                    _distanceToDestination.value = formatDistance(distance.toInt())
                    
                    // Update speed and calculate ETA
                    routeDistanceEngine.updateAverageSpeed(currentLocation.speed.toDouble())
                    
                    // Segment-Aware Speed Correction
                    val currentSegmentSpeed = routeDistanceEngine.getCurrentSegmentSpeed(
                        route, 
                        userPoint, 
                        _segmentSpeeds.value
                    )
                    
                    val globalSpeed = if (_expectedDuration.value > 0) _expectedDistance.value / _expectedDuration.value else 0.0
                    val segmentSpeed = if (currentSegmentSpeed > 0) currentSegmentSpeed else globalSpeed

                    val etaMinutes = routeDistanceEngine.calculateCalibratedETA(
                        remainingDistanceMeters = distance,
                        globalExpectedSpeedMps = globalSpeed,
                        currentSegmentSpeedMps = segmentSpeed
                    )
                    
                    if (etaMinutes != Double.MAX_VALUE) {
                        _remainingEta.value = etaMinutes.roundToInt()
                        android.util.Log.d("MapViewModel", "checkDistance: distance=${distance.toInt()}m, eta=${etaMinutes.roundToInt()}min, speed=${currentLocation.speed}mps")
                    } else {
                        _remainingEta.value = null
                        android.util.Log.d("MapViewModel", "checkDistance: distance=${distance.toInt()}m, eta=WAITING_FOR_SPEED")
                    }
                    return
                } catch (e: Exception) {
                    // Turf lineSlice throws exception if start and end are the same point (arrived)
                    _distanceToDestination.value = formatDistance(0)
                    _remainingEta.value = 0
                    android.util.Log.d("MapViewModel", "checkDistance: Arrived at exact destination point")
                }
            } else {
                android.util.Log.w("MapViewModel", "checkDistance: Could not snap user to route line")
            }
        }

        // 3. Fallback to Haversine (ONLY if OSRM is unavailable)
        val distance = straightLineDistance

        _distanceToDestination.value = if (_isAlarmSet.value || _isPreviewMode.value) {
            formatDistance(distance.toInt())
        } else {
            null
        }

        // Auto-arrival detection (50m threshold)
        if (distance <= 50 && !hasTriggeredArrival && (_isAlarmSet.value || _isPreviewMode.value)) {
            android.util.Log.d("MapViewModel", "Arrival detected at distance: $distance. isAlarmSet: ${_isAlarmSet.value}")
            hasTriggeredArrival = true
            _journeyCompleted.value = true
            
            if (!_isAlarmSet.value) {
                // In preview mode, we just clear and stop
                _isPreviewMode.value = false
                _routeLine.value = null
                fullRouteLine = null
            }
            // If isAlarmSet is true, we wait for the Service's JOURNEY_COMPLETED_BROADCAST
            // which will call stopAlarm() and ensure data is saved.
        }

        _remainingEta.value = null
    }

    private fun formatDistance(meters: Int): String {
        return if (meters >= 1000) String.format(Locale.getDefault(), "%.1fkm", meters / 1000f) else "${meters}m"
    }
    
    fun saveRoute(destinationName: String, breadcrumbs: List<RouteBreadcrumbEntity>, alarmSettings: AlarmSettings) {
        val dest = _destination.value ?: return
        viewModelScope.launch {
            // Get latest history entry to extract actual path metrics
            val latestHistory = historyRepository.getLatestJourney()
            
            val route = SavedRouteEntity(
                destinationName = destinationName.ifBlank { "Unknown Destination" },
                mapDestinationName = null,
                destinationLat = dest.latitude,
                destinationLng = dest.longitude,
                transportMode = alarmSettings.transportMode,
                alarmSettings = alarmSettings,
                dateSaved = System.currentTimeMillis(),
                lastTakenTimestamp = System.currentTimeMillis(),
                routeGeoJson = latestHistory?.actualRouteGeoJson,
                actualDistanceMeters = latestHistory?.actualDistanceMeters ?: 0.0,
                estimatedDurationMillis = latestHistory?.durationMillis ?: 0
            )
            routeRepository.saveJourney(route, breadcrumbs)
        }
    }

    fun updateRoute(route: SavedRouteEntity) {
        viewModelScope.launch {
            routeRepository.updateSavedRoute(route)
        }
    }

    fun deleteJourneys(journeys: List<JourneyHistoryEntity>) {
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

    fun getBreadcrumbsForHistory(historyId: Long): kotlinx.coroutines.flow.Flow<List<RouteBreadcrumbEntity>> {
        return historyRepository.getBreadcrumbsForHistory(historyId)
    }

    fun startJourneyDirect(
        lat: Double, 
        lng: Double, 
        name: String, 
        settings: AlarmSettings,
        preLoadedRouteGeoJson: String? = null,
        preLoadedDistance: Double = 0.0,
        preLoadedDuration: Long = 0
    ) {
        android.util.Log.d("MapViewModel", "startJourneyDirect: Starting journey to $name, resetting state first")
        resetRouteState()
        
        _destination.value = LatLng(lat, lng)
        _destinationName.value = name
        _alarmSettings.value = settings
        _isPreviewMode.value = true
        
        if (preLoadedRouteGeoJson != null) {
            _currentRouteGeoJson.value = preLoadedRouteGeoJson
            _expectedDistance.value = preLoadedDistance
            _expectedDuration.value = preLoadedDuration.toDouble() / 1000.0
            
            try {
                val mbRoute = com.mapbox.geojson.LineString.fromJson(preLoadedRouteGeoJson)
                // Simplify preloaded geometry to reduce CPU overhead
                val simplifiedPoints = routeDistanceEngine.simplifyPolyline(mbRoute.coordinates(), 5.0)
                val simplifiedRoute = com.mapbox.geojson.LineString.fromLngLats(simplifiedPoints)
                fullRouteLine = simplifiedRoute
                _routeLine.value = simplifiedRoute.toMapLibre()
                android.util.Log.d("MapViewModel", "startJourneyDirect: Using pre-loaded simplified route")
            } catch (e: Exception) {
                android.util.Log.e("MapViewModel", "Failed to parse pre-loaded GeoJSON", e)
            }
        }
        
        _userLocation.value?.let { 
            checkDistance(it)
            if (preLoadedRouteGeoJson == null) {
                fetchRoute(it, LatLng(lat, lng))
            }
        }
    }

    fun startJourneyFromSavedRoute(route: SavedRouteEntity) {
        startJourneyDirect(
            route.destinationLat, 
            route.destinationLng, 
            route.destinationName, 
            route.alarmSettings,
            preLoadedRouteGeoJson = route.routeGeoJson,
            preLoadedDistance = route.actualDistanceMeters,
            preLoadedDuration = route.estimatedDurationMillis
        )
    }

    fun startJourneyFromHistory(history: JourneyHistoryEntity) {
        startJourneyDirect(
            history.destinationLat, 
            history.destinationLng, 
            history.destinationName, 
            history.alarmConfigAtTime,
            preLoadedRouteGeoJson = history.actualRouteGeoJson,
            preLoadedDistance = history.actualDistanceMeters,
            preLoadedDuration = history.durationMillis
        )
    }

    override fun onCleared() {
        super.onCleared()
        try {
            context.unregisterReceiver(reRouteReceiver)
            context.unregisterReceiver(journeyCompletedReceiver)
            context.unregisterReceiver(locationSettingsReceiver)
        } catch (e: Exception) {}
    }
}

class MapViewModelFactory(
    private val locationTrackingManager: LocationTrackingManager,
    private val alarmEngine: AlarmEngine,
    private val photonApiService: PhotonApiService,
    private val osrmApiService: PhotonApiService,
    private val openRailRoutingApiService: OpenRailRoutingApiService,
    private val routeRepository: RouteRepository,
    private val historyRepository: HistoryRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MapViewModel(
                locationTrackingManager, alarmEngine, 
                photonApiService, osrmApiService, openRailRoutingApiService,
                routeRepository, historyRepository, context
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

