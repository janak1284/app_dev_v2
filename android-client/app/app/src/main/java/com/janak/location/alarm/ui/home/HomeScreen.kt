package com.janak.location.alarm.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.janak.location.alarm.data.entity.SavedRouteEntity
import com.janak.location.alarm.model.TransportMode
import com.janak.location.alarm.viewmodel.MapViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import com.janak.location.alarm.ui.components.SavedRouteCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MapViewModel,
    onNewJourneyClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onManageJourneysClick: () -> Unit,
    onManageSearchesClick: () -> Unit
) {
    val savedRoutes by viewModel.savedRoutes.collectAsState(initial = emptyList())
    val searchHistory by viewModel.searchHistory.collectAsState()
    var showModeSelection by remember { mutableStateOf(false) }

    if (showModeSelection) {
        AlertDialog(
            onDismissRequest = { showModeSelection = false },
            title = {
                Text(
                    "Choose Transport Mode",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text("How will you be travelling today?")
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            android.util.Log.d("HomeScreen", "Selecting ROAD mode")
                            viewModel.updateAlarmSettings(viewModel.alarmSettings.value.copy(transportMode = TransportMode.ROAD))
                            showModeSelection = false
                            onNewJourneyClick()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.DirectionsCar, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Roadway (Car/Bike)")
                    }
                    Button(
                        onClick = {
                            android.util.Log.d("HomeScreen", "Selecting TRAIN mode")
                            viewModel.updateAlarmSettings(viewModel.alarmSettings.value.copy(transportMode = TransportMode.TRAIN))
                            showModeSelection = false
                            onNewJourneyClick()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Icon(Icons.Default.DirectionsTransit, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Railway (Train/Subway)")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showModeSelection = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(28.dp)
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showModeSelection = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(20.dp),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Journey", fontWeight = FontWeight.Bold) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            HomeHeader(onSettingsClick = onSettingsClick)

            if (savedRoutes.isEmpty() && searchHistory.isEmpty()) {
                EmptyState(onNewJourneyClick)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Saved Routes Section
                    if (savedRoutes.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Saved Routes",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                TextButton(onClick = onManageJourneysClick) {
                                    Text("Manage")
                                }
                            }
                        }
                        items(
                            items = savedRoutes.take(5),
                            key = { it.routeId }
                        ) { route ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = {
                                    if (it == SwipeToDismissBoxValue.EndToStart) {
                                        viewModel.deleteRoute(route)
                                        true
                                    } else false
                                }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = { DismissBackground(dismissState) },
                                content = {
                                    SavedRouteCard(
                                        route = route,
                                        onClick = {
                                            viewModel.startJourneyFromSavedRoute(route)
                                            onNewJourneyClick()
                                        },
                                        onStartClick = {
                                            viewModel.startJourneyFromSavedRoute(route)
                                            onNewJourneyClick()
                                        }
                                    )
                                }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(8.dp)) }
                    }
                    
                    item {
                        OutlinedButton(
                            onClick = onManageJourneysClick,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("View All Saved Routes")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                    // Recent Searches
                    if (searchHistory.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Recent Searches",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                TextButton(onClick = onManageSearchesClick) {
                                    Text("Manage")
                                }
                            }
                        }
                        items(
                            items = searchHistory.take(3),
                            key = { "${it.geometry.coordinates[0]},${it.geometry.coordinates[1]}" }
                        ) { feature ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = {
                                    if (it == SwipeToDismissBoxValue.EndToStart) {
                                        viewModel.removeFromHistory(feature)
                                        true
                                    } else false
                                }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                backgroundContent = { DismissBackground(dismissState) },
                                content = {
                                    ListItem(
                                        headlineContent = {
                                            Text(
                                                text = feature.properties.name
                                                    ?: "Unknown Location",
                                                fontWeight = FontWeight.Bold
                                            )
                                        },
                                        leadingContent = {
                                            Icon(
                                                Icons.Default.LocationOn,
                                                contentDescription = null
                                            )
                                        },
                                        trailingContent = {
                                            IconButton(onClick = {
                                                viewModel.removeFromHistory(
                                                    feature
                                                )
                                            }) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Delete"
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .clickable {
                                                viewModel.selectSearchResult(feature)
                                                onNewJourneyClick()
                                            }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DismissBackground(dismissState: SwipeToDismissBoxState) {
    val isSwiping = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
    val color = if (isSwiping) MaterialTheme.colorScheme.errorContainer else Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(24.dp))
            .background(color)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        if (isSwiping) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
fun HomeHeader(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        Color.Transparent
                    )
                )
            )
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Commuter",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-1).sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Smart Location Alarms",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
        IconButton(onClick = onSettingsClick) {
            Icon(Icons.Default.Settings, contentDescription = "Settings")
        }
    }
}

@Composable
fun RouteCard(
    route: SavedRouteEntity,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                shape = CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Route,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (route.transportMode == TransportMode.ROAD) Icons.Default.DirectionsCar else Icons.Default.DirectionsTransit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (route.transportMode == TransportMode.ROAD) "Roadway" else "Railway",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = route.destinationName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = SimpleDateFormat("EEEE, MMM d • HH:mm", Locale.getDefault()).format(
                        Date(
                            route.dateSaved
                        )
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                Icons.Default.PlayCircle,
                contentDescription = "Re-activate",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun EmptyState(onAction: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                shape = CircleShape,
                modifier = Modifier.size(120.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "No Journeys Yet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Your saved routes will appear here once you complete a trip.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
