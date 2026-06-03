package com.janak.location.alarm.ui.components

import androidx.compose.foundation.layout.*
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

    AlertDialog(
        onDismissRequest = onDismiss,
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
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = {
                        isSearching = true
                        viewModel.fetchTelemetryForDropdown(trainNumber)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(if (isSearching && availableStations.isEmpty()) "Searching..." else "Find Stations")
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
                            availableStations.forEach { station ->
                                DropdownMenuItem(
                                    text = { Text("${station.stationName ?: station.stationCode} (${station.stationCode})") },
                                    onClick = {
                                        selectedDestination = station.stationName ?: station.stationCode
                                        selectedStationCode = station.stationCode
                                        selectedLat = station.latitude ?: 0.0
                                        selectedLon = station.longitude ?: 0.0
                                        expanded = false
                                    }
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
