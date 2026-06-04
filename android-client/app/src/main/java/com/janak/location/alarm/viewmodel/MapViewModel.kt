package com.janak.location.alarm.viewmodel

import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.janak.location.alarm.alarm.AlarmEngine
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
import com.janak.location.alarm.data.repository.StationRepository
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
import com.janak.location.alarm.util.AppLogger
import java.util.Locale
import com.janak.location.alarm.api.*
import com.janak.location.alarm.util.PolylineDecoder
import com.janak.location.alarm.data.entity.RouteBreadcrumbEntity
import kotlinx.coroutines.flow.asStateFlow
import android.os.SystemClock
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@OptIn(FlowPreview::class)
class MapViewModel(
    private val locationTrackingManager: LocationTrackingManager,
    private val alarmEngine: AlarmEngine,
    private val photonApiService: PhotonApiService,
    private val osrmApiService: PhotonApiService,
    private val routeRepository: RouteRepository,
    private val historyRepository: HistoryRepository,
    private val stationRepository: StationRepository,
    private val context: Context
) : ViewModel() {

    private val _stationSequence = MutableStateFlow<List<StationSequenceItem>>(emptyList())
    val stationSequence: StateFlow<List<StationSequenceItem>> = _stationSequence.asStateFlow()

    fun fetchTelemetryForDropdown(trainNum: String) {
        viewModelScope.launch {
            try {
                val response = RetrofitClient.railwayTelemetryApi.getTrainTelemetry(trainNum)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    _stationSequence.value = body.stationSequence
                    
                    // Update age tracking states
                    _dataAgeAtFetchMs.value = (body.serverTime ?: 0L) - (body.timestampFetched ?: 0L)
                    _localUptimeAtFetchMs.value = SystemClock.elapsedRealtime()
                }
            } catch (e: Exception) {
                AppLogger.e("MapViewModel", "Failed to fetch telemetry for dropdown", e)
            }
        }
    }

    private val routeDistanceEngine = RouteDistanceEngine()
    private val maxStationSearchRadiusKm = 30.0

    // Timing Constants for Multi-Modal Realism
    private val STATION_OVERHEAD_SEC = 300.0 // 5 mins to reach platform/exit station
    private val TRANSIT_WAIT_BUFFER_SEC = 600.0 // 10 mins average wait for next vehicle
    private val RAIL_SPEED_CALIBRATION_FACTOR =
        0.8 // Assume 20% overhead for stops/track geometry mismatches

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
    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _userLocation = MutableStateFlow<Location?>(null)
    val userLocation: StateFlow<Location?> = _userLocation.asStateFlow()

    private val _routeLine = MutableStateFlow<LineString?>(null)
    val routeLine: StateFlow<LineString?> = _routeLine.asStateFlow()

    private val _destination = MutableStateFlow<LatLng?>(null)
    val destination: StateFlow<LatLng?> = _destination.asStateFlow()

    private val _destinationName = MutableStateFlow<String?>(null)
    val destinationName: StateFlow<String?> = _destinationName.asStateFlow()

    private val _destinationCode = MutableStateFlow<String?>(null)
    private var _currentTrainNumber: String? = null
    private val _railwayEtaStatus = MutableStateFlow<String?>(null)
    val railwayEtaStatus: StateFlow<String?> = _railwayEtaStatus.asStateFlow()

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

    // --- Manual Refresh & Age Tracking ---
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isRefreshEnabled = MutableStateFlow(true)
    val isRefreshEnabled: StateFlow<Boolean> = _isRefreshEnabled.asStateFlow()

    private val _dataAgeAtFetchMs = MutableStateFlow(0L)
    val dataAgeAtFetchMs: StateFlow<Long> = _dataAgeAtFetchMs.asStateFlow()

    private val _localUptimeAtFetchMs = MutableStateFlow(0L)
    val localUptimeAtFetchMs: StateFlow<Long> = _localUptimeAtFetchMs.asStateFlow()

    fun manualRefresh() {
        val tNum = _currentTrainNumber ?: return
        if (!_isRefreshEnabled.value) return

        viewModelScope.launch {
            try {
                _isRefreshing.value = true
                _isRefreshEnabled.value = false
                AppLogger.d("MapViewModel", "Manual refresh triggered for $tNum")
                
                val response = RetrofitClient.railwayTelemetryApi.getTrainTelemetry(tNum, forceRefresh = true)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    _stationSequence.value = body.stationSequence
                    
                    // Update age tracking states
                    _dataAgeAtFetchMs.value = (body.serverTime ?: 0L) - (body.timestampFetched ?: 0L)
                    _localUptimeAtFetchMs.value = SystemClock.elapsedRealtime()
                    
                    AppLogger.d("MapViewModel", "Manual refresh successful. Cache hit: ${body.cacheHit}")
                    
                    // Force UI update by bypassing the distance throttle
                    needsInitialCalculation = true
                    _userLocation.value?.let { checkDistance(it) }
                }
            } catch (e: Exception) {
                AppLogger.e("MapViewModel", "Manual refresh failed", e)
            } finally {
                _isRefreshing.value = false
                // Enforce 120-second cooldown
                delay(120_000L)
                _isRefreshEnabled.value = true
            }
        }
    }

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
                    AppLogger.d("MapViewModel", "Re-route request ignored (Debounce active)")
                    return
                    }
                    lastReRouteTime = currentTime

                    AppLogger.d("MapViewModel", "Re-route broadcast received")
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

    private val telemetryUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == LocationAlarmService.ACTION_TELEMETRY_UPDATED) {
                val jsonStr = intent.getStringExtra("STATION_SEQUENCE_JSON") ?: return
                try {
                    val sequence = Json.decodeFromString<List<StationSequenceItem>>(jsonStr)
                    _stationSequence.value = sequence
                    
                    val serverTime = intent.getLongExtra("SERVER_TIME", 0L)
                    val timestampFetched = intent.getLongExtra("TIMESTAMP_FETCHED", 0L)
                    
                    if (serverTime > 0 && timestampFetched > 0) {
                        _dataAgeAtFetchMs.value = serverTime - timestampFetched
                        _localUptimeAtFetchMs.value = SystemClock.elapsedRealtime()
                    }
                    
                    AppLogger.d("MapViewModel", "Telemetry updated from background broadcast")
                    
                    // Trigger immediate UI refresh
                    _userLocation.value?.let { checkDistance(it) }
                } catch (e: Exception) {
                    AppLogger.e("MapViewModel", "Failed to decode background telemetry", e)
                }
            }
        }
    }

    init {
        checkLocationSettings()
        setupSearchDebounce()

        val reRouteFilter = IntentFilter(LocationAlarmService.ACTION_RE_ROUTE)
        val journeyFilter = IntentFilter(LocationAlarmService.JOURNEY_COMPLETED_BROADCAST)
        val telemetryFilter = IntentFilter(LocationAlarmService.ACTION_TELEMETRY_UPDATED)
        val locationSettingsFilter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)

        ContextCompat.registerReceiver(
            context,
            reRouteReceiver,
            reRouteFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            context,
            journeyCompletedReceiver,
            journeyFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ContextCompat.registerReceiver(
            context,
            telemetryUpdateReceiver,
            telemetryFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        context.registerReceiver(locationSettingsReceiver, locationSettingsFilter)
    }

    fun checkLocationSettings() {
        try {
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled =
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            _isLocationEnabled.value = isGpsEnabled || isNetworkEnabled
        } catch (e: Exception) {
            AppLogger.e("MapViewModel", "Error checking location settings", e)
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
                .debounce(350)
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
            var combined = (historyMatches + remoteFeatures).distinctBy {
                "${it.geometry.coordinates[0]},${it.geometry.coordinates[1]}"
            }.take(6) // Limit matrix calculation to top 6 results for performance

            // MATRIX PIPELINE: Fetch actual road distances for all results in one call
            val userLoc = _userLocation.value
            if (userLoc != null && combined.isNotEmpty()) {
                try {
                    val coords = StringBuilder("${userLoc.longitude},${userLoc.latitude}")
                    combined.forEach { feature ->
                        coords.append(";${feature.geometry.coordinates[0]},${feature.geometry.coordinates[1]}")
                    }

                    val matrixResponse = photonApiService.getTable(coords.toString())
                    if (matrixResponse.isSuccessful) {
                        val distances = matrixResponse.body()?.distances?.firstOrNull()
                        // distances[0] is user to user (0), distances[1..N] are user to destinations
                        if (distances != null && distances.size > 1) {
                            combined = combined.mapIndexed { index, feature ->
                                // index + 1 because distances[0] is the source
                                val roadDist = if (index + 1 < distances.size) distances[index + 1] else null
                                feature.copy(properties = feature.properties.copy(roadDistance = roadDist))
                            }
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e("MapViewModel", "Matrix calculation failed", e)
                }
            }

            _searchResults.value = combined
        } catch (e: Exception) {
            AppLogger.e("MapViewModel", "Search failed", e)
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
        val legsJson =
            if (_journeyLegs.value.isNotEmpty()) Json.encodeToString(_journeyLegs.value) else null
        val duration = _expectedDuration.value
        val distance = _expectedDistance.value
        val speeds = _segmentSpeeds.value

        if (dest != null) {
            val serviceIntent = Intent(context, LocationAlarmService::class.java).apply {
                putExtra("DEST_LAT", dest.latitude)
                putExtra("DEST_LNG", dest.longitude)
                putExtra("DEST_NAME", destName)
                putExtra("TRANSPORT_MODE", settings.transportMode.name)
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
                
                // --- NEW: Add train number for telemetry refresh ---
                if (settings.transportMode == TransportMode.TRAIN) {
                    // Extract train number from search history or temporary state
                    // For now, we assume _destinationCode contains the train/station context needed
                    // We'll pass the trainNumber if we have it in a member variable
                    // (Actually we should store the current active train number)
                    _currentTrainNumber?.let { putExtra("TRAIN_NUMBER", it) }
                }
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
            AppLogger.d(
                "MapViewModel",
                "setDestination: $name, location: ${_userLocation.value}"
            )
            _userLocation.value?.let {
                checkDistance(it)
                fetchRoute(it, latLng)
            } ?: AppLogger.d(
                "MapViewModel",
                "setDestination: Waiting for user location to fetch route"
            )
        }
    }

    private fun fetchRoute(start: Location, end: LatLng, pushToService: Boolean = false) {
        val mode = _alarmSettings.value.transportMode
        if (mode == TransportMode.ROAD) {
            fetchRoadRoute(start, end, pushToService)
        } else if (mode == TransportMode.TRAIN) {
            // For Railway, we already handle routing via fetchRailwayRoute
            // This is called during startRailwayJourney.
            // If this is a re-route (e.g., settings change), we re-trigger it.
            val tNum = _currentTrainNumber
            val destCode = _destinationCode.value
            if (tNum != null && destCode != null) {
                viewModelScope.launch {
                    fetchRailwayRoute(tNum, destCode)
                }
            } else {
                AppLogger.w("MapViewModel", "Railway mode active but no train number/dest code for routing")
            }
        }
    }

    fun startRailwayJourney(trainNumber: String, destinationName: String, destinationCode: String, destLat: Double, destLon: Double) {
        AppLogger.d("MapViewModel", "startRailwayJourney: $trainNumber to $destinationName ($destinationCode)")

        // Preserve the current sequence before resetting state
        val currentSequence = _stationSequence.value

        resetRouteState()

        // Restore the sequence so fetchRailwayRoute can use it
        _stationSequence.value = currentSequence

        _currentTrainNumber = trainNumber

        _destination.value = LatLng(destLat, destLon)
        _destinationName.value = destinationName
        _destinationCode.value = destinationCode
        _alarmSettings.value = _alarmSettings.value.copy(transportMode = TransportMode.TRAIN)
        _isPreviewMode.value = true
        
        viewModelScope.launch {
            fetchRailwayRoute(trainNumber, destinationCode)
        }
    }

    private suspend fun fetchRailwayRoute(trainNumber: String, destinationCode: String) {
        _isRouting.value = true
        try {
            AppLogger.d("MapViewModel", "Calculating Hybrid Route geometry for $destinationCode...")
            
            // 1. Grab the sequence we already fetched in the UI Dialog
            val sequence = _stationSequence.value 
            val destIndex = sequence.indexOfFirst { it.stationCode == destinationCode }
            
            if (destIndex <= 0) {
                throw Exception("Destination is invalid or is the starting station.")
            }
            
            // 1. Collect all valid station coordinates for the sequence
            val routePoints = mutableListOf<String>()
            val missingCoords = mutableListOf<String>()
            
            for (i in 0..destIndex) {
                val station = sequence[i]
                var lat = station.latitude
                var lon = station.longitude
                
                if (lat == null || lon == null) {
                    val dbStation = stationRepository.getStationByCode(station.stationCode)
                    if (dbStation != null) {
                        lat = dbStation.latitude
                        lon = dbStation.longitude
                    }
                }
                
                if (lat != null && lon != null) {
                    // Use Locale.US to ensure decimal point '.' instead of locale-specific ','
                    val formatted = String.format(java.util.Locale.US, "%.6f,%.6f", lat, lon)
                    routePoints.add(formatted)
                } else {
                    missingCoords.add(station.stationName ?: station.stationCode)
                }
            }
            
            // 2. We only send Start and End points to ORR. 
            // Sending middle stations increases the risk of a "Connection not found" error if one coordinate is slightly off-track.
            val finalRoutePoints = mutableListOf<String>()
            if (routePoints.size >= 2) {
                finalRoutePoints.add(routePoints.first())
                finalRoutePoints.add(routePoints.last())
            }
            
            AppLogger.d("MapViewModel", "Collected ${routePoints.size} stations. Sending Source & Destination to ORR. Missing: $missingCoords")

            var orrPoints: List<com.mapbox.geojson.Point>? = null
            var orrDistance = 0.0
            var orrDuration = 0.0

            if (finalRoutePoints.size == 2) {
                // 3. Fetch the geometry for the whole path
                try {
                    val orrResponse = RetrofitClient.openRailRoutingApi.getTrackGeometry(finalRoutePoints)
                    
                    if (orrResponse.isSuccessful && orrResponse.body() != null) {
                        val paths = orrResponse.body()!!.paths
                        val bestPath = paths.minByOrNull { it.distanceMeters }
                        if (bestPath != null) {
                            orrPoints = PolylineDecoder.decode(bestPath.points)
                            orrDistance = bestPath.distanceMeters
                            orrDuration = bestPath.timeMillis / 1000.0
                        } else {
                            AppLogger.w("MapViewModel", "ORR success but empty paths list")
                        }
                    } else {
                        AppLogger.e("MapViewModel", "ORR API failed: ${orrResponse.code()} ${orrResponse.message()}")
                        AppLogger.d("MapViewModel", "Endpoints sent: Source=${finalRoutePoints.first()}, Dest=${finalRoutePoints.last()}")
                    }
                } catch (e: Exception) {
                    AppLogger.e("MapViewModel", "ORR Network/Parsing Error", e)
                }
            } else {
                AppLogger.w("MapViewModel", "Insufficient points for ORR request")
            }

            if (orrPoints != null) {
                // Use the fetched ORR points as the full route
                val mbLine = com.mapbox.geojson.LineString.fromLngLats(orrPoints)
                _routeLine.value = mbLine.toMapLibre()
                
                val geoJson = mbLine.toJson()
                
                _currentRouteGeoJson.value = geoJson
                _expectedDistance.value = orrDistance
                _expectedDuration.value = orrDuration
                
                // For Railway, we don't have segment speeds yet, use average
                val avgSpeed = if (orrDuration > 0) orrDistance / orrDuration else 15.0 // Default 15m/s (~54km/h)
                val speeds = List(orrPoints.size) { avgSpeed }
                _segmentSpeeds.value = speeds

                processRouteSuccess(geoJson, orrDistance, pushToService = _isAlarmSet.value, speeds = speeds)
                AppLogger.d("MapViewModel", "✅ Railway Track rendered! Total Points: ${orrPoints.size}")
            } else {
                // FALLBACK: "Connect-the-Dots" approach
                // Build a path using straight lines between all stations that have coordinates
                AppLogger.w("MapViewModel", "ORR failed. Building 'Connect-the-Dots' fallback route.")
                
                val dotPoints = mutableListOf<com.mapbox.geojson.Point>()
                var totalDistance = 0.0
                var lastPoint: com.mapbox.geojson.Point? = null

                for (i in 0..destIndex) {
                    val station = sequence[i]
                    var lat = station.latitude
                    var lon = station.longitude
                    
                    if (lat == null || lon == null) {
                        val dbStation = stationRepository.getStationByCode(station.stationCode)
                        if (dbStation != null) {
                            lat = dbStation.latitude
                            lon = dbStation.longitude
                        }
                    }
                    
                    if (lat != null && lon != null) {
                        val currentPoint = com.mapbox.geojson.Point.fromLngLat(lon, lat)
                        dotPoints.add(currentPoint)
                        
                        if (lastPoint != null) {
                            totalDistance += calculateHaversineDistance(
                                lastPoint.latitude(), lastPoint.longitude(),
                                currentPoint.latitude(), currentPoint.longitude()
                            )
                        }
                        lastPoint = currentPoint
                    }
                }

                if (dotPoints.size >= 2) {
                    val mbLine = com.mapbox.geojson.LineString.fromLngLats(dotPoints)
                    _routeLine.value = mbLine.toMapLibre()
                    val geoJson = mbLine.toJson()
                    
                    _currentRouteGeoJson.value = geoJson
                    _expectedDistance.value = totalDistance
                    // For duration, if we don't have it from ORR, we can't do much. 
                    // Let's assume a default speed of 50km/h (13.8 m/s)
                    val estimatedDuration = totalDistance / 13.8 
                    _expectedDuration.value = estimatedDuration
                    
                    val speeds = List(dotPoints.size) { 13.8 }
                    _segmentSpeeds.value = speeds

                    processRouteSuccess(geoJson, totalDistance, pushToService = _isAlarmSet.value, speeds = speeds)
                    AppLogger.d("MapViewModel", "✅ 'Connect-the-Dots' Route rendered! Total Points: ${dotPoints.size}")
                } else {
                    throw Exception("Insufficient coordinates for even a dot-to-dot route.")
                }
            }

        } catch (e: Exception) {
            AppLogger.e("MapViewModel", "⚠️ Hybrid route fetch failed. Engaging Haversine Fallback!", e)
            _currentRouteGeoJson.value = null
            _routeLine.value = null
        } finally {
            _isRouting.value = false
        }
    }

    private fun fetchRoadRoute(start: Location, end: LatLng, pushToService: Boolean = false) {
        AppLogger.d("MapViewModel", "fetchRoadRoute: start=$start, end=$end")
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
                AppLogger.e("MapViewModel", "Failed to fetch road route", e)
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
        val endStation: LatLng,
        val isSuboptimal: Boolean
    )

    private val routingSemaphore = Semaphore(3)

    private fun fetchTransitRoute(start: Location, end: LatLng, pushToService: Boolean = false) {
        AppLogger.d("MapViewModel", "fetchTransitRoute (Comprehensive): start=${start.latitude},${start.longitude}, end=${end.latitude},${end.longitude}")
        _isRouting.value = true
        viewModelScope.launch {
            try {
                coroutineScope {
                    // 0. Establish Baseline: Direct Road Duration
                    val directRoadSegment = fetchOsrmSegment(start.latitude, start.longitude, end.latitude, end.longitude)
                    val directRoadDuration = directRoadSegment?.duration ?: Double.MAX_VALUE
                    val transitThreshold = maxOf(directRoadDuration * 2.0, directRoadDuration + 1800.0)

                    // 1. Find candidate stations near BOTH origin and destination
                    // Increased limit to get more candidates for better linking
                    val startCandidates = findCandidateStations(start.latitude, start.longitude, limit = 15)
                    val endCandidates = findCandidateStations(end.latitude, end.longitude, limit = 15)

                    if (startCandidates.isEmpty() || endCandidates.isEmpty()) {
                        fetchRoadRoute(start, end, pushToService)
                        return@coroutineScope
                    }

                    // 2. Pre-fetch OSRM segments (First Mile)
                    val firstMileResults = startCandidates.map { station ->
                        async {
                            val segment = routingSemaphore.withPermit {
                                fetchOsrmSegment(start.latitude, start.longitude, station.latitude, station.longitude)
                            }
                            if (segment != null) station to segment else null
                        }
                    }.awaitAll().filterNotNull().toMap()

                    // 2b. Pre-fetch OSRM segments (Last Mile)
                    val lastMileResults = endCandidates.map { station ->
                        async {
                            val segment = routingSemaphore.withPermit {
                                fetchOsrmSegment(station.latitude, station.longitude, end.latitude, end.longitude)
                            }
                            if (segment != null) station to segment else null
                        }
                    }.awaitAll().filterNotNull().toMap()

                    // 3. Matrix Evaluation (NxM)
                    val matrixEvaluations = mutableListOf<kotlinx.coroutines.Deferred<EvaluatedMultiModalRoute?>>()

                    for ((sStation, fMile) in firstMileResults) {
                        for ((eStation, lMile) in lastMileResults) {
                            matrixEvaluations.add(async {
                                // Directionality Filter
                                if (!isDirectional(start, end, sStation, eStation)) return@async null

                                val rLeg = routingSemaphore.withPermit {
                                    fetchRailSegment(sStation.latitude, sStation.longitude, eStation.latitude, eStation.longitude)
                                }
                                
                                if (rLeg != null) {
                                    val railDurationCalibrated = rLeg.duration / RAIL_SPEED_CALIBRATION_FACTOR
                                    val totalDuration = fMile.duration + railDurationCalibrated + lMile.duration + (STATION_OVERHEAD_SEC * 2) + TRANSIT_WAIT_BUFFER_SEC
                                    
                                    val isSuboptimal = totalDuration > transitThreshold
                                    
                                    EvaluatedMultiModalRoute(fMile, rLeg.copy(duration = railDurationCalibrated), lMile, totalDuration, totalDuration, sStation, eStation, isSuboptimal)
                                } else null
                            })
                        }
                    }

                    // 4. Select Best Route
                    val evaluatedRoutes = matrixEvaluations.awaitAll().filterNotNull()
                    val globalWinner = evaluatedRoutes.minByOrNull { it.totalDuration }
                        ?: throw Exception("No transit path found.")

                    if (globalWinner.isSuboptimal) {
                        launch(kotlinx.coroutines.Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Note: This is not an optimal route.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }

                    // 5. Update UI/State with globalWinner
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
                AppLogger.e("MapViewModel", "Failed to fetch Multi-modal route", e)
                fetchRoadRoute(start, end, pushToService)
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(context, e.message ?: "Railway path unreachable.", android.widget.Toast.LENGTH_LONG).show()
                }
            } finally {
                _isRouting.value = false
            }
        }
    }

    private data class RouteSegment(
        val coordinates: List<Point>,
        val distance: Double,
        val duration: Double,
        val speeds: List<Double>
    )

    private suspend fun fetchOsrmSegment(
        sLat: Double,
        sLon: Double,
        eLat: Double,
        eLon: Double
    ): RouteSegment? {
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
            AppLogger.e("MapViewModel", "OSRM segment failed", e)
        }
        return null
    }

    private suspend fun fetchRailSegment(
        sLat: Double,
        sLon: Double,
        eLat: Double,
        eLon: Double
    ): RouteSegment? {
        try {
            val startPoint = "$sLat,$sLon"
            val endPoint = "$eLat,$eLon"
            val response = com.janak.location.alarm.api.RetrofitClient.openRailRoutingApi.getTrackGeometry(listOf(startPoint, endPoint))
            if (response.isSuccessful) {
                val orrResponse = response.body() ?: return null
                val path = orrResponse.paths.firstOrNull() ?: return null

                val decodedPoints = com.janak.location.alarm.util.PolylineDecoder.decode(path.points)
                // Convert Mapbox Point to MapLibre Point
                val mapLibrePoints = decodedPoints.map {
                    Point.fromLngLat(it.longitude(), it.latitude())
                }

                return RouteSegment(
                    coordinates = mapLibrePoints,
                    distance = path.distanceMeters,
                    duration = (path.timeMillis / 1000.0),
                    speeds = emptyList() // ORR doesn't provide per-segment speeds yet
                )
            }
        } catch (e: Exception) {
            AppLogger.e("MapViewModel", "Rail segment fetch failed", e)
        }
        return null
    }

    private fun isDirectional(start: Location, end: LatLng, sStation: LatLng, eStation: LatLng): Boolean {
        val bearingToDest = calculateBearing(start.latitude, start.longitude, end.latitude, end.longitude)
        val bearingToStartStation = calculateBearing(start.latitude, start.longitude, sStation.latitude, sStation.longitude)
        val bearingToEndStation = calculateBearing(start.latitude, start.longitude, eStation.latitude, eStation.longitude)

        fun angleDiff(a: Double, b: Double) = abs(((a - b + 540) % 360) - 180)

        return angleDiff(bearingToDest, bearingToStartStation) < 90.0 &&
               angleDiff(bearingToDest, bearingToEndStation) < 90.0
    }

    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val y = sin(Math.toRadians(lon2 - lon1)) * cos(Math.toRadians(lat2))
        val x = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
                sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(
            Math.toRadians(
                lon2 - lon1
            )
        )
        return Math.toDegrees(atan2(y, x))
    }


    private suspend fun findCandidateStations(lat: Double, lon: Double, limit: Int = 10): List<LatLng> {
        try {
            val stations = stationRepository.getNearbyStations(lat, lon, maxStationSearchRadiusKm, limit)
            return stations.map { LatLng(it.latitude, it.longitude) }
        } catch (e: Exception) {
            AppLogger.e("MapViewModel", "Failed to find candidate stations", e)
        }
        return emptyList()
    }

    private suspend fun findNearestStation(lat: Double, lon: Double): LatLng? {
        return findCandidateStations(lat, lon).firstOrNull()
    }

    private fun processRouteSuccess(
        geoJson: String,
        distance: Double,
        pushToService: Boolean,
        speeds: List<Double>
    ) {
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
            val legsJson =
                if (_journeyLegs.value.isNotEmpty()) Json.encodeToString(_journeyLegs.value) else null
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
        AppLogger.d("MapViewModel", "resetRouteState: Clearing all routing and ETA states")
        _destination.value = null
        _destinationName.value = null
        _destinationCode.value = null
        _railwayEtaStatus.value = null
        _distanceToDestination.value = null
        _remainingEta.value = null
        _currentRouteGeoJson.value = null
        _journeyLegs.value = emptyList()
        _stationSequence.value = emptyList() // Clear railway sequence
        _routeLine.value = null
        fullRouteLine = null
        _expectedDistance.value = 0.0
        _expectedDuration.value = 0.0
        _segmentSpeeds.value = emptyList()
        _alarmSettings.value = _alarmSettings.value.copy(transportMode = TransportMode.ROAD) // Default back to Road
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
        val mode = _alarmSettings.value.transportMode

        // Fast straight-line distance calculation to destination
        val results = FloatArray(1)
        Location.distanceBetween(
            currentLocation.latitude, currentLocation.longitude,
            dest.latitude, dest.longitude,
            results
        )
        val straightLineDistance = results[0]

        // Distance-based throttling (5m) to reduce CPU overhead
        val lastLoc = lastCheckedLocation
        if (!needsInitialCalculation && lastLoc != null && straightLineDistance > 100.0 && currentLocation.distanceTo(
                lastLoc
            ) < 5.0
        ) {
            AppLogger.d(
                "MapViewModel",
                "checkDistance: Throttling calculations, distance moved < 5m"
            )
            return
        }
        lastCheckedLocation = currentLocation
        needsInitialCalculation = false

        // 1. Transport Mode Specific Logic
        if (mode == TransportMode.TRAIN) {
            // Check for Null Island (0.0, 0.0) coordinates - Safeguard against missing station data
            if (dest.latitude == 0.0 && dest.longitude == 0.0) {
                _distanceToDestination.value = "Station coordinates unavailable"
                _railwayEtaStatus.value = "Coordinate Error ⚠️"
                return
            }

            // RAILWAY: Use Haversine distance for UI (Decoupled from "Final Approach" polyline)
            _distanceToDestination.value = formatDistance(straightLineDistance.toInt())
            
            // ETA: Extract official arrival and status from sequence
            val sequence = _stationSequence.value
            val destCode = _destinationCode.value
            val destStation = if (destCode != null) sequence.find { it.stationCode == destCode } else null
            
            if (destStation != null) {
                val confirmTktEta = destStation.arrival ?: "Unknown"
                val trainStatus = destStation.status ?: "On Time"
                
                val relativeEta = if (confirmTktEta != "Unknown") {
                    calculateRelativeEta(confirmTktEta)
                } else {
                    null
                }

                if (trainStatus.contains("Delay", ignoreCase = true)) {
                    _railwayEtaStatus.value = "ETA: ${relativeEta ?: confirmTktEta} ($trainStatus) ⚠️"
                } else {
                    _railwayEtaStatus.value = "ETA: ${relativeEta ?: confirmTktEta}"
                }
                _remainingEta.value = null // Clear math-based ETA
            } else {
                // Fallback: 60 km/h average = 1 km/min
                val estimatedMinutes = (straightLineDistance / 1000.0).roundToInt()
                _remainingEta.value = estimatedMinutes
                _railwayEtaStatus.value = null
            }
            
            // Slicing for MAP UI (if track geometry exists)
            val route = fullRouteLine
            if (route != null) {
                val userPoint = com.mapbox.geojson.Point.fromLngLat(currentLocation.longitude, currentLocation.latitude)
                try {
                    val feature = com.mapbox.turf.TurfMisc.nearestPointOnLine(userPoint, route.coordinates())
                    val snappedPoint = feature.geometry() as? com.mapbox.geojson.Point
                    if (snappedPoint != null) {
                        val slicedLine = com.mapbox.turf.TurfMisc.lineSlice(snappedPoint, route.coordinates().last(), route)
                        if (slicedLine.coordinates().size >= 2) {
                            _routeLine.value = slicedLine.toMapLibre()
                        }
                    }
                } catch (e: Exception) {
                    // Ignore slicing errors for long distance rail
                }
            }
        } else {
            // ROAD: Use high-precision route-snapping logic
            
            // If in Preview Mode, prioritize the expected distance
            if (_isPreviewMode.value && _expectedDistance.value > 0) {
                _distanceToDestination.value = formatDistance(_expectedDistance.value.toInt())
                _remainingEta.value = (_expectedDuration.value / 60.0).roundToInt()
                return
            }

            val route = fullRouteLine
            if (route != null && (_isAlarmSet.value || _isPreviewMode.value)) {
                val userPoint = com.mapbox.geojson.Point.fromLngLat(
                    currentLocation.longitude,
                    currentLocation.latitude
                )

                // Snapping & Slicing
                val feature =
                    com.mapbox.turf.TurfMisc.nearestPointOnLine(userPoint, route.coordinates())
                val snappedPoint = feature.geometry() as? com.mapbox.geojson.Point

                if (snappedPoint != null) {
                    try {
                        val slicedLine = com.mapbox.turf.TurfMisc.lineSlice(
                            snappedPoint,
                            route.coordinates().last(),
                            route
                        )

                        // Update map route line (sliced version)
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

                        val globalSpeed =
                            if (_expectedDuration.value > 0) _expectedDistance.value / _expectedDuration.value else 0.0
                        val segmentSpeed =
                            if (currentSegmentSpeed > 0) currentSegmentSpeed else globalSpeed

                        val etaMinutes = routeDistanceEngine.calculateCalibratedETA(
                            remainingDistanceMeters = distance,
                            globalExpectedSpeedMps = globalSpeed,
                            currentSegmentSpeedMps = segmentSpeed
                        )

                        if (etaMinutes != Double.MAX_VALUE) {
                            _remainingEta.value = kotlin.math.ceil(etaMinutes).toInt()
                            AppLogger.d(
                                "MapViewModel",
                                "checkDistance: distance=${distance.toInt()}m, eta=${etaMinutes.roundToInt()}min, speed=${currentLocation.speed}mps"
                            )
                        } else {
                            _remainingEta.value = null
                            AppLogger.d(
                                "MapViewModel",
                                "checkDistance: distance=${distance.toInt()}m, eta=WAITING_FOR_SPEED"
                            )
                        }
                        return
                    } catch (e: Exception) {
                        // Turf lineSlice throws exception if start and end are the same point (arrived)
                        _distanceToDestination.value = formatDistance(0)
                        _remainingEta.value = 0
                        AppLogger.d(
                            "MapViewModel",
                            "checkDistance: Arrived at exact destination point"
                        )
                    }
                } else {
                    AppLogger.w(
                        "MapViewModel",
                        "checkDistance: Could not snap user to route line"
                    )
                }
            }

            // Fallback to Haversine (ONLY if OSRM is unavailable)
            _distanceToDestination.value = if (_isAlarmSet.value || _isPreviewMode.value) {
                formatDistance(straightLineDistance.toInt())
            } else {
                null
            }
            _remainingEta.value = null
        }

        // 2. Arrival Check (Common for both modes)
        if (straightLineDistance <= 50 && !hasTriggeredArrival && (_isAlarmSet.value || _isPreviewMode.value)) {
            AppLogger.d(
                "MapViewModel",
                "Arrival detected at distance: $straightLineDistance. isAlarmSet: ${_isAlarmSet.value}"
            )
            hasTriggeredArrival = true
            _journeyCompleted.value = true

            if (!_isAlarmSet.value) {
                // In preview mode, we just clear and stop
                _isPreviewMode.value = false
                _routeLine.value = null
                fullRouteLine = null
            }
        }
    }

    private fun calculateRelativeEta(arrivalTimeStr: String): String {
        return try {
            val parts = arrivalTimeStr.split(":")
            if (parts.size != 2) return arrivalTimeStr
            
            val arrivalHour = parts[0].trim().toInt()
            val arrivalMin = parts[1].trim().toInt()
            
            val now = java.util.Calendar.getInstance()
            val arrival = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, arrivalHour)
                set(java.util.Calendar.MINUTE, arrivalMin)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            
            // Handle cross-day boundary
            if (arrival.before(now)) {
                arrival.add(java.util.Calendar.DATE, 1)
            }
            
            val diffMs = arrival.timeInMillis - now.timeInMillis
            val diffMins = (diffMs / 60000).toInt()
            
            if (diffMins <= 0) return "Arriving..."
            
            val h = diffMins / 60
            val m = diffMins % 60
            
            if (h > 0) {
                "${h} hr ${m} min"
            } else {
                "${m} min"
            }
        } catch (e: Exception) {
            arrivalTimeStr
        }
    }

    private fun calculateHaversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0].toDouble()
    }

    private fun formatDistance(meters: Int): String {
        return if (meters >= 1000) String.format(
            Locale.getDefault(),
            "%.1fkm",
            meters / 1000f
        ) else "${meters}m"
    }

    fun saveRoute(
        destinationName: String,
        breadcrumbs: List<RouteBreadcrumbEntity>,
        alarmSettings: AlarmSettings
    ) {
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
        AppLogger.d(
            "MapViewModel",
            "startJourneyDirect: Starting journey to $name, resetting state first"
        )
        resetRouteState()

        _destination.value = LatLng(lat, lng)
        _destinationName.value = name
        _alarmSettings.value = settings
        _isPreviewMode.value = true

        AppLogger.d("MapViewModel", "startJourneyDirect: Set Destination: $name")

        if (preLoadedRouteGeoJson != null) {
            _currentRouteGeoJson.value = preLoadedRouteGeoJson
            _expectedDistance.value = preLoadedDistance
            _expectedDuration.value = preLoadedDuration.toDouble() / 1000.0

            try {
                val mbRoute = com.mapbox.geojson.LineString.fromJson(preLoadedRouteGeoJson)
                // Simplify preloaded geometry to reduce CPU overhead
                val simplifiedPoints =
                    routeDistanceEngine.simplifyPolyline(mbRoute.coordinates(), 5.0)
                val simplifiedRoute = com.mapbox.geojson.LineString.fromLngLats(simplifiedPoints)
                fullRouteLine = simplifiedRoute
                _routeLine.value = simplifiedRoute.toMapLibre()
                AppLogger.d(
                    "MapViewModel",
                    "startJourneyDirect: Using pre-loaded simplified route"
                )
            } catch (e: Exception) {
                AppLogger.e("MapViewModel", "Failed to parse pre-loaded GeoJSON", e)
            }
        }

        _userLocation.value?.let {
            AppLogger.d("MapViewModel", "startJourneyDirect: User location available, checking distance")
            checkDistance(it)
            if (preLoadedRouteGeoJson == null) {
                fetchRoute(it, LatLng(lat, lng))
            }
        } ?: AppLogger.d("MapViewModel", "startJourneyDirect: Waiting for user location")
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
        } catch (e: Exception) {
        }
    }
}
class MapViewModelFactory(
    private val locationTrackingManager: LocationTrackingManager,
    private val alarmEngine: AlarmEngine,
    private val photonApiService: PhotonApiService,
    private val osrmApiService: PhotonApiService,
    private val routeRepository: RouteRepository,
    private val historyRepository: HistoryRepository,
    private val stationRepository: StationRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MapViewModel(
                locationTrackingManager, alarmEngine,
                photonApiService, osrmApiService,
                routeRepository, historyRepository, stationRepository, context
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
