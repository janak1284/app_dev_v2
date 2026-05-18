package com.janak.location.alarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.janak.location.alarm.alarm.AlarmEngine
import com.janak.location.alarm.api.RetrofitClient
import com.janak.location.alarm.location.LocationTrackingManager
import com.janak.location.alarm.viewmodel.MapViewModel
import com.janak.location.alarm.viewmodel.MapViewModelFactory
import com.janak.location.alarm.ui.theme.LocationAlarmTheme
import com.janak.location.alarm.ui.map.MapScreen
import com.janak.location.alarm.ui.home.HomeScreen
import com.janak.location.alarm.data.AppDatabase
import com.janak.location.alarm.data.repository.RouteRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current.applicationContext
            
            // Database and Repository
            val db = remember { AppDatabase.getDatabase(context) }
            val routeRepository = remember { RouteRepository(db) }
            
            // Components
            val alarmEngine = remember { AlarmEngine(context) }
            val locationTrackingManager = remember { LocationTrackingManager(context) }
            val photonApiService = remember { RetrofitClient.photonApiService }
            val osrmApiService = remember { RetrofitClient.osrmApiService }
            
            val viewModel: MapViewModel = viewModel(
                factory = MapViewModelFactory(
                    locationTrackingManager, 
                    alarmEngine, 
                    photonApiService,
                    osrmApiService,
                    routeRepository,
                    context
                )
            )

            var currentScreen by remember { mutableStateOf("home") }

            val themeMode by viewModel.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }

            LocationAlarmTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when (currentScreen) {
                        "home" -> HomeScreen(
                            viewModel = viewModel,
                            onNewJourneyClick = { currentScreen = "map" }
                        )
                        "map" -> MapScreen(
                            viewModel = viewModel,
                            onNavigateHome = { currentScreen = "home" }
                        )
                    }
                }
            }
        }
    }
}
