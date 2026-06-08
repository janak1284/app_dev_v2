package com.janak.location.alarm.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.janak.location.alarm.viewmodel.MapViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RailwaySetupDialog(
    viewModel: MapViewModel,
    onDismiss: () -> Unit,
    onStartTracking: (trainNumber: String, destinationName: String, destinationCode: String, lat: Double, lon: Double) -> Unit
) {
    var trainNumber by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var selectedDestination by remember { mutableStateOf("Select Destination Station") }
    var selectedStationCode by remember { mutableStateOf("") }
    var selectedLat by remember { mutableStateOf(0.0) }
    var selectedLon by remember { mutableStateOf(0.0) }
    
    val availableStations by viewModel.stationSequence.collectAsState() 
    val searchError by viewModel.railwaySearchError.collectAsState()

    // Reset local state and ViewModel data when the dialog is FIRST opened
    LaunchedEffect(Unit) {
        viewModel.clearRailwayData()
        trainNumber = ""
        isSearching = false
        selectedDestination = "Select Destination Station"
        selectedStationCode = ""
    }

    // Reset searching state if error or stations arrive
    LaunchedEffect(availableStations, searchError) {
        if (availableStations.isNotEmpty() || searchError != null) {
            isSearching = false
        }
    }

    AlertDialog(
        onDismissRequest = {
            viewModel.clearRailwayData()
            onDismiss()
        },
        title = { 
            Text(
                "Railway Setup",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            ) 
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Enter your train number to fetch live stations.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = trainNumber,
                    onValueChange = { trainNumber = it },
                    label = { Text("Train Number (e.g., 12605)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                if (searchError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = searchError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = {
                        isSearching = true
                        viewModel.fetchTelemetryForDropdown(trainNumber)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(if (isSearching && availableStations.isEmpty() && searchError == null) "Searching..." else "Find Stations")
                }

                if (availableStations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "Where are you getting off?",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = selectedDestination,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            val upcomingStations = availableStations.filter { !it.hasDeparted }
                            
                            upcomingStations.forEachIndexed { index, station ->
                                DropdownMenuItem(
                                    text = { 
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                        ) {
                                            Text("${station.stationName ?: station.stationCode} (${station.stationCode})")
                                            if (index == 0) {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "NEXT",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    onClick = {
                                        selectedDestination = station.stationName ?: station.stationCode
                                        selectedStationCode = station.stationCode
                                        selectedLat = station.latitude ?: 0.0
                                        selectedLon = station.longitude ?: 0.0
                                        expanded = false
                                    }
                                )
                            }
                            
                            if (upcomingStations.isEmpty() && availableStations.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No upcoming stations found.") },
                                    onClick = { expanded = false },
                                    enabled = false
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    onStartTracking(trainNumber, selectedDestination, selectedStationCode, selectedLat, selectedLon) 
                    onDismiss()
                },
                enabled = selectedStationCode.isNotEmpty(),
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Start Tracking")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
