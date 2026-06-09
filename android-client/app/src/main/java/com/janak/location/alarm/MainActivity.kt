package com.janak.location.alarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
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
            var historyRepository by remember {
                mutableStateOf<com.janak.location.alarm.data.repository.HistoryRepository?>(null)
            }
            var stationRepository by remember {
                mutableStateOf<com.janak.location.alarm.data.repository.StationRepository?>(null)
            }
            var alarmEngine by remember { mutableStateOf<AlarmEngine?>(null) }
            var locationTrackingManager by remember { mutableStateOf<LocationTrackingManager?>(null) }

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    val database = AppDatabase.getDatabase(context)
                    routeRepository = RouteRepository(database)
                    historyRepository = com.janak.location.alarm.data.repository.HistoryRepository(database)
                    stationRepository = com.janak.location.alarm.data.repository.StationRepository(database, context)

                    // Ensure stations are loaded into DB during initial splash
                    stationRepository!!.ensureStationsLoaded()

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
                val database = AppDatabase.getDatabase(context)
                val railwayTrackCacheDao = database.railwayTrackCacheDao()

                val viewModel: MapViewModel = viewModel(
                    factory = MapViewModelFactory(
                        locationTrackingManager!!,
                        alarmEngine!!,
                        photonApiService,
                        osrmApiService,
                        routeRepository!!,
                        historyRepository!!,
                        stationRepository!!,
                        railwayTrackCacheDao,
                        context
                    )
                )

                val navigationStack = remember { mutableStateListOf("home") }
                val currentScreen by remember { derivedStateOf { navigationStack.last() } }

                val navigateTo = remember { { screen: String -> navigationStack.add(screen) } }
                val navigateBack = remember {
                    {
                        if (navigationStack.size > 1) {
                            navigationStack.removeAt(navigationStack.size - 1)
                        }
                    }
                }

                val themeMode by viewModel.themeMode.collectAsState()
                val darkTheme = when (themeMode) {
                    1 -> false
                    2 -> true
                    else -> isSystemInDarkTheme()
                }

                var selectedHistoryId by remember { mutableStateOf<Long?>(null) }
                var routeToEdit by remember { mutableStateOf<SavedRouteEntity?>(null) }

                LocationAlarmTheme(darkTheme = darkTheme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        val visitedScreens = remember { mutableStateListOf("home", "map") }
                        LaunchedEffect(currentScreen) {
                            if (!visitedScreens.contains(currentScreen)) {
                                visitedScreens.add(currentScreen)
                            }
                        }

                        Box(modifier = Modifier.fillMaxSize()) {
                            // Render all visited screens with animations
                            for (screen in visitedScreens) {
                                val isVisible = currentScreen == screen

                                // Unified animation state
                                val alpha by androidx.compose.animation.core.animateFloatAsState(
                                    targetValue = if (isVisible) 1f else 0f,
                                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 400),
                                    label = "alpha"
                                )

                                val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
                                val offsetValue by androidx.compose.animation.core.animateIntOffsetAsState(
                                    targetValue = if (isVisible) {
                                        IntOffset.Zero
                                    } else {
                                        IntOffset(x = (screenWidth * context.resources.displayMetrics.density).toInt(), y = 0)
                                    },
                                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 400),
                                    label = "offset"
                                )

                                // Apply animations to a container Box
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .offset { offsetValue }
                                        .alpha(alpha)
                                ) {
                                    // Disable touch for background screens
                                    val interactionModifier = if (isVisible) Modifier.fillMaxSize() else Modifier.size(0.dp)

                                    Box(modifier = interactionModifier) {
                                        when (screen) {
                                            "map" -> {
                                                MapScreen(
                                                    viewModel = viewModel,
                                                    onNavigateHome = { 
                                                        navigationStack.clear()
                                                        navigationStack.add("home") 
                                                    },
                                                    onOpenSettings = { navigateTo("settings") },
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                                if (isVisible) androidx.activity.compose.BackHandler { navigateBack() }
                                            }
                                            "home" -> {
                                                HomeScreen(
                                                    viewModel = viewModel,
                                                    onNewJourneyClick = { resetState ->
                                                        if (resetState) viewModel.resetRouteState()
                                                        navigateTo("map")
                                                    },
                                                    onSettingsClick = { navigateTo("settings") },
                                                    onManageJourneysClick = { navigateTo("saved_routes") },
                                                    onManageSearchesClick = { navigateTo("search_history") },
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                            "settings" -> {
                                                if (isVisible) androidx.activity.compose.BackHandler { navigateBack() }
                                                com.janak.location.alarm.ui.settings.SettingsScreen(
                                                    viewModel = viewModel,
                                                    onBackClick = { navigateBack() },
                                                    onNavigateToSearchHistory = { navigateTo("search_history") },
                                                    onNavigateToJourneyHistory = { navigateTo("journey_history") },
                                                    onNavigateToSavedRoutes = { navigateTo("saved_routes") },
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                            "saved_routes" -> {
                                                 if (isVisible) androidx.activity.compose.BackHandler { navigateBack() }
                                                 com.janak.location.alarm.ui.settings.SavedRoutesScreen(
                                                     viewModel = viewModel,
                                                     onBackClick = { navigateBack() },
                                                     onEditRouteClick = { route -> routeToEdit = route },
                                                     onRouteClick = { route ->
                                                         viewModel.startJourneyFromSavedRoute(route)
                                                         navigateTo("map")
                                                     },
                                                     modifier = Modifier.fillMaxSize()
                                                 )
                                             }
                                            "journey_history" -> {
                                                if (isVisible) androidx.activity.compose.BackHandler { navigateBack() }
                                                com.janak.location.alarm.ui.settings.JourneyHistoryScreen(
                                                    viewModel = viewModel,
                                                    onBackClick = { navigateBack() },
                                                    onHistoryItemClick = { history ->
                                                        viewModel.startJourneyFromHistory(history)
                                                        navigateTo("map")
                                                    },
                                                    onReactivateClick = { navigateTo("map") },
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                            "journey_details" -> {
                                                if (isVisible) androidx.activity.compose.BackHandler { navigateBack() }
                                                com.janak.location.alarm.ui.settings.JourneyHistoryDetailsScreen(
                                                    viewModel = viewModel,
                                                    historyId = selectedHistoryId ?: 0,
                                                    onBackClick = { navigateBack() },
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                            "search_history" -> {
                                                if (isVisible) androidx.activity.compose.BackHandler { navigateBack() }
                                                com.janak.location.alarm.ui.settings.SearchHistoryScreen(
                                                    viewModel = viewModel,
                                                    onBackClick = { navigateBack() },
                                                    onItemClick = { feature ->
                                                        viewModel.selectSearchResult(feature)
                                                        navigateTo("map")
                                                    },
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Edit Route Sheet - Global overlay
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
