package com.janak.location.alarm.ui.map

import android.Manifest
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.janak.location.alarm.location.LocationTrackingManager
import com.janak.location.alarm.viewmodel.MapViewModel
import com.janak.location.alarm.viewmodel.MapViewModelFactory
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@Composable
fun MapScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // Dependencies (normally injected, but manual for MVP)
    val locationTrackingManager = remember { LocationTrackingManager(context) }
    val viewModel: MapViewModel = viewModel(
        factory = MapViewModelFactory(locationTrackingManager)
    )

    val userLocation by viewModel.userLocation.collectAsState()
    var hasLocationPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            hasLocationPermission = true
            viewModel.startLocationUpdates()
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
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

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                map.getMapAsync { mapLibreMap ->
                    mapLibreMap.setStyle("https://demotiles.maplibre.org/style.json") { style ->
                        if (hasLocationPermission) {
                            val locationComponent = mapLibreMap.locationComponent
                            locationComponent.activateLocationComponent(
                                LocationComponentActivationOptions.builder(context, style).build()
                            )
                            locationComponent.isLocationComponentEnabled = true
                            locationComponent.cameraMode = CameraMode.TRACKING
                            locationComponent.renderMode = RenderMode.COMPASS
                            
                            // MapLibre's LocationComponent handles its own updates by default using internal engine
                            // But we also have our own manager. For simple visualization, 
                            // MapLibre's default is often enough, but let's sync if needed.
                            // For now, enabling the component is enough to show the dot if permissions are granted.
                        }
                    }
                }
            }
        )
        
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
    }
}
