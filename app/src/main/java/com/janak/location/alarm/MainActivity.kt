package com.janak.location.alarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import com.janak.location.alarm.data.entity.SavedRouteEntity
import com.janak.location.alarm.ui.settings.EditRouteSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current.applicationContext
            var isInitialized by remember { mutableStateOf(false) }
            
            var routeRepository by remember { mutableStateOf<RouteRepository?>(null) }
            var historyRepository by remember { mutableStateOf<com.janak.location.alarm.data.repository.HistoryRepository?>(null) }
            var alarmEngine by remember { mutableStateOf<AlarmEngine?>(null) }
            var locationTrackingManager by remember { mutableStateOf<LocationTrackingManager?>(null) }

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    val database = AppDatabase.getDatabase(context)
                    routeRepository = RouteRepository(database)
                    historyRepository = com.janak.location.alarm.data.repository.HistoryRepository(database)
                    alarmEngine = AlarmEngine(context)
                    locationTrackingManager = LocationTrackingManager(context)
                    isInitialized = true
                }
            }

            if (!isInitialized) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val photonApiService = remember { RetrofitClient.photonApiService }
                val osrmApiService = remember { RetrofitClient.osrmApiService }
                val openRailRoutingApiService = remember { RetrofitClient.openRailRoutingApiService }

                val viewModel: MapViewModel = viewModel(
                    factory = MapViewModelFactory(
                        locationTrackingManager!!,
                        alarmEngine!!,
                        photonApiService,
                        osrmApiService,
                        openRailRoutingApiService,
                        routeRepository!!,
                        historyRepository!!,
                        context
                    )
                )
                var currentScreen by remember { mutableStateOf("home") }
                var selectedHistoryId by remember { mutableStateOf<Long?>(null) }
                var routeToEdit by remember { mutableStateOf<SavedRouteEntity?>(null) }

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
                            "home" -> {
                                HomeScreen(
                                    viewModel = viewModel,
                                    onNewJourneyClick = { currentScreen = "map" },
                                    onSettingsClick = { currentScreen = "settings" },
                                    onManageJourneysClick = { currentScreen = "journey_history" },
                                    onManageSearchesClick = { currentScreen = "search_history" }
                                )
                            }
                            "map" -> {
                                androidx.activity.compose.BackHandler { currentScreen = "home" }
                                MapScreen(
                                    viewModel = viewModel,
                                    onNavigateHome = { currentScreen = "home" }
                                )
                            }
                            "settings" -> {
                                androidx.activity.compose.BackHandler { currentScreen = "home" }
                                com.janak.location.alarm.ui.settings.SettingsScreen(
                                    viewModel = viewModel,
                                    onBackClick = { currentScreen = "home" },
                                    onNavigateToSearchHistory = { currentScreen = "search_history" },
                                    onNavigateToJourneyHistory = { currentScreen = "journey_history" },
                                    onNavigateToSavedRoutes = { currentScreen = "saved_routes" }
                                )
                            }
                            "saved_routes" -> {
                                 androidx.activity.compose.BackHandler { currentScreen = "settings" }
                                 com.janak.location.alarm.ui.settings.SavedRoutesScreen(
                                     viewModel = viewModel,
                                     onBackClick = { currentScreen = "settings" },
                                     onEditRouteClick = { route -> routeToEdit = route },
                                     onRouteClick = { currentScreen = "map" }
                                 )
                             }
                            "journey_history" -> {
                                androidx.activity.compose.BackHandler { currentScreen = "settings" }
                                com.janak.location.alarm.ui.settings.JourneyHistoryScreen(
                                    viewModel = viewModel,
                                    onBackClick = { currentScreen = "settings" },
                                    onHistoryItemClick = { historyId ->
                                        selectedHistoryId = historyId
                                        currentScreen = "journey_details"
                                    },
                                    onReactivateClick = { currentScreen = "home" }
                                )
                            }                            "journey_details" -> {
                                androidx.activity.compose.BackHandler { currentScreen = "journey_history" }
                                com.janak.location.alarm.ui.settings.JourneyHistoryDetailsScreen(
                                    viewModel = viewModel,
                                    historyId = selectedHistoryId ?: 0,
                                    onBackClick = { currentScreen = "journey_history" }
                                )
                            }
                            "search_history" -> {
                                androidx.activity.compose.BackHandler { currentScreen = "settings" }
                                com.janak.location.alarm.ui.settings.SearchHistoryScreen(
                                    viewModel = viewModel,
                                    onBackClick = { currentScreen = "settings" },
                                    onItemClick = { feature ->
                                        viewModel.selectSearchResult(feature)
                                        currentScreen = "map"
                                    }
                                )
                            }
                        }

                        // Edit Route Sheet
                        routeToEdit?.let { route ->
                            EditRouteSheet(
                                route = route,
                                onDismissRequest = { routeToEdit = null },
                                onSaveRoute = { updatedRoute ->
                                    viewModel.updateRoute(updatedRoute)
                                    routeToEdit = null
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
