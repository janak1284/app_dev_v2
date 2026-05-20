package com.janak.location.alarm.ui.map

import android.Manifest
import android.location.Location
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.janak.location.alarm.service.LocationAlarmService
import com.janak.location.alarm.ui.alarm.ModernConfigurationSheet
import com.janak.location.alarm.ui.components.DestinationSearchField
import com.janak.location.alarm.ui.components.JourneySummarySheet
import com.janak.location.alarm.ui.settings.SettingsScreen
import com.janak.location.alarm.viewmodel.MapViewModel
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.geojson.Point
import android.view.MotionEvent
import kotlin.math.roundToInt

@Composable
fun MapScreen(viewModel: MapViewModel, onNavigateHome: () -> Unit) {
    var showSettings by remember { mutableStateOf(false) }
    var showSearchHistory by remember { mutableStateOf(false) }
    var showJourneyHistory by remember { mutableStateOf(false) }

    when {
        showSearchHistory -> {
            com.janak.location.alarm.ui.settings.SearchHistoryScreen(
                viewModel = viewModel,
                onBackClick = { showSearchHistory = false },
                onItemClick = { feature ->
                    viewModel.selectSearchResult(feature)
                    showSearchHistory = false
                    showSettings = false
                }
            )
        }
        showJourneyHistory -> {
            com.janak.location.alarm.ui.settings.JourneyHistoryScreen(
                viewModel = viewModel,
                onBackClick = { showJourneyHistory = false },
                onHistoryItemClick = { /* Handle click if needed */ },
                onReactivateClick = { showJourneyHistory = false }
            )
        }
        showSettings -> {
            SettingsScreen(
                viewModel = viewModel,
                onBackClick = { showSettings = false },
                onNavigateToSearchHistory = { showSearchHistory = true },
                onNavigateToJourneyHistory = { showJourneyHistory = true },
                onNavigateToSavedRoutes = { /* Add navigation logic if needed */ }
            )
        }
        else -> {
            MapContent(
                viewModel = viewModel, 
                onOpenSettings = { showSettings = true },
                onNavigateHome = onNavigateHome
            )
        }
    }
}

@Composable
fun MapContent(viewModel: MapViewModel, onOpenSettings: () -> Unit, onNavigateHome: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    
    val userLocation by viewModel.userLocation.collectAsState(initial = null)
    val destination by viewModel.destination.collectAsState()
    val destinationName by viewModel.destinationName.collectAsState()
    val isAlarmSet by viewModel.isAlarmSet.collectAsState()
    val isPreviewMode by viewModel.isPreviewMode.collectAsState()
    val distanceToDestination by viewModel.distanceToDestination.collectAsState()
    val remainingEta by viewModel.remainingEta.collectAsState()
    val alarmSettings by viewModel.alarmSettings.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()
    val journeyCompleted by viewModel.journeyCompleted.collectAsState()

    var showBottomSheet by remember { mutableStateOf(false) }
    val onDismissSheet = remember { { showBottomSheet = false } }
    var isSearchFocused by remember { mutableStateOf(false) }
    var isMapInteracting by remember { mutableStateOf(false) }
    var isFollowMode by remember { mutableStateOf(false) }

    var hasLocationPermission by remember { 
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) 
    }
    var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseLocation = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        
        if (fineLocation || coarseLocation) {
            hasLocationPermission = true
            viewModel.startLocationUpdates()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            viewModel.startLocationUpdates()
        }
    }

    // Initialize MapLibre
    remember {
        MapLibre.getInstance(context)
    }

    val mapView = remember {
        MapView(context).apply {
            onCreate(Bundle())
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isMapInteracting = true
                        focusManager.clearFocus()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        isMapInteracting = false
                    }
                }
                false
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Handle Map Clicks and Movement
    LaunchedEffect(mapInstance) {
        val map = mapInstance ?: return@LaunchedEffect
        map.addOnMapClickListener { latLng ->
            focusManager.clearFocus()
            if (!isAlarmSet) {
                viewModel.setDestination(latLng)
            }
            true
        }

        map.addOnMoveListener(object : MapLibreMap.OnMoveListener {
            override fun onMoveBegin(detector: org.maplibre.android.gestures.MoveGestureDetector) {
                isFollowMode = false
            }
            override fun onMove(detector: org.maplibre.android.gestures.MoveGestureDetector) {}
            override fun onMoveEnd(detector: org.maplibre.android.gestures.MoveGestureDetector) {}
        })
    }

    // Auto-follow User Location
    LaunchedEffect(userLocation, isFollowMode) {
        if (isFollowMode && userLocation != null) {
            mapInstance?.animateCamera(
                CameraUpdateFactory.newLatLng(
                    LatLng(userLocation!!.latitude, userLocation!!.longitude)
                )
            )
        }
    }

    // Reactive Location Component Activation
    LaunchedEffect(mapInstance, hasLocationPermission) {
        val map = mapInstance
        if (map != null && hasLocationPermission) {
            map.getStyle { style ->
                val locationComponent = map.locationComponent
                if (!locationComponent.isLocationComponentActivated) {
                    try {
                        locationComponent.activateLocationComponent(
                            LocationComponentActivationOptions.builder(context, style).build()
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("MapScreen", "Error activating LocationComponent", e)
                    }
                }
                if (locationComponent.isLocationComponentActivated) {
                    locationComponent.isLocationComponentEnabled = true
                    locationComponent.renderMode = RenderMode.COMPASS
                }
            }
        }
    }

    // Update Destination Marker
    LaunchedEffect(mapInstance, destination) {
        val map = mapInstance ?: return@LaunchedEffect
        val dest = destination
        
        if (map.style?.isFullyLoaded == true) {
            val style = map.style!!
            val sourceId = "destination-source"
            val layerId = "destination-layer"
            val imageId = "destination-marker"

            if (style.getImage(imageId) == null) {
                val drawable = androidx.core.content.ContextCompat.getDrawable(context, com.janak.location.alarm.R.drawable.ic_location_pin)
                drawable?.let {
                    style.addImage(imageId, it)
                }
            }

            var source = style.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>(sourceId)
            if (source == null) {
                source = org.maplibre.android.style.sources.GeoJsonSource(sourceId)
                style.addSource(source)
                
                val layer = org.maplibre.android.style.layers.SymbolLayer(layerId, sourceId)
                layer.setProperties(
                    org.maplibre.android.style.layers.PropertyFactory.iconImage(imageId),
                    org.maplibre.android.style.layers.PropertyFactory.iconSize(1.5f),
                    org.maplibre.android.style.layers.PropertyFactory.iconAnchor(org.maplibre.android.style.layers.Property.ICON_ANCHOR_BOTTOM)
                )
                style.addLayer(layer)
            }

            if (dest != null) {
                val point = Point.fromLngLat(dest.longitude, dest.latitude)
                source.setGeoJson(point)
            } else {
                source.setGeoJson(Point.fromLngLat(0.0, 0.0)) // Clear marker
            }
        }
    }

    // Observe Route Updates
    val routeLine by viewModel.routeLine.collectAsState()
    LaunchedEffect(routeLine, mapInstance) {
        val line = routeLine
        val map = mapInstance
        
        if (map?.style?.isFullyLoaded == true) {
            val style = map.style!!
            val sourceId = "route-source"
            val layerId = "route-layer"

            var source = style.getSourceAs<org.maplibre.android.style.sources.GeoJsonSource>(sourceId)
            
            if (line != null) {
                if (source == null) {
                    source = org.maplibre.android.style.sources.GeoJsonSource(sourceId)
                    style.addSource(source)
                }
                
                if (style.getLayer(layerId) == null) {
                    val layer = org.maplibre.android.style.layers.LineLayer(layerId, sourceId)
                    layer.setProperties(
                        org.maplibre.android.style.layers.PropertyFactory.lineColor(android.graphics.Color.BLUE),
                        org.maplibre.android.style.layers.PropertyFactory.lineWidth(5f),
                        org.maplibre.android.style.layers.PropertyFactory.lineCap(org.maplibre.android.style.layers.Property.LINE_CAP_ROUND),
                        org.maplibre.android.style.layers.PropertyFactory.lineJoin(org.maplibre.android.style.layers.Property.LINE_JOIN_ROUND)
                    )
                    style.addLayerBelow(layer, "destination-layer")
                }
                source.setGeoJson(org.maplibre.geojson.FeatureCollection.fromFeature(org.maplibre.geojson.Feature.fromGeometry(line)))
            } else {
                if (source != null) {
                    source.setGeoJson(org.maplibre.geojson.FeatureCollection.fromFeatures(emptyList()))
                }
            }
        }
    }

    // Initial Zoom Effect
    var isInitialZoomDone by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    
    LaunchedEffect(userLocation, mapInstance) {
        if (!isInitialZoomDone && userLocation != null && mapInstance != null) {
            mapInstance?.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(userLocation!!.latitude, userLocation!!.longitude),
                    15.0
                )
            )
            isInitialZoomDone = true
        }
    }

    // Zoom to destination when it changes
    LaunchedEffect(destination, mapInstance) {
        val dest = destination
        val map = mapInstance
        if (dest != null && map != null) {
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(dest.latitude, dest.longitude),
                    15.0
                )
            )
        }
    }

    val isPaneVisible = destination != null && !isSearchFocused && !isMapInteracting

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    focusManager.clearFocus()
                })
            }
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                map.getMapAsync { mapLibreMap ->
                    mapInstance = mapLibreMap
                    
                    mapLibreMap.uiSettings.isCompassEnabled = true
                    mapLibreMap.uiSettings.setCompassFadeFacingNorth(false)
                    mapLibreMap.uiSettings.setCompassGravity(android.view.Gravity.BOTTOM or android.view.Gravity.START)
                    mapLibreMap.uiSettings.setCompassMargins(48, 0, 0, 150)

                    if (mapLibreMap.style == null) {
                        val osmStyle = """
                        {
                          "version": 8,
                          "sources": {
                            "osm": {
                              "type": "raster",
                              "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
                              "tileSize": 256,
                              "attribution": "&copy; OpenStreetMap Contributors",
                              "maxzoom": 19
                            }
                          },
                          "layers": [
                            {
                              "id": "osm",
                              "type": "raster",
                              "source": "osm"
                            }
                          ]
                        }
                        """.trimIndent()
                        
                        mapLibreMap.setStyle(Style.Builder().fromJson(osmStyle))
                    }
                }
            }
        )
        
        // --- Top Search Bar ---
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onNavigateHome,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .background(MaterialTheme.colorScheme.surface, shape = CircleShape)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Home")
            }
            DestinationSearchField(
                query = searchQuery,
                onQueryChange = { viewModel.onSearchQueryChange(it) },
                results = searchResults,
                history = searchHistory,
                onResultClick = { viewModel.selectSearchResult(it) },
                isSearching = isSearching,
                onMenuClick = onOpenSettings,
                userLocation = userLocation,
                onFocusChanged = { isSearchFocused = it },
                modifier = Modifier.weight(1f)
            )
        }

        // --- Floating Buttons ---
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = if (isPaneVisible) 320.dp else 32.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { 
                    isFollowMode = true
                    userLocation?.let { loc ->
                        mapInstance?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(loc.latitude, loc.longitude),
                                15.0
                            )
                        )
                    }
                },
                containerColor = if (isFollowMode) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) {
                Icon(
                    if (isFollowMode) Icons.Default.LocationSearching else Icons.Default.MyLocation, 
                    contentDescription = "Focus on User"
                )
            }

            SmallFloatingActionButton(
                onClick = { viewModel.refreshLocation() },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Location")
            }
        }
        
        if (journeyCompleted) {
            JourneySummarySheet(
                initialDestinationName = destinationName ?: "",
                onDismissRequest = { 
                    viewModel.resetJourneyCompleted()
                },
                onSaveJourney = { routeName ->
                    viewModel.saveRoute(routeName, emptyList(), alarmSettings) 
                    viewModel.resetJourneyCompleted()
                }
            )
        }

        // UI Overlay for Permissions
        if (!hasLocationPermission) {
            Button(
                onClick = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(text = "Grant Location Permission")
            }
        }

        // --- Animated Status Card ---
        AnimatedVisibility(
            visible = isPaneVisible,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Card(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAlarmSet) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(28.dp)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (!isAlarmSet) {
                        IconButton(
                            onClick = { viewModel.clearDestination() },
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Discard Location")
                        }
                    }

                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isAlarmSet) {
                            StatusHeader(
                                title = "ALARM ACTIVE",
                                icon = Icons.Default.Lock,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            if (distanceToDestination != null) {
                                Text(
                                    text = distanceToDestination!!,
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                com.janak.location.alarm.ui.components.SkeletonBox(width = 160.dp, height = 48.dp)
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))

                            if (remainingEta != null) {
                                Text(
                                    text = "ETA: $remainingEta min",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                com.janak.location.alarm.ui.components.SkeletonBox(width = 100.dp, height = 24.dp)
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "DISTANCE TO TARGET",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { viewModel.toggleAlarm() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.Stop, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("TURN OFF")
                            }
                        } else if (isPreviewMode) {
                            StatusHeader(
                                title = "DESTINATION SET",
                                icon = Icons.Default.Route,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            if (distanceToDestination != null) {
                                Text(
                                    text = distanceToDestination!!,
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                com.janak.location.alarm.ui.components.SkeletonBox(width = 160.dp, height = 48.dp)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (remainingEta != null) {
                                Text(
                                    text = "ETA: $remainingEta min",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                com.janak.location.alarm.ui.components.SkeletonBox(width = 100.dp, height = 24.dp)
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = destinationName ?: "Selected Destination",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { showBottomSheet = true },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("SET UP ALARM")
                            }
                        } else {
                            StatusHeader(
                                title = "SELECT DESTINATION",
                                icon = Icons.Default.LocationOn,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap on map or use search",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
         
         if (showBottomSheet) {
             ConfigSheetWrapper(
                 viewModel = viewModel,
                 onDismiss = onDismissSheet
             )
         }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigSheetWrapper(
    viewModel: MapViewModel,
    onDismiss: () -> Unit
) {
    val alarmSettings by viewModel.alarmSettings.collectAsState()
    val scrollState = rememberScrollState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    val stableOnDismiss = remember(onDismiss) { onDismiss }

    ModernConfigurationSheet(
        initialSettings = alarmSettings,
        onDismissRequest = stableOnDismiss,
        onSaveSettings = { newSettings ->
            viewModel.updateAlarmSettings(newSettings)
            stableOnDismiss()
        },
        scrollState = scrollState,
        sheetState = sheetState
    )
}

@Composable
fun StatusHeader(title: String, icon: ImageVector, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

