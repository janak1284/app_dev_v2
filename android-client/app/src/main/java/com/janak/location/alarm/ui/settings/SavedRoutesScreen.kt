package com.janak.location.alarm.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.janak.location.alarm.data.entity.SavedRouteEntity
import com.janak.location.alarm.ui.components.SavedRouteCard
import com.janak.location.alarm.util.AppLogger
import com.janak.location.alarm.viewmodel.MapViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SavedRoutesScreen(
    viewModel: MapViewModel,
    onBackClick: () -> Unit,
    onEditRouteClick: (SavedRouteEntity) -> Unit,
    onRouteClick: (SavedRouteEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val savedRoutes by viewModel.savedRoutes.collectAsState(initial = emptyList())
    val selectedRoutes = remember { mutableStateMapOf<Long, SavedRouteEntity>() }
    var isSelectionMode by remember { mutableStateOf(false) }

    val routeToDelete by viewModel.routeToDelete.collectAsState()
    val deleteMultiple by viewModel.deleteMultipleRoutes.collectAsState()

    if (routeToDelete != null || deleteMultiple) {
        AlertDialog(
            onDismissRequest = {
                viewModel.setRouteToDelete(null)
                viewModel.setDeleteMultipleRoutes(false)
            },
            title = { Text("Delete Route(s)?") },
            text = { Text("Are you sure you want to delete the selected route(s)? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    if (deleteMultiple) {
                        viewModel.deleteRoutes(selectedRoutes.values.toList())
                        isSelectionMode = false
                        selectedRoutes.clear()
                    } else {
                        routeToDelete?.let { viewModel.deleteRoute(it) }
                    }
                    viewModel.setRouteToDelete(null)
                    viewModel.setDeleteMultipleRoutes(false)
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.setRouteToDelete(null)
                    viewModel.setDeleteMultipleRoutes(false)
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
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
                            viewModel.setDeleteMultipleRoutes(true)
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
                        onDeleteClick = { 
                            viewModel.setRouteToDelete(route)
                        },
                        onStartClick = {
                            onRouteClick(route)
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
                                onRouteClick(route)
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun formatDistance(meters: Int): String {
    return if (meters >= 1000) String.format(Locale.getDefault(), "%.1f km", meters / 1000f) else "${meters}m"
}
