package com.janak.location.alarm.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.janak.location.alarm.data.entity.SavedRouteEntity
import com.janak.location.alarm.viewmodel.MapViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

import com.janak.location.alarm.ui.components.SavedRouteCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SavedRoutesScreen(
    viewModel: MapViewModel,
    onBackClick: () -> Unit,
    onEditRouteClick: (SavedRouteEntity) -> Unit,
    onRouteClick: () -> Unit
) {
    val savedRoutes by viewModel.savedRoutes.collectAsState(initial = emptyList())
    val selectedRoutes = remember { mutableStateMapOf<Long, SavedRouteEntity>() }
    var isSelectionMode by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isSelectionMode) "${selectedRoutes.size} selected" else "Saved Routes") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectionMode) {
                            isSelectionMode = false
                            selectedRoutes.clear()
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            viewModel.deleteRoutes(selectedRoutes.values.toList())
                            isSelectionMode = false
                            selectedRoutes.clear()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (savedRoutes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No saved routes yet.", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(savedRoutes) { route ->
                    val isSelected = selectedRoutes.containsKey(route.routeId)
                    
                    SavedRouteCard(
                        route = route,
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        onEditClick = { onEditRouteClick(route) },
                        onDeleteClick = { viewModel.deleteRoute(route) },
                        onStartClick = {
                            viewModel.startJourneyFromSavedRoute(route)
                            onRouteClick()
                        },
                        onLongClick = {
                            isSelectionMode = true
                            if (isSelected) selectedRoutes.remove(route.routeId) else selectedRoutes[route.routeId] = route
                            if (selectedRoutes.isEmpty()) isSelectionMode = false
                        },
                        onClick = {
                            if (isSelectionMode) {
                                if (isSelected) selectedRoutes.remove(route.routeId) else selectedRoutes[route.routeId] = route
                                if (selectedRoutes.isEmpty()) isSelectionMode = false
                            } else {
                                viewModel.startJourneyFromSavedRoute(route)
                                onRouteClick()
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun formatDistance(meters: Int): String {
    return if (meters >= 1000) String.format("%.1f km", meters / 1000f) else "${meters}m"
}
