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
    var sourceStation by remember { mutableStateOf("") }
    var destStation by remember { mutableStateOf("") }
    var trainNumber by remember { mutableStateOf("") }
    
    var isSearchingStations by remember { mutableStateOf(false) }
    var trainMenuExpanded by remember { mutableStateOf(false) }
    var selectedTrainText by remember { mutableStateOf("Select from available trains") }
    
    var expanded by remember { mutableStateOf(false) }
    var selectedDestination by remember { mutableStateOf("Select Destination Station") }
    var selectedStationCode by remember { mutableStateOf("") }
    var selectedLat by remember { mutableStateOf(0.0) }
    var selectedLon by remember { mutableStateOf(0.0) }
    
    val availableStations by viewModel.stationSequence.collectAsState() 
    val searchError by viewModel.railwaySearchError.collectAsState()
    val trainResults by viewModel.trainSearchResults.collectAsState()
    val isSearchingTrains by viewModel.isSearchingTrains.collectAsState()

    // Reset local state and ViewModel data when the dialog is FIRST opened
    LaunchedEffect(Unit) {
        viewModel.clearRailwayData()
        sourceStation = ""
        destStation = ""
        trainNumber = ""
        isSearchingStations = false
        trainMenuExpanded = false
        selectedTrainText = "Select from available trains"
        selectedDestination = "Select Destination Station"
        selectedStationCode = ""
    }

    // Reset searching state if error or stations arrive
    LaunchedEffect(availableStations, searchError) {
        if (availableStations.isNotEmpty() || searchError != null) {
            isSearchingStations = false
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
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(
                    "Search trains by stations or directly enter a Train Number.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = sourceStation,
                        onValueChange = { sourceStation = it },
                        label = { Text("Source (e.g. NDLS)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = destStation,
                        onValueChange = { destStation = it },
                        label = { Text("Dest (e.g. MMCT)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = trainNumber,
                    onValueChange = { trainNumber = it },
                    label = { Text("Train Number (Optional if searching)") },
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
                        if (trainNumber.isNotBlank()) {
                            isSearchingStations = true
                            viewModel.fetchTelemetryForDropdown(trainNumber.trim())
                        } else if (sourceStation.isNotBlank() && destStation.isNotBlank()) {
                            viewModel.searchTrainsBetweenStations(sourceStation.trim(), destStation.trim())
                        } else {
                            viewModel.clearRailwaySearchError()
                            // Trigger manual error warning
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = trainNumber.isNotBlank() || (sourceStation.isNotBlank() && destStation.isNotBlank()),
                    shape = MaterialTheme.shapes.medium
                ) {
                    val btnText = when {
                        isSearchingTrains -> "Scraping Trains List..."
                        isSearchingStations -> "Fetching Stations..."
                        trainNumber.isNotBlank() -> "Find Stations directly"
                        else -> "Search Trains Between Stations"
                    }
                    Text(btnText)
                }

                if (trainResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Scraped Trains (Select to load stations):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    ExposedDropdownMenuBox(
                        expanded = trainMenuExpanded,
                        onExpandedChange = { trainMenuExpanded = !trainMenuExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedTrainText,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = trainMenuExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        )
                        ExposedDropdownMenu(
                            expanded = trainMenuExpanded,
                            onDismissRequest = { trainMenuExpanded = false }
                        ) {
                            trainResults.forEach { train ->
                                DropdownMenuItem(
                                    text = { 
                                        Column {
                                            Text("${train.trainNumber} - ${train.trainName}", fontWeight = FontWeight.Bold)
                                            if (!train.departure.isNullOrBlank()) {
                                                Text("Dep: ${train.departure} | Arr: ${train.arrival ?: "N/A"}", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    },
                                    onClick = {
                                        trainNumber = train.trainNumber
                                        selectedTrainText = "${train.trainNumber} (${train.trainName})"
                                        trainMenuExpanded = false
                                        isSearchingStations = true
                                        viewModel.fetchTelemetryForDropdown(train.trainNumber)
                                    }
                                )
                            }
                        }
                    }
                }

                if (availableStations.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
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
