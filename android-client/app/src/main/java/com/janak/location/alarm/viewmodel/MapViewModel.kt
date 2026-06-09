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
    private val locationTrackingManager: LocationTrackingManager,
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

    fun fetchTelemetryForDropdown(trainNum: String) {
        _railwaySearchError.value = null
        viewModelScope.launch {
            try {
                val response = RetrofitClient.railwayTelemetryApi.getTrainTelemetry(trainNum)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body.stationSequence.isEmpty()) {
                        _railwaySearchError.value = "Train not found or no stations available."
                    } else {
                        _stationSequence.value = body.stationSequence
                        _railwayGlobalStatus.value = body.etaString
                        _dataAgeAtFetchMs.value = (body.serverTime ?: 0L) - (body.timestampFetched ?: 0L)
                        _localUptimeAtFetchMs.value = SystemClock.elapsedRealtime()
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
    private var _currentTrainNumber: String? = null
    private val _railwayEtaStatus = MutableStateFlow<String?>(null)
    val railwayEtaStatus: StateFlow<String?> = _railwayEtaStatus.asStateFlow()

    private val _railwayGlobalStatus = MutableStateFlow<String?>(null)
    val railwayGlobalStatus: StateFlow<String?> = _railwayGlobalStatus.asStateFlow()

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
        _userLocation.value?.let { fetchRoute(it, latLng) }
    }

    fun clearDestination() { if (!_isAlarmSet.value) { resetRouteState(); _isPreviewMode.value = false } }

    fun toggleAlarm() { if (_isAlarmSet.value) stopAlarm() else startAlarm() }

    fun startAlarm() {
        val dest = _destination.value ?: return
        val settings = _alarmSettings.value
        _isAlarmSet.value = true; _isPreviewMode.value = false
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
            _currentTrainNumber?.let { putExtra("TRAIN_NUMBER", it) }
        }
        context.startForegroundService(intent)
    }

    fun stopAlarm() {
        _isAlarmSet.value = false; _isPreviewMode.value = false
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
            _currentTrainNumber?.let { t -> _destinationCode.value?.let { d -> viewModelScope.launch { fetchRailwayRoute(t, d) } } }
        } else { fetchRoadRoute(start, end, pushToService) }
    }

    private fun fetchRoadRoute(start: Location, end: LatLng, pushToService: Boolean) {
        _isRouting.value = true
        viewModelScope.launch {
            try {
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
        resetRouteState()
        _stationSequence.value = seq
        _railwayGlobalStatus.value = globalStatus
        _currentTrainNumber = tNum
        _destination.value = LatLng(lat, lon)
        _destinationName.value = name; _destinationCode.value = code
        _alarmSettings.value = _alarmSettings.value.copy(transportMode = TransportMode.TRAIN)
        _isPreviewMode.value = true; viewModelScope.launch { delay(50); fetchRailwayRoute(tNum, code) }
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
        _railwayEtaStatus.value = null; _distanceToDestination.value = null; _remainingEta.value = null
        _currentRouteGeoJson.value = null; _journeyLegs.value = emptyList(); _stationSequence.value = emptyList()
        _routeLine.value = null; fullRouteLine = null; _expectedDistance.value = 0.0; _expectedDuration.value = 0.0
        _segmentSpeeds.value = emptyList(); _alarmSettings.value = _alarmSettings.value.copy(transportMode = TransportMode.ROAD)
        routeDistanceEngine.resetStats(); lastCheckedLocation = null; needsInitialCalculation = true; hasTriggeredArrival = false
    }

    private fun checkDistance(currentLocation: Location) {
        val dest = _destination.value ?: return
        val results = FloatArray(1); Location.distanceBetween(currentLocation.latitude, currentLocation.longitude, dest.latitude, dest.longitude, results)
        val dist = results[0]
        if (!needsInitialCalculation && lastCheckedLocation != null && dist > 100 && currentLocation.distanceTo(lastCheckedLocation!!) < 5) return
        lastCheckedLocation = currentLocation; needsInitialCalculation = false
        _distanceToDestination.value = formatDistance(dist.toInt())

        if (_alarmSettings.value.transportMode == TransportMode.TRAIN) {
            val destStation = _stationSequence.value.find { it.stationCode == _destinationCode.value }
            if (destStation != null) _railwayEtaStatus.value = "ETA: ${calculateRelativeEta(destStation.arrival ?: "Unknown")}"
        }

        if (dist <= 50 && !hasTriggeredArrival && (_isAlarmSet.value || _isPreviewMode.value)) {
            hasTriggeredArrival = true; _journeyCompleted.value = true
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
    fun manualRefresh() { _currentTrainNumber?.let { fetchTelemetryForDropdown(it) } }

    fun saveRoute(name: String, breadcrumbs: List<RouteBreadcrumbEntity>, settings: AlarmSettings) {
        val dest = _destination.value ?: return
        viewModelScope.launch { 
            routeRepository.saveJourney(SavedRouteEntity(
                destinationName = name, mapDestinationName = null, destinationLat = dest.latitude, destinationLng = dest.longitude,
                transportMode = settings.transportMode, alarmSettings = settings, dateSaved = System.currentTimeMillis(),
                lastTakenTimestamp = System.currentTimeMillis(), actualDistanceMeters = 0.0, estimatedDurationMillis = 0L, routeGeoJson = _currentRouteGeoJson.value
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
    }

    fun startLocationUpdates() { viewModelScope.launch { locationTrackingManager.getLocationUpdates().collect { _userLocation.value = it; checkDistance(it) } } }
    fun refreshLocation() = startLocationUpdates()
}

class MapViewModelFactory(
    private val locationTrackingManager: LocationTrackingManager,
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
        return MapViewModel(locationTrackingManager, alarmEngine, photonApiService, osrmApiService, routeRepository, historyRepository, stationRepository, railwayTrackCacheDao, context) as T
    }
}
