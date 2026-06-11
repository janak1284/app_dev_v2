package com.janak.location.alarm.viewmodel

import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.janak.location.alarm.alarm.AlarmEngine
import com.janak.location.alarm.api.*
import com.janak.location.alarm.location.LocationTrackingManager
import com.janak.location.alarm.model.*
import com.janak.location.alarm.service.LocationAlarmService
import com.janak.location.alarm.location.LocationRepository
import com.janak.location.alarm.data.SettingsDataStore
import com.janak.location.alarm.util.AppLogger
import com.janak.location.alarm.util.PolylineDecoder
import com.janak.location.alarm.data.repository.RouteRepository
import com.janak.location.alarm.data.repository.HistoryRepository
import com.janak.location.alarm.data.repository.StationRepository
import com.janak.location.alarm.data.dao.RailwayTrackCacheDao
import com.janak.location.alarm.data.entity.RailwayTrackCacheEntity
import com.janak.location.alarm.data.entity.JourneyHistoryEntity
import com.janak.location.alarm.data.entity.RouteBreadcrumbEntity
import com.janak.location.alarm.data.entity.SavedRouteEntity
import com.janak.location.alarm.domain.RouteDistanceEngine
import androidx.core.content.edit
import androidx.core.content.ContextCompat
import org.maplibre.android.geometry.LatLng
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.SystemClock
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale
import kotlin.math.*
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
class MapViewModel(
    private val locationRepository: LocationRepository,
    private val settingsDataStore: SettingsDataStore,
    private val alarmEngine: AlarmEngine,
    private val photonApiService: PhotonApiService,
    private val osrmApiService: PhotonApiService,
    private val routeRepository: RouteRepository,
    private val historyRepository: HistoryRepository,
    private val stationRepository: StationRepository,
    private val railwayTrackCacheDao: RailwayTrackCacheDao,
    private val context: Context
) : ViewModel() {

    private val sharedPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    // --- State Flows ---
    val demoSettingsFlow = settingsDataStore.demoSettingsFlow

    private val _stationSequence = MutableStateFlow<List<StationSequenceItem>>(emptyList())
    val stationSequence: StateFlow<List<StationSequenceItem>> = _stationSequence.asStateFlow()

    private val _railwaySearchError = MutableStateFlow<String?>(null)
    val railwaySearchError: StateFlow<String?> = _railwaySearchError.asStateFlow()

    fun clearRailwaySearchError() { _railwaySearchError.value = null }

    fun clearRailwayData() {
        _stationSequence.value = emptyList()
        _railwaySearchError.value = null
        _railwayGlobalStatus.value = null
    }

    fun fetchTelemetryForDropdown(trainNum: String, forceRefresh: Boolean = false, ttlMins: Int? = 1) {
        _railwaySearchError.value = null
        viewModelScope.launch {
            try {
                val response = RetrofitClient.railwayTelemetryApi.getTrainTelemetry(trainNum, forceRefresh, ttlMins)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body.stationSequence.isEmpty()) {
                        _railwaySearchError.value = "Train not found or no stations available."
                    } else {
                        _stationSequence.value = body.stationSequence
                        _railwayGlobalStatus.value = body.etaString
                        _dataAgeAtFetchMs.value = (body.serverTime ?: 0L) - (body.timestampFetched ?: 0L)
                        _localUptimeAtFetchMs.value = SystemClock.elapsedRealtime()

                        // Calculate distance-based staleness warning
                        _railwayStaleDataWarning.value = null
                        if (body.lastUpdatedWebsiteMs != null && body.serverTime != null) {
                            val dataAgeMins = ((body.serverTime - body.lastUpdatedWebsiteMs) / 60000).toInt()
                            
                            val sIdx = body.stationSequence.indexOfLast { it.hasDeparted }.coerceAtLeast(0)
                            val nextIdx = (sIdx + 1).coerceAtMost(body.stationSequence.size - 1)
                            val lastStation = body.stationSequence[sIdx]
                            val nextStation = body.stationSequence[nextIdx]

                            if (lastStation.latitude != null && lastStation.longitude != null &&
                                nextStation.latitude != null && nextStation.longitude != null && sIdx != nextIdx) {
                                
                                val results = FloatArray(1)
                                android.location.Location.distanceBetween(
                                    lastStation.latitude, lastStation.longitude,
                                    nextStation.latitude, nextStation.longitude,
                                    results
                                )
                                val distanceMeters = results[0]
                                // Assume 40 km/h average speed (40000m / 60 = 666.6 m/min)
                                val travelTimeMins = (distanceMeters / 666.6).toInt()
                                // Min threshold of 45 mins to avoid false alarms on short segments
                                val thresholdMins = maxOf(45, travelTimeMins)
                                
                                AppLogger.d("MapViewModel", "Staleness Check: dataAge=$dataAgeMins mins, threshold=$thresholdMins mins, dist=$distanceMeters")

                                if (dataAgeMins > thresholdMins) {
                                    _railwayStaleDataWarning.value = "Warning: Train data has not been updated in $dataAgeMins mins."
                                }
                            } else if (dataAgeMins > 60) {
                                AppLogger.d("MapViewModel", "Staleness Check Fallback: dataAge=$dataAgeMins mins")
                                // Fallback if no valid coordinates found
                                _railwayStaleDataWarning.value = "Warning: Train data has not been updated in $dataAgeMins mins."
                            } else {
                                AppLogger.d("MapViewModel", "Staleness Check: Coordinates missing, but dataAge ($dataAgeMins) <= 60")
                            }
                        } else {
                            AppLogger.d("MapViewModel", "Staleness Check: lastUpdatedWebsiteMs=${body.lastUpdatedWebsiteMs}, serverTime=${body.serverTime}")
                        }
                    }
                } else { _railwaySearchError.value = "Train not found or server error." }
            } catch (e: Exception) {
                AppLogger.e("MapViewModel", "Failed to fetch telemetry", e)
                _railwaySearchError.value = "Network error. Please try again."
            }
        }
    }

    private val _userLocation = MutableStateFlow<Location?>(null)
    val userLocation: StateFlow<Location?> = _userLocation.asStateFlow()

    private val _routeLine = MutableStateFlow<org.maplibre.geojson.LineString?>(null)
    val routeLine: StateFlow<org.maplibre.geojson.LineString?> = _routeLine.asStateFlow()

    private val _destination = MutableStateFlow<LatLng?>(null)
    val destination: StateFlow<LatLng?> = _destination.asStateFlow()

    private val _destinationName = MutableStateFlow<String?>(null)
    val destinationName: StateFlow<String?> = _destinationName.asStateFlow()

    private val _destinationCode = MutableStateFlow<String?>(null)
    private val _currentTrainNumber = MutableStateFlow<String?>(null)
    val currentTrainNumber: StateFlow<String?> = _currentTrainNumber.asStateFlow()

    private val _railwayEtaStatus = MutableStateFlow<String?>(null)
    val railwayEtaStatus: StateFlow<String?> = _railwayEtaStatus.asStateFlow()

    private val _railwayGlobalStatus = MutableStateFlow<String?>(null)
    val railwayGlobalStatus: StateFlow<String?> = _railwayGlobalStatus.asStateFlow()

    private val _railwayStaleDataWarning = MutableStateFlow<String?>(null)
    val railwayStaleDataWarning: StateFlow<String?> = _railwayStaleDataWarning.asStateFlow()

    private val _currentRouteGeoJson = MutableStateFlow<String?>(null)
    val currentRouteGeoJson: StateFlow<String?> = _currentRouteGeoJson.asStateFlow()

    private val _journeyLegs = MutableStateFlow<List<JourneyLeg>>(emptyList())
    val journeyLegs: StateFlow<List<JourneyLeg>> = _journeyLegs.asStateFlow()

    private val _isAlarmSet = MutableStateFlow(false)
    val isAlarmSet: StateFlow<Boolean> = _isAlarmSet.asStateFlow()

    private val _distanceToDestination = MutableStateFlow<String?>(null)
    val distanceToDestination: StateFlow<String?> = _distanceToDestination.asStateFlow()

    private val _remainingEta = MutableStateFlow<Int?>(null)
    val remainingEta: StateFlow<Int?> = _remainingEta.asStateFlow()

    private val _isRouting = MutableStateFlow(false)
    val isRouting: StateFlow<Boolean> = _isRouting.asStateFlow()

    private val _alarmSettings = MutableStateFlow(AlarmSettings())
    val alarmSettings: StateFlow<AlarmSettings> = _alarmSettings.asStateFlow()

    private val _isPreviewMode = MutableStateFlow(false)
    val isPreviewMode: StateFlow<Boolean> = _isPreviewMode.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<PhotonFeature>>(emptyList())
    val searchResults: StateFlow<List<PhotonFeature>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isRefreshEnabled = MutableStateFlow(true)
    val isRefreshEnabled: StateFlow<Boolean> = _isRefreshEnabled.asStateFlow()

    private val _dataAgeAtFetchMs = MutableStateFlow(0L)
    val dataAgeAtFetchMs: StateFlow<Long> = _dataAgeAtFetchMs.asStateFlow()

    private val _localUptimeAtFetchMs = MutableStateFlow(0L)
    val localUptimeAtFetchMs: StateFlow<Long> = _localUptimeAtFetchMs.asStateFlow()

    private val _journeyCompleted = MutableStateFlow(false)
    val journeyCompleted: StateFlow<Boolean> = _journeyCompleted.asStateFlow()

    private val _themeMode = MutableStateFlow(sharedPrefs.getInt("theme_mode", 0))
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    private val _isLocationEnabled = MutableStateFlow(true)
    val isLocationEnabled: StateFlow<Boolean> = _isLocationEnabled.asStateFlow()

    val savedRoutes = routeRepository.allSavedRoutes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val journeyHistory = historyRepository.allHistory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Deletion & Removal State ---
    private val _routeToDelete = MutableStateFlow<SavedRouteEntity?>(null)
    val routeToDelete = _routeToDelete.asStateFlow()
    private val _itemToRemove = MutableStateFlow<PhotonFeature?>(null)
    val itemToRemove = _itemToRemove.asStateFlow()
    private val _deleteMultipleRoutes = MutableStateFlow(false)
    val deleteMultipleRoutes = _deleteMultipleRoutes.asStateFlow()
    private val _deleteMultipleJourneys = MutableStateFlow(false)
    val deleteMultipleJourneys = _deleteMultipleJourneys.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<PhotonFeature>>(emptyList())
    val searchHistory = _searchHistory.asStateFlow()

    // --- Internal Logic State ---
    private var fullRouteLine: com.mapbox.geojson.LineString? = null
    private var lastCheckedLocation: Location? = null
    private var needsInitialCalculation: Boolean = true
    private var hasTriggeredArrival = false
    private val routeDistanceEngine = RouteDistanceEngine()
    private val _expectedDuration = MutableStateFlow(0.0)
    private val _expectedDistance = MutableStateFlow(0.0)
    private val _segmentSpeeds = MutableStateFlow<List<Double>>(emptyList())

    init {
        _searchHistory.value = loadSearchHistory()
        setupSearchDebounce()
        startLocationUpdates()
        checkLocationStatus()
        registerLocationStatusReceiver()
        
        viewModelScope.launch {
            while (isActive) {
                updateRailwayEtaStatus()
                delay(30000)
            }
        }
    }

    private fun registerLocationStatusReceiver() {
        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                checkLocationStatus()
            }
        }
        context.registerReceiver(receiver, filter)
    }

    private fun checkLocationStatus() {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        _isLocationEnabled.value = isGpsEnabled || isNetworkEnabled
        AppLogger.d("MapViewModel", "Location Status: GPS=$isGpsEnabled, Network=$isNetworkEnabled, Overall=${_isLocationEnabled.value}")
    }

    // --- Helper for LngLat mapping ---
    private fun com.mapbox.geojson.Point.toLibre(): org.maplibre.geojson.Point = 
        org.maplibre.geojson.Point.fromLngLat(longitude(), latitude())

    // --- Search logic ---
    private fun loadSearchHistory(): List<PhotonFeature> {
        val jsonStr = sharedPrefs.getString("search_history", null) ?: return emptyList()
        return try { Json.decodeFromString<List<PhotonFeature>>(jsonStr) } catch (e: Exception) { emptyList() }
    }

    private fun saveSearchHistory(history: List<PhotonFeature>) {
        viewModelScope.launch(Dispatchers.IO) {
            sharedPrefs.edit { putString("search_history", Json.encodeToString(history)) }
        }
    }

    private fun setupSearchDebounce() {
        viewModelScope.launch {
            _searchQuery.debounce(350.milliseconds).distinctUntilChanged().collect { q ->
                if (q.length >= 3) performSearch(q) else _searchResults.value = emptyList()
            }
        }
    }

    private suspend fun performSearch(query: String) {
        _isSearching.value = true
        try {
            val res = photonApiService.getSuggestions(query, _userLocation.value?.latitude, _userLocation.value?.longitude)
            if (res.isSuccessful) _searchResults.value = res.body()?.features ?: emptyList()
        } catch (e: Exception) { AppLogger.e("MapViewModel", "Search failed", e) }
        finally { _isSearching.value = false }
    }

    fun onSearchQueryChange(q: String) { _searchQuery.value = q }

    fun selectSearchResult(feature: PhotonFeature) {
        val current = _searchHistory.value.toMutableList()
        current.removeAll { it.geometry.coordinates == feature.geometry.coordinates }
        current.add(0, feature); val updated = current.take(50)
        _searchHistory.value = updated; saveSearchHistory(updated)
        val c = feature.geometry.coordinates
        setDestination(LatLng(c[1], c[0]), feature.properties.name ?: feature.properties.street)
        _searchQuery.value = ""; _searchResults.value = emptyList()
    }

    fun removeFromHistory(feature: PhotonFeature) {
        val updated = _searchHistory.value.filter { it.geometry.coordinates != feature.geometry.coordinates }
        _searchHistory.value = updated; saveSearchHistory(updated)
    }

    fun clearSearchHistory() { _searchHistory.value = emptyList(); saveSearchHistory(emptyList()) }

    // --- Destination & Alarm Management ---

    fun setDestination(latLng: LatLng, name: String? = null) {
        if (_isAlarmSet.value) return
        resetRouteState()
        _destination.value = latLng; _destinationName.value = name; _isPreviewMode.value = true
        _userLocation.value?.let { 
            checkDistance(it)
            fetchRoute(it, latLng) 
        }
    }

    fun clearDestination() { if (!_isAlarmSet.value) { resetRouteState(); _isPreviewMode.value = false } }

    fun toggleAlarm() { if (_isAlarmSet.value) stopAlarm() else startAlarm() }

    private var journeyStartTimeMillis: Long = 0

    fun startAlarm() {
        val dest = _destination.value ?: return
        val settings = _alarmSettings.value
        _isAlarmSet.value = true; _isPreviewMode.value = false
        journeyStartTimeMillis = System.currentTimeMillis()
        viewModelScope.launch { settingsDataStore.setDemoPlaybackActive(true) }
        val intent = Intent(context, LocationAlarmService::class.java).apply {
            putExtra("DEST_LAT", dest.latitude); putExtra("DEST_LNG", dest.longitude)
            putExtra("DEST_NAME", _destinationName.value ?: "Destination")
            putExtra("TRANSPORT_MODE", settings.transportMode.name)
            putExtra("ROUTE_GEOJSON", _currentRouteGeoJson.value)
            putExtra("EXPECTED_DISTANCE", _expectedDistance.value)
            putExtra("DISTANCE_THRESHOLD", settings.distanceMeters.toFloat())
            putExtra("DISTANCE_ALARM_ENABLED", settings.isDistanceAlarmEnabled)
            putExtra("PREDICTIVE_MINUTES", settings.predictiveMinutes)
            putExtra("PREDICTIVE_ALARM_ENABLED", settings.isPredictiveAlarmEnabled)
            putExtra("RINGTONE_URI", settings.ringtoneUri?.toString())
            putExtra("VIBRATE", settings.isVibrateEnabled)
            _currentTrainNumber.value?.let { putExtra("TRAIN_NUMBER", it) }
        }
        context.startForegroundService(intent)
    }

    fun stopAlarm() {
        _isAlarmSet.value = false; _isPreviewMode.value = false
        viewModelScope.launch { settingsDataStore.setDemoPlaybackActive(false) }
        alarmEngine.stop(); context.stopService(Intent(context, LocationAlarmService::class.java))
    }

    fun updateAlarmSettings(settings: AlarmSettings) {
        val oldMode = _alarmSettings.value.transportMode
        _alarmSettings.value = settings
        if (settings.transportMode != oldMode && _destination.value != null) {
            _userLocation.value?.let { fetchRoute(it, _destination.value!!) }
        } else if (_isAlarmSet.value) { startAlarm() }
    }

    // --- Routing Engines ---

    private fun fetchRoute(start: Location, end: LatLng, pushToService: Boolean = false) {
        if (_alarmSettings.value.transportMode == TransportMode.TRAIN) {
            _currentTrainNumber.value?.let { t -> _destinationCode.value?.let { d -> viewModelScope.launch { fetchRailwayRoute(t, d) } } }
        } else { fetchRoadRoute(start, end, pushToService) }
    }

    private fun fetchRoadRoute(start: Location, end: LatLng, pushToService: Boolean) {
        _isRouting.value = true
        viewModelScope.launch {
            try {
                val settings = settingsDataStore.demoSettingsFlow.first()
                if (settings.isDemoEnabled) {
                    val fileName = if (settings.selectedRoute == "555S") "555S_variable_speed_route.gpx" else "55v_variable_speed_route.gpx"
                    val points = parseGpxGeometry(fileName)
                    if (points.size >= 2) {
                        val mbLine = com.mapbox.geojson.LineString.fromLngLats(points)
                        _currentRouteGeoJson.value = mbLine.toJson()
                        
                        val lastPoint = points.last()
                        _destination.value = LatLng(lastPoint.latitude(), lastPoint.longitude())
                        _destinationName.value = "Demo Route Destination"
                        
                        var dist = 0.0
                        for (i in 0 until points.size - 1) {
                            val results = FloatArray(1)
                            android.location.Location.distanceBetween(
                                points[i].latitude(), points[i].longitude(),
                                points[i+1].latitude(), points[i+1].longitude(),
                                results
                            )
                            dist += results[0]
                        }
                        
                        _expectedDistance.value = dist
                        _expectedDuration.value = dist / 10.0 
                        _segmentSpeeds.value = List(points.size) { 10.0 }
                        processRouteSuccess(mbLine.toJson(), dist, pushToService, _segmentSpeeds.value)
                        return@launch
                    }
                }

                val coords = "${start.longitude},${start.latitude};${end.longitude},${end.latitude}"
                val res = osrmApiService.getRoute(coords)
                if (res.isSuccessful && res.body() != null) {
                    val route = res.body()!!.routes.firstOrNull() ?: return@launch
                    val points = route.geometry.coordinates.map { com.mapbox.geojson.Point.fromLngLat(it[0], it[1]) }
                    val mbLine = com.mapbox.geojson.LineString.fromLngLats(points)
                    _currentRouteGeoJson.value = mbLine.toJson()
                    _expectedDistance.value = route.distance; _expectedDuration.value = route.duration
                    _segmentSpeeds.value = route.legs.firstOrNull()?.annotation?.speed ?: emptyList()
                    processRouteSuccess(mbLine.toJson(), route.distance, pushToService, _segmentSpeeds.value)
                }
            } catch (e: Exception) { AppLogger.e("MapViewModel", "Road routing failed", e) }
            finally { _isRouting.value = false }
        }
    }

    private fun parseGpxGeometry(fileName: String): List<com.mapbox.geojson.Point> {
        val points = mutableListOf<com.mapbox.geojson.Point>()
        try {
            val inputStream = context.assets.open(fileName)
            val parser = android.util.Xml.newPullParser()
            parser.setInput(inputStream, null)
            var eventType = parser.eventType
            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "trkpt") {
                    val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                    val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                    if (lat != null && lon != null) {
                        points.add(com.mapbox.geojson.Point.fromLngLat(lon, lat))
                    }
                }
                eventType = parser.next()
            }
            inputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return points
    }

    // --- Railway (Hybrid Granular Cloud) ---
    private data class ORRResult(val points: List<com.mapbox.geojson.Point>, val distance: Double, val duration: Double)

    private suspend fun callGlobalRoute(points: List<String>, sCode: String? = null, eCode: String? = null, bypassCache: Boolean = false, radiuses: String? = null): ORRResult? {
        if (points.size < 2) return null
        val uniquePoints = points.distinctUntilChangedList(); if (uniquePoints.size < 2) return null
        val segmentKey = if (sCode != null && eCode != null) "$sCode-$eCode" else null

        if (!bypassCache && segmentKey != null) {
            try {
                val cacheRes = RetrofitClient.railwayTelemetryApi.getGlobalRouteCache(segmentKey)
                if (cacheRes.isSuccessful && cacheRes.body() != null) {
                    val body = cacheRes.body()!!
                    AppLogger.d("MapViewModel", "🎯 CLOUD CACHE HIT: $segmentKey")
                    return ORRResult(PolylineDecoder.decode(body.points), body.distance, body.time.toDouble())
                }
            } catch (e: Exception) { }
        }

        return try {
            AppLogger.d("MapViewModel", "🌐 Direct ORR: $segmentKey" + if (bypassCache) " (bypass cache)" else "")
            val res = RetrofitClient.openRailRoutingApi.getTrackGeometry(points = uniquePoints, radiuses = radiuses)
            if (res.isSuccessful && res.body() != null) {
                val path = res.body()!!.paths.firstOrNull() ?: return null
                val result = ORRResult(PolylineDecoder.decode(path.points), path.distanceMeters, path.timeMillis / 1000.0)
                if (!bypassCache && segmentKey != null) {
                    viewModelScope.launch {
                        try { RetrofitClient.railwayTelemetryApi.saveGlobalRouteCache(RouteCacheSaveRequest(segmentKey, path.points, path.distanceMeters, result.duration)) } catch (e: Exception) {}
                    }
                }
                result
            } else {
                val error = res.errorBody()?.string() ?: ""
                AppLogger.e("MapViewModel", "Direct ORR failed with error: $error | Code: ${res.code()}")
                if (error.contains("Connection between locations not found") && sCode != null && eCode != null) { performAutoSnapFix(sCode, eCode, uniquePoints.first(), uniquePoints.last()) }
                null
            }
        } catch (e: Exception) { AppLogger.e("MapViewModel", "ORR failed", e); null }
    }

    private suspend fun performAutoSnapFix(sCode: String, eCode: String, sCoords: String, eCoords: String) {
        suspend fun snap(code: String, coords: String) {
            try {
                val res = RetrofitClient.openRailRoutingApi.getNearestPoint(coords)
                if (res.isSuccessful && res.body() != null) {
                    val n = res.body()!!
                    if (n.distance < 2000) RetrofitClient.railwayTelemetryApi.reportCoordinateCorrection(CoordinateCorrection(code, n.coordinates[1], n.coordinates[0]))
                }
            } catch (e: Exception) { }
        }
        snap(sCode, sCoords); snap(eCode, eCoords)
    }
    private fun trimPolylineUturns(
        polyline: List<com.mapbox.geojson.Point>, 
        startCoords: String, 
        endCoords: String
    ): Pair<List<com.mapbox.geojson.Point>, Double> {
        if (polyline.size < 5) return Pair(polyline, 0.0)
        
        val startLat = startCoords.split(",")[0].toDouble()
        val startLon = startCoords.split(",")[1].toDouble()
        val endLat = endCoords.split(",")[0].toDouble()
        val endLon = endCoords.split(",")[1].toDouble()

        var cutStartIndex = 0
        var cutEndIndex = polyline.size - 1

        val results = FloatArray(1)

        // 1. Find the absolute closest point to the destination
        var minEndDist = Float.MAX_VALUE
        var closestEndIdx = polyline.size - 1
        for (i in 0 until polyline.size) {
            val pt = polyline[i]
            android.location.Location.distanceBetween(pt.latitude(), pt.longitude(), endLat, endLon, results)
            if (results[0] <= 200.0) {
                closestEndIdx = i
                break
            }
            if (results[0] < minEndDist) {
                minEndDist = results[0]
                closestEndIdx = i
            }
        }
        
        cutEndIndex = closestEndIdx

        // 2. Find the absolute closest point to the start (search backwards from cutEndIndex)
        var minStartDist = Float.MAX_VALUE
        var closestStartIdx = 0
        for (i in cutEndIndex downTo 0) {
            val pt = polyline[i]
            android.location.Location.distanceBetween(pt.latitude(), pt.longitude(), startLat, startLon, results)
            if (results[0] <= 200.0) {
                closestStartIdx = i
                break
            }
            if (results[0] < minStartDist) {
                minStartDist = results[0]
                closestStartIdx = i
            }
        }
        
        cutStartIndex = closestStartIdx

        if (cutStartIndex >= cutEndIndex) {
            return Pair(polyline, 0.0)
        }

        val trimmed = polyline.subList(cutStartIndex, cutEndIndex + 1)
        
        var newDistance = 0.0
        for (i in 0 until trimmed.size - 1) {
            val p1 = trimmed[i]
            val p2 = trimmed[i+1]
            android.location.Location.distanceBetween(p1.latitude(), p1.longitude(), p2.latitude(), p2.longitude(), results)
            newDistance += results[0]
        }
        
        return Pair(trimmed, newDistance)
    }

    private fun calculateStraightLineDistance(p1: String, p2: String): Double {
        val c1 = p1.split(",")
        val c2 = p2.split(",")
        val loc1 = Location("").apply { latitude = c1[0].toDouble(); longitude = c1[1].toDouble() }
        val loc2 = Location("").apply { latitude = c2[0].toDouble(); longitude = c2[1].toDouble() }
        return loc1.distanceTo(loc2).toDouble()
    }

    private suspend fun fetchRailwayRoute(trainNumber: String, destinationCode: String) {
        _isRouting.value = true
        AppLogger.d("MapViewModel", "fetchRailwayRoute: Starting for train $trainNumber to $destinationCode")
        try {
            val seq = _stationSequence.value; if (seq.isEmpty()) {
                AppLogger.e("MapViewModel", "fetchRailwayRoute: Station sequence is empty")
                throw Exception("No sequence")
            }
            val dIdx = seq.indexOfFirst { it.stationCode == destinationCode }
            AppLogger.d("MapViewModel", "fetchRailwayRoute: Found destination $destinationCode at index $dIdx in sequence of size ${seq.size}")
            if (dIdx < 0) {
                AppLogger.e("MapViewModel", "fetchRailwayRoute: Destination code $destinationCode not found in sequence")
                throw Exception("Dest not found")
            }
            var sIdx = seq.indexOfLast { it.hasDeparted }
            AppLogger.d("MapViewModel", "fetchRailwayRoute: Last departed station index: $sIdx")
            if (sIdx < 0) sIdx = 0
            if (sIdx >= dIdx) {
                AppLogger.d("MapViewModel", "fetchRailwayRoute: Already at or past destination (sIdx=$sIdx, dIdx=$dIdx)")
                return
            }

            val stitched = mutableListOf<com.mapbox.geojson.Point>(); var totalDist = 0.0; var totalTime = 0.0
            
            // Filter sequence to only include stations with valid coordinates
            val validSeq = mutableListOf<Pair<StationSequenceItem, String>>()
            var lastCoords: String? = null
            for (i in sIdx..dIdx) {
                val station = seq[i]
                val coords = getStationCoords(station)
                if (coords != null && coords != lastCoords) {
                    validSeq.add(station to coords)
                    lastCoords = coords
                } else if (coords == null) {
                    AppLogger.w("MapViewModel", "fetchRailwayRoute: Skipping station ${station.stationCode} (${station.stationName}) due to missing coordinates")
                } else {
                    AppLogger.w("MapViewModel", "fetchRailwayRoute: Skipping duplicate consecutive station ${station.stationCode} at $coords")
                }
            }

            AppLogger.d("MapViewModel", "fetchRailwayRoute: Valid station sequence size: ${validSeq.size} (original: ${dIdx - sIdx + 1})")

            val allCoords = validSeq.map { it.second }
            val radiusesString = List(allCoords.size) { "500" }.joinToString(";")
            
            var totalStraightLineDist = 0.0
            for (i in 0 until allCoords.size - 1) {
                totalStraightLineDist += calculateStraightLineDistance(allCoords[i], allCoords[i+1])
            }
            
            val isLongDistance = totalStraightLineDist > 200_000.0 // > 200 km
            
            var primaryAttemptSuccess = false
            var fullRouteSegment: ORRResult? = null
            
            if (!isLongDistance && allCoords.size >= 2) {
                AppLogger.d("MapViewModel", "fetchRailwayRoute: Local train detected. Attempting full route multi-waypoint call")
                fullRouteSegment = callGlobalRoute(allCoords, bypassCache = true, radiuses = radiusesString)
                
                // If full route fails (common for dense local trains due to bad snapping),
                // attempt an end-to-end route using only the first and last stations.
                if (fullRouteSegment == null && allCoords.size > 2) {
                    AppLogger.d("MapViewModel", "fetchRailwayRoute: Full route failed, attempting end-to-end fallback")
                    fullRouteSegment = callGlobalRoute(listOf(allCoords.first(), allCoords.last()), bypassCache = true)
                }
            } else if (isLongDistance) {
                AppLogger.d("MapViewModel", "fetchRailwayRoute: Long distance train detected (${(totalStraightLineDist / 1000).toInt()} km). Skipping full route, enforcing segment-by-segment")
            }
            
            if (fullRouteSegment != null) {
                val (trimmedPoints, trimmedDist) = trimPolylineUturns(fullRouteSegment.points, allCoords.first(), allCoords.last())
                val effectiveDistance = if (trimmedDist > 0.0) trimmedDist else fullRouteSegment.distance
                val effectivePoints = if (trimmedDist > 0.0) trimmedPoints else fullRouteSegment.points
                
                if (effectiveDistance > totalStraightLineDist * 2.5) {
                    AppLogger.w("MapViewModel", "fetchRailwayRoute: Full route discarded due to U-turn heuristic (dist: $effectiveDistance, straight: $totalStraightLineDist)")
                } else {
                    AppLogger.d("MapViewModel", "fetchRailwayRoute: Full route success: ${effectivePoints.size} points")
                    stitched.addAll(effectivePoints)
                    totalDist += effectiveDistance
                    totalTime += fullRouteSegment.duration // keeping original duration for simplicity
                    primaryAttemptSuccess = true
                }
            } else {
                AppLogger.d("MapViewModel", "fetchRailwayRoute: Full route attempt failed")
            }

            if (!primaryAttemptSuccess) {
                AppLogger.d("MapViewModel", "fetchRailwayRoute: Falling back to segment-by-segment routing")
                for (i in 0 until validSeq.size - 1) {
                    val (s1, p1) = validSeq[i]
                    val (s2, p2) = validSeq[i+1]
                    
                    AppLogger.d("MapViewModel", "fetchRailwayRoute: Segment $i: ${s1.stationCode} to ${s2.stationCode}")
                    
                    var segment = callGlobalRoute(listOf(p1, p2), s1.stationCode, s2.stationCode)
                    if (segment == null) { 
                        AppLogger.d("MapViewModel", "fetchRailwayRoute: Segment ${s1.stationCode}-${s2.stationCode} failed, retrying...")
                        delay(1500L); segment = callGlobalRoute(listOf(p1, p2), s1.stationCode, s2.stationCode) 
                    }
                    
                    if (segment != null) {
                        val (trimmedPoints, trimmedDist) = trimPolylineUturns(segment.points, p1, p2)
                        if (trimmedDist > 0.0) {
                            segment = ORRResult(trimmedPoints, trimmedDist, segment.duration)
                        }
                    }

                    val straightLineDist = calculateStraightLineDistance(p1, p2)
                    
                    if (segment != null && segment.distance > straightLineDist * 2.5) {
                        AppLogger.w("MapViewModel", "fetchRailwayRoute: Segment discarded due to U-turn heuristic (dist: ${segment.distance}, straight: $straightLineDist)")
                        segment = null
                    }
                    
                    if (segment != null) { 
                        AppLogger.d("MapViewModel", "fetchRailwayRoute: Segment ${s1.stationCode}-${s2.stationCode} success: ${segment.points.size} points")
                        stitched.addAll(segment.points); totalDist += segment.distance; totalTime += segment.duration 
                    } else {
                        AppLogger.w("MapViewModel", "fetchRailwayRoute: Segment ${s1.stationCode}-${s2.stationCode} failed after retry or heuristic, falling back to direct line")
                        val c1 = p1.split(","); val c2 = p2.split(",")
                        stitched.add(com.mapbox.geojson.Point.fromLngLat(c1[1].toDouble(), c1[0].toDouble()))
                        stitched.add(com.mapbox.geojson.Point.fromLngLat(c2[1].toDouble(), c2[0].toDouble()))
                    }
                }
            }
            
            AppLogger.d("MapViewModel", "fetchRailwayRoute: Stitched route has ${stitched.size} points")
            if (stitched.size >= 2) {
                val mb = com.mapbox.geojson.LineString.fromLngLats(stitched)
                fullRouteLine = mb
                _routeLine.value = org.maplibre.geojson.LineString.fromLngLats(stitched.map { it.toLibre() })
                _currentRouteGeoJson.value = mb.toJson(); _expectedDistance.value = totalDist; _expectedDuration.value = totalTime
                _segmentSpeeds.value = List(stitched.size) { totalDist / (if (totalTime > 0) totalTime else 1.0) }
                processRouteSuccess(mb.toJson(), totalDist, _isAlarmSet.value, _segmentSpeeds.value)
                _userLocation.value?.let { checkDistance(it, force = true) }
            }
        } catch (e: Exception) { AppLogger.e("MapViewModel", "Railway routing failed", e) }
        finally { _isRouting.value = false }
    }

    private suspend fun getStationCoords(station: StationSequenceItem): String? {
        var lat = station.latitude; var lon = station.longitude
        if (lat == null || lon == null) {
            val db = stationRepository.getStationByCode(station.stationCode)
            if (db != null) { lat = db.latitude; lon = db.longitude }
        }
        return if (lat != null && lon != null) String.format(Locale.US, "%.6f,%.6f", lat, lon) else null
    }

    // --- State Handlers ---

    fun startRailwayJourney(tNum: String, name: String, code: String, lat: Double, lon: Double) {
        val seq = _stationSequence.value
        val globalStatus = _railwayGlobalStatus.value
        val staleWarning = _railwayStaleDataWarning.value
        resetRouteState()
        _stationSequence.value = seq
        _railwayGlobalStatus.value = globalStatus
        _railwayStaleDataWarning.value = staleWarning
        _currentTrainNumber.value = tNum
        _destination.value = LatLng(lat, lon)
        _destinationName.value = name; _destinationCode.value = code
        _alarmSettings.value = _alarmSettings.value.copy(transportMode = TransportMode.TRAIN)
        _isPreviewMode.value = true
        _userLocation.value?.let { checkDistance(it) }
        viewModelScope.launch { delay(50); fetchRailwayRoute(tNum, code) }
    }

    fun startJourneyDirect(lat: Double, lng: Double, name: String, settings: AlarmSettings, preRoute: String? = null, preDist: Double = 0.0, preDur: Long = 0) {
        resetRouteState(); _destination.value = LatLng(lat, lng); _destinationName.value = name
        _alarmSettings.value = settings; _isPreviewMode.value = true
        if (preRoute != null) {
            _currentRouteGeoJson.value = preRoute; _expectedDistance.value = preDist; _expectedDuration.value = preDur.toDouble() / 1000.0
            try {
                val mb = com.mapbox.geojson.LineString.fromJson(preRoute); fullRouteLine = mb
                _routeLine.value = org.maplibre.geojson.LineString.fromLngLats(mb.coordinates().map { it.toLibre() })
            } catch (e: Exception) {}
        }
        _userLocation.value?.let { checkDistance(it); if (preRoute == null) fetchRoute(it, LatLng(lat, lng)) }
    }

    fun startJourneyFromSavedRoute(r: SavedRouteEntity) = startJourneyDirect(r.destinationLat, r.destinationLng, r.destinationName, r.alarmSettings, r.routeGeoJson, r.actualDistanceMeters, r.estimatedDurationMillis)
    fun startJourneyFromHistory(h: JourneyHistoryEntity) = startJourneyDirect(h.destinationLat, h.destinationLng, h.destinationName, h.alarmConfigAtTime, h.actualRouteGeoJson, h.actualDistanceMeters, h.durationMillis)

    fun resetRouteState() {
        _destination.value = null; _destinationName.value = null; _destinationCode.value = null
        _currentTrainNumber.value = null
        _railwayEtaStatus.value = null; _railwayGlobalStatus.value = null; _railwayStaleDataWarning.value = null; _distanceToDestination.value = null; _remainingEta.value = null
        _currentRouteGeoJson.value = null; _journeyLegs.value = emptyList(); _stationSequence.value = emptyList()
        _routeLine.value = null; fullRouteLine = null; _expectedDistance.value = 0.0; _expectedDuration.value = 0.0
        _segmentSpeeds.value = emptyList(); _alarmSettings.value = _alarmSettings.value.copy(transportMode = TransportMode.ROAD)
        routeDistanceEngine.resetStats(); lastCheckedLocation = null; needsInitialCalculation = true; hasTriggeredArrival = false
        viewModelScope.launch { settingsDataStore.setDemoPlaybackActive(false) }
    }

    private fun checkDistance(currentLocation: Location, force: Boolean = false) {
        val dest = _destination.value ?: return
        val results = FloatArray(1); Location.distanceBetween(currentLocation.latitude, currentLocation.longitude, dest.latitude, dest.longitude, results)
        val dist = results[0]
        
        if (!force && !needsInitialCalculation && lastCheckedLocation != null && dist > 100 && currentLocation.distanceTo(lastCheckedLocation!!) < 5) return
        lastCheckedLocation = currentLocation; needsInitialCalculation = false

        // 1. Distance Calculation (Route vs Straight Line)
        if (fullRouteLine != null) {
            val userPoint = com.mapbox.geojson.Point.fromLngLat(currentLocation.longitude, currentLocation.latitude)
            val roadDist = routeDistanceEngine.calculateRemainingDistance(fullRouteLine!!, userPoint)
            _distanceToDestination.value = formatDistance(roadDist.toInt())
            
            val slicedRoute = routeDistanceEngine.getSlicedRoute(fullRouteLine!!, userPoint)
            if (slicedRoute != null) {
                _routeLine.value = org.maplibre.geojson.LineString.fromLngLats(slicedRoute.coordinates().map { it.toLibre() })
            }
            
            // 2. ETA Calculation (Active Route Mode)
            routeDistanceEngine.updateAverageSpeed(currentLocation.speed.toDouble())
            val currentSegmentSpeed = routeDistanceEngine.getCurrentSegmentSpeed(fullRouteLine!!, userPoint, _segmentSpeeds.value)
            val globalExpectedSpeed = if (_expectedDuration.value > 0) _expectedDistance.value / _expectedDuration.value else 0.0
            
            val eta = routeDistanceEngine.calculateCalibratedETA(
                remainingDistanceMeters = roadDist,
                globalExpectedSpeedMps = globalExpectedSpeed,
                currentSegmentSpeedMps = if (currentSegmentSpeed > 0) currentSegmentSpeed else globalExpectedSpeed
            )
            if (eta != Double.MAX_VALUE && eta >= 0) {
                _remainingEta.value = eta.toInt()
            } else {
                // Fallback to simple distance-based ETA if moving too slow
                val fallbackEta = (roadDist / (13.0)) / 60.0 // Assume ~45km/h fallback
                _remainingEta.value = fallbackEta.toInt()
            }
        } else {
            // Fallback: Straight-line distance
            _distanceToDestination.value = formatDistance(dist.toInt())
            
            // Fallback: Straight-line ETA (Rough estimate at 30km/h average)
            val speedMps = if (currentLocation.speed > 1.0) currentLocation.speed.toDouble() else 8.33 // 30km/h fallback
            val estimatedEtaMins = (dist / speedMps) / 60.0
            _remainingEta.value = estimatedEtaMins.toInt()
        }

        updateRailwayEtaStatus()

        if (dist <= 50 && !hasTriggeredArrival && _destination.value != null) {
            hasTriggeredArrival = true; _journeyCompleted.value = true
        }
    }

    private fun updateRailwayEtaStatus() {
        if (_alarmSettings.value.transportMode == TransportMode.TRAIN) {
            val destStation = _stationSequence.value.find { it.stationCode == _destinationCode.value }
            if (destStation != null) {
                val arr = destStation.arrival?.trim()
                if (arr.isNullOrEmpty() || arr == "-" || arr == "NA" || arr.equals("Source", ignoreCase = true) || arr.equals("Destination", ignoreCase = true)) {
                    _railwayEtaStatus.value = null // Scraped ETA unavailable, triggers fallback
                } else {
                    _railwayEtaStatus.value = "ETA: ${calculateRelativeEta(arr)}"
                }

                if (!destStation.status.isNullOrEmpty()) {
                    var statusText = destStation.status
                    if (statusText.contains("right time", ignoreCase = true)) {
                        statusText = statusText.replace("right time", "On time", ignoreCase = true)
                    }
                    _railwayGlobalStatus.value = statusText
                }
            }
        }
    }

    fun setThemeMode(mode: Int) { _themeMode.value = mode; sharedPrefs.edit { putInt("theme_mode", mode) } }
    fun setRouteToDelete(r: SavedRouteEntity?) { _routeToDelete.value = r }
    fun setItemToRemove(f: PhotonFeature?) { _itemToRemove.value = f }
    fun setDeleteMultipleRoutes(v: Boolean) { _deleteMultipleRoutes.value = v }
    fun setDeleteMultipleJourneys(v: Boolean) { _deleteMultipleJourneys.value = v }
    fun deleteRoute(r: SavedRouteEntity) = viewModelScope.launch { routeRepository.deleteRoute(r) }
    fun deleteRoutes(list: List<SavedRouteEntity>) = viewModelScope.launch { list.forEach { routeRepository.deleteRoute(it) } }
    fun deleteJourneys(list: List<JourneyHistoryEntity>) = viewModelScope.launch { historyRepository.deleteJourneys(list) }
    fun clearJourneyHistory() = viewModelScope.launch { historyRepository.clearAllHistory() }
    fun updateRoute(r: SavedRouteEntity) = viewModelScope.launch { routeRepository.updateSavedRoute(r) }
    fun getBreadcrumbsForHistory(id: Long) = historyRepository.getBreadcrumbsForHistory(id)
    fun resetJourneyCompleted() { _journeyCompleted.value = false; hasTriggeredArrival = false; resetRouteState() }
    fun fetchTelemetryForDropdownAsync(t: String) = fetchTelemetryForDropdown(t)
    fun manualRefresh() { _currentTrainNumber.value?.let { fetchTelemetryForDropdown(it, forceRefresh = true, ttlMins = null) } }
    fun setRefreshEnabled(enabled: Boolean) { _isRefreshEnabled.value = enabled }

    fun saveRoute(name: String, breadcrumbs: List<RouteBreadcrumbEntity>, settings: AlarmSettings) {
        val dest = _destination.value ?: return
        viewModelScope.launch { 
            val isDemo = settingsDataStore.demoSettingsFlow.first().isDemoEnabled
            var durationMillis = if (journeyStartTimeMillis > 0) System.currentTimeMillis() - journeyStartTimeMillis else (_expectedDuration.value * 1000).toLong()
            if (isDemo) durationMillis *= 30L
            
            routeRepository.saveJourney(SavedRouteEntity(
                destinationName = name, mapDestinationName = null, destinationLat = dest.latitude, destinationLng = dest.longitude,
                transportMode = settings.transportMode, alarmSettings = settings, dateSaved = System.currentTimeMillis(),
                lastTakenTimestamp = System.currentTimeMillis(), actualDistanceMeters = _expectedDistance.value, estimatedDurationMillis = durationMillis, routeGeoJson = _currentRouteGeoJson.value
            ), breadcrumbs) 
        }
    }

    private fun calculateRelativeEta(timeStr: String): String {
        return try {
            val parts = timeStr.split(":"); if (parts.size != 2) return timeStr
            val now = java.util.Calendar.getInstance()
            val arrival = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, parts[0].trim().toInt()); set(java.util.Calendar.MINUTE, parts[1].trim().toInt()) }
            if (arrival.before(now)) arrival.add(java.util.Calendar.DATE, 1)
            val diff = (arrival.timeInMillis - now.timeInMillis) / 60000
            if (diff <= 0) "Arriving..." else "${diff / 60}h ${diff % 60}m"
        } catch (e: Exception) { timeStr }
    }

    private fun <T> Iterable<T>.distinctUntilChangedList(): List<T> {
        val list = mutableListOf<T>()
        for (item in this) if (list.isEmpty() || list.last() != item) list.add(item)
        return list
    }

    private fun formatDistance(m: Int): String = if (m >= 1000) String.format(Locale.getDefault(), "%.1fkm", m / 1000f) else "${m}m"

    private fun processRouteSuccess(geoJson: String, distance: Double, pushToService: Boolean, speeds: List<Double>) {
        val mb = com.mapbox.geojson.LineString.fromJson(geoJson); fullRouteLine = mb
        _routeLine.value = org.maplibre.geojson.LineString.fromLngLats(mb.coordinates().map { it.toLibre() })
        if (pushToService && _isAlarmSet.value) {
            val intent = Intent(context, LocationAlarmService::class.java).apply {
                action = LocationAlarmService.ACTION_UPDATE_ROUTE
                putExtra("ROUTE_GEOJSON", geoJson); putExtra("EXPECTED_DISTANCE", distance); putExtra("SEGMENT_SPEEDS", speeds.toDoubleArray())
            }
            context.startService(intent)
        }
        _userLocation.value?.let { checkDistance(it, force = true) }
    }

    private var locationJob: Job? = null
    fun startLocationUpdates() { 
        if (locationJob?.isActive == true) return
        locationJob = viewModelScope.launch { 
            locationRepository.getLocationUpdates().collect { 
                val wasNull = _userLocation.value == null
                _userLocation.value = it
                checkDistance(it)
                if (wasNull && _destination.value != null && _routeLine.value == null && !_isRouting.value) {
                    fetchRoute(it, _destination.value!!)
                }
            } 
        } 
    }
    fun refreshLocation() = startLocationUpdates()

    fun setDemoEnabled(isEnabled: Boolean) = viewModelScope.launch { settingsDataStore.setDemoEnabled(isEnabled) }
    fun setSelectedRoute(route: String) = viewModelScope.launch { settingsDataStore.setSelectedRoute(route) }
}

class MapViewModelFactory(
    private val locationRepository: LocationRepository,
    private val settingsDataStore: SettingsDataStore,
    private val alarmEngine: AlarmEngine,
    private val photonApiService: PhotonApiService,
    private val osrmApiService: PhotonApiService,
    private val routeRepository: RouteRepository,
    private val historyRepository: HistoryRepository,
    private val stationRepository: StationRepository,
    private val railwayTrackCacheDao: RailwayTrackCacheDao,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return MapViewModel(locationRepository, settingsDataStore, alarmEngine, photonApiService, osrmApiService, routeRepository, historyRepository, stationRepository, railwayTrackCacheDao, context) as T
    }
}
