package com.janak.location.alarm.ui.map

import android.Manifest
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.util.Calendar
import android.app.TimePickerDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.janak.location.alarm.location.LocationTrackingManager
import com.janak.location.alarm.viewmodel.MapViewModel
import com.janak.location.alarm.viewmodel.MapViewModelFactory
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.geojson.Point
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import com.janak.location.alarm.ui.alarm.ModernConfigurationSheet
import com.janak.location.alarm.ui.components.DestinationSearchField
import com.janak.location.alarm.ui.settings.SettingsScreen
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import android.view.MotionEvent

@Composable
fun MapScreen(viewModel: MapViewModel) {
    var showSettings by remember { mutableStateOf(false) }

    if (showSettings) {
        SettingsScreen(
            viewModel = viewModel,
            onBackClick = { showSettings = false }
        )
    } else {
        MapContent(viewModel = viewModel, onOpenSettings = { showSettings = true })
    }
}

@Composable
fun MapContent(viewModel: MapViewModel, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusManager = LocalFocusManager.current
    
    val userLocation by viewModel.userLocation.collectAsState()


    val destination by viewModel.destination.collectAsState()
    val isAlarmSet by viewModel.isAlarmSet.collectAsState()
    val distanceToDestination by viewModel.distanceToDestination.collectAsState()
    val alarmSettings by viewModel.alarmSettings.collectAsState()

    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchHistory by viewModel.searchHistory.collectAsState()

    var showBottomSheet by remember { mutableStateOf(false) }
    var isSearchFocused by remember { mutableStateOf(false) }
    var isMapInteracting by remember { mutableStateOf(false) }

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

    // Handle Map Clicks
    LaunchedEffect(mapInstance) {
        mapInstance?.addOnMapClickListener { latLng ->
            focusManager.clearFocus()
            if (!isAlarmSet) {
                viewModel.setDestination(latLng)
            }
            true
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
                    locationComponent.cameraMode = CameraMode.TRACKING
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

    var hasZoomedToUser by remember { mutableStateOf(false) }
    
    // Auto-zoom to user location
    LaunchedEffect(userLocation, mapInstance) {
        val loc = userLocation
        val map = mapInstance
        if (loc != null && map != null && !hasZoomedToUser) {
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(loc.latitude, loc.longitude),
                    15.0
                )
            )
            hasZoomedToUser = true
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
                    
                    // Reposition compass to bottom-left to avoid overlap with search bar
                    mapLibreMap.uiSettings.isCompassEnabled = true
                    mapLibreMap.uiSettings.setCompassFadeFacingNorth(false) // Make it always visible for debugging/better UX
                    mapLibreMap.uiSettings.setCompassGravity(android.view.Gravity.BOTTOM or android.view.Gravity.START)
                    mapLibreMap.uiSettings.setCompassMargins(48, 0, 0, 150) // Adjust bottom margin to be above the logo

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
        
        // --- STEP 1: Top Search Bar ---
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
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .fillMaxWidth()
        )

        // --- STEP 1.5: Floating Buttons ---
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = if (isPaneVisible) 320.dp else 32.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { 
                    userLocation?.let { loc ->
                        mapInstance?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                LatLng(loc.latitude, loc.longitude),
                                15.0
                            )
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Focus on User")
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
        
        // --- STEP 2: Animated Status Card ---
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
                                title = "GUARDIAN ACTIVE",
                                icon = Icons.Default.Lock,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = distanceToDestination ?: "Calculating...",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold
                            )
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
                                Text("DEACTIVATE")
                            }
                        } else {
                            StatusHeader(
                                title = "DESTINATION SET",
                                icon = Icons.Default.Route,
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { showBottomSheet = true },
                                modifier = Modifier.fillMaxWidth().height(56.dp),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Icon(Icons.Default.Shield, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("ARM GUARDIAN")
                            }
                        }
                    }
                }
            }
        }
         
         if (showBottomSheet) {
             ModernConfigurationSheet(
                 initialSettings = alarmSettings,
                 onDismissRequest = { showBottomSheet = false },
                 onSaveSettings = { newSettings ->
                     viewModel.updateAlarmSettings(newSettings)
                     showBottomSheet = false
                 }
             )
         }
    }
}

@Composable
fun StatusHeader(title: String, icon: ImageVector, color: androidx.compose.ui.graphics.Color) {
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

