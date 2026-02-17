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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.janak.location.alarm.alarm.AlarmHandler
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

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Dependencies
    val alarmHandler = remember { AlarmHandler(context) }
    val locationTrackingManager = remember { LocationTrackingManager(context) }
    val viewModel: MapViewModel = viewModel(
        factory = MapViewModelFactory(locationTrackingManager, alarmHandler)
    )

    val userLocation by viewModel.userLocation.collectAsState()


    val destination by viewModel.destination.collectAsState()
    val isAlarmSet by viewModel.isAlarmSet.collectAsState()
    val distanceToDestination by viewModel.distanceToDestination.collectAsState()

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
        android.util.Log.d("MapScreen", "PermissionCallback: fine=$fineLocation, coarse=$coarseLocation")
        
        if (fineLocation || coarseLocation) {
            hasLocationPermission = true
            viewModel.startLocationUpdates()
        }
    }

    LaunchedEffect(Unit) {
        android.util.Log.d("MapScreen", "LaunchedEffect: Checking initial permissions. hasPermission=$hasLocationPermission")
        if (!hasLocationPermission) {
            android.util.Log.d("MapScreen", "LaunchedEffect: Requesting permissions...")
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            android.util.Log.d("MapScreen", "LaunchedEffect: Already has permissions, starting updates")
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
            if (!isAlarmSet) {
                viewModel.setDestination(latLng)
            }
            true
        }
    }

    // Reactive Location Component Activation
    LaunchedEffect(mapInstance, hasLocationPermission) {
        val map = mapInstance
        android.util.Log.d("MapScreen", "LaunchedEffect(LocComp): map=${map != null}, hasPermission=$hasLocationPermission")
        if (map != null && hasLocationPermission) {
            map.getStyle { style ->
                android.util.Log.d("MapScreen", "LaunchedEffect(LocComp): Style loaded")
                val locationComponent = map.locationComponent
                if (!locationComponent.isLocationComponentActivated) {
                    try {
                        android.util.Log.d("MapScreen", "LaunchedEffect(LocComp): Activating component")
                        locationComponent.activateLocationComponent(
                            LocationComponentActivationOptions.builder(context, style).build()
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("MapScreen", "Error activating LocationComponent", e)
                    }
                }
                if (locationComponent.isLocationComponentActivated) {
                    android.util.Log.d("MapScreen", "LaunchedEffect(LocComp): Component activated, enabling...")
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
                // Add icon
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
                val point = org.maplibre.geojson.Point.fromLngLat(dest.longitude, dest.latitude)
                source?.setGeoJson(point) // Update directly
            } else {
                 // Clear if null (optional, though logic usually keeps it once set for now)
            }
        }
    }

    var hasZoomedToUser by remember { mutableStateOf(false) }
    
    // Auto-zoom to user location
    LaunchedEffect(userLocation, mapInstance) {
        val loc = userLocation
        val map = mapInstance
        if (loc != null && map != null && !hasZoomedToUser) {
            android.util.Log.d("MapScreen", "Auto-zooming to ${loc.latitude}, ${loc.longitude}")
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(loc.latitude, loc.longitude),
                    15.0
                )
            )
            hasZoomedToUser = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                map.getMapAsync { mapLibreMap ->
                    mapInstance = mapLibreMap // Save instance
                    
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
        
        // UI Overlay
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
        } else if (userLocation == null) {
            // Show waiting for location
             Card(
                 modifier = Modifier
                     .align(Alignment.TopCenter)
                     .padding(16.dp)
             ) {
                 Text(
                     text = "Waiting for location...",
                     modifier = Modifier.padding(8.dp),
                     style = MaterialTheme.typography.bodyMedium
                 )
             }
        }
        
        // Alarm Control UI
         if (destination != null) {
             Card(
                 modifier = Modifier
                     .align(Alignment.BottomCenter)
                     .padding(16.dp)
                     .fillMaxWidth()
             ) {
                 Column(
                     modifier = Modifier.padding(16.dp),
                     horizontalAlignment = Alignment.CenterHorizontally
                 ) {
                     if (isAlarmSet) {
                         Text(
                             text = "Alarm Active!", 
                             style = MaterialTheme.typography.titleLarge,
                             color = MaterialTheme.colorScheme.primary
                         )
                         Spacer(modifier = Modifier.height(8.dp))
                         Text(
                             text = "Distance: ${distanceToDestination ?: "Calculating..."}",
                             style = MaterialTheme.typography.bodyLarge
                         )
                         Spacer(modifier = Modifier.height(16.dp))
                         Button(
                             onClick = { viewModel.toggleAlarm() },
                             colors = ButtonDefaults.buttonColors(
                                 containerColor = MaterialTheme.colorScheme.error
                             )
                         ) {
                             Text("Stop Alarm")
                         }
                     } else {
                         Text(
                             text = "Destination Selected",
                             style = MaterialTheme.typography.titleMedium
                         )
                         Spacer(modifier = Modifier.height(16.dp))
                         Button(
                             onClick = { viewModel.toggleAlarm() }
                         ) {
                             Text("Set Alarm (500m)")
                         }
                     }
                 }
             }
         }
    }
}
