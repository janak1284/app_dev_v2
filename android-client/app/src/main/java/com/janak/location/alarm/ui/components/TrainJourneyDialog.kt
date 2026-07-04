package com.janak.location.alarm.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsTransit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.janak.location.alarm.viewmodel.MapViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainJourneyDialog(
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
    val cloudSuggestions by viewModel.cloudStationSuggestions.collectAsState()

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

    // Reset searching state if error or stations arrive, and match destination from To Station
    LaunchedEffect(availableStations, searchError, destStation) {
        if (availableStations.isNotEmpty() || searchError != null) {
            isSearchingStations = false
        }
        if (availableStations.isNotEmpty() && destStation.isNotBlank()) {
            val destQuery = destStation.trim().lowercase()
            val matched = availableStations.find { 
                it.stationCode?.lowercase() == destQuery || 
                it.stationName?.lowercase() == destQuery ||
                (it.stationName?.lowercase()?.contains(destQuery) == true) ||
                (destQuery.contains(it.stationName?.lowercase() ?: "---"))
            } ?: availableStations.lastOrNull { !it.hasDeparted } ?: availableStations.lastOrNull()
            
            if (matched != null) {
                selectedDestination = matched.stationName ?: matched.stationCode ?: destStation
                selectedStationCode = matched.stationCode ?: ""
                selectedLat = matched.latitude ?: 0.0
                selectedLon = matched.longitude ?: 0.0
            }
        }
    }

    val modernFieldColors = TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent
    )
    val modernShape = RoundedCornerShape(16.dp)

    AlertDialog(
        onDismissRequest = {
            viewModel.clearRailwayData()
            onDismiss()
        },
        shape = RoundedCornerShape(28.dp),
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DirectionsTransit, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "Train Journey",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                ) 
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) {
                Text(
                    "Enter your From and To stations to search for trains. Entering a Train Number or Name is optional if you already know it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                var sourceExpanded by remember { mutableStateOf(false) }
                val filteredSourceStations = remember(sourceStation, cloudSuggestions) {
                    val local = StationDictionary.search(sourceStation)
                    val cloud = cloudSuggestions.filter { c -> local.none { l -> l.code.equals(c.code, ignoreCase = true) } }
                    (local + cloud).take(8)
                }

                ExposedDropdownMenuBox(
                    expanded = sourceExpanded && filteredSourceStations.isNotEmpty(),
                    onExpandedChange = { sourceExpanded = !sourceExpanded }
                ) {
                    TextField(
                        value = sourceStation,
                        onValueChange = { 
                            sourceStation = it
                            sourceExpanded = true
                            viewModel.searchStationsAutocomplete(it)
                        },
                        label = { Text("From Station") },
                        placeholder = { Text("Type city or station name...", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        shape = modernShape,
                        colors = modernFieldColors,
                        leadingIcon = {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    if (filteredSourceStations.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = sourceExpanded,
                            onDismissRequest = { sourceExpanded = false }
                        ) {
                            filteredSourceStations.forEach { st ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("${st.name} (${st.code})", fontWeight = FontWeight.Bold)
                                            Text("City: ${st.city}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    onClick = {
                                        sourceStation = "${st.name} (${st.code})"
                                        sourceExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                var destExpanded by remember { mutableStateOf(false) }
                val filteredDestStations = remember(destStation, cloudSuggestions) {
                    val local = StationDictionary.search(destStation)
                    val cloud = cloudSuggestions.filter { c -> local.none { l -> l.code.equals(c.code, ignoreCase = true) } }
                    (local + cloud).take(8)
                }

                ExposedDropdownMenuBox(
                    expanded = destExpanded && filteredDestStations.isNotEmpty(),
                    onExpandedChange = { destExpanded = !destExpanded }
                ) {
                    TextField(
                        value = destStation,
                        onValueChange = { 
                            destStation = it
                            destExpanded = true
                            viewModel.searchStationsAutocomplete(it)
                        },
                        label = { Text("To Station") },
                        placeholder = { Text("Type city or station name...", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true,
                        shape = modernShape,
                        colors = modernFieldColors,
                        leadingIcon = {
                            Icon(
                                Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    if (filteredDestStations.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = destExpanded,
                            onDismissRequest = { destExpanded = false }
                        ) {
                            filteredDestStations.forEach { st ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("${st.name} (${st.code})", fontWeight = FontWeight.Bold)
                                            Text("City: ${st.city}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    onClick = {
                                        destStation = "${st.name} (${st.code})"
                                        destExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextField(
                    value = trainNumber,
                    onValueChange = { trainNumber = it },
                    label = { Text("Train Number or Name (Optional)") },
                    placeholder = { Text("e.g. 12605 or Pallavan", style = MaterialTheme.typography.bodySmall) },
                    singleLine = true,
                    shape = modernShape,
                    colors = modernFieldColors,
                    leadingIcon = {
                        Icon(
                            Icons.Default.DirectionsTransit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
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

                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        if (trainNumber.isNotBlank()) {
                            isSearchingStations = true
                            viewModel.fetchTelemetryForDropdown(trainNumber.trim())
                        } else if (sourceStation.isNotBlank() && destStation.isNotBlank()) {
                            viewModel.searchTrainsBetweenStations(sourceStation.trim(), destStation.trim())
                        } else {
                            viewModel.clearRailwaySearchError()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    enabled = trainNumber.isNotBlank() || (sourceStation.isNotBlank() && destStation.isNotBlank()),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    val btnText = when {
                        isSearchingTrains -> "Searching Trains..."
                        isSearchingStations -> "Loading Stations..."
                        trainNumber.isNotBlank() -> "Find Train"
                        else -> "Search Trains"
                    }
                    Text(btnText, fontWeight = FontWeight.SemiBold)
                }

                if (trainResults.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(18.dp))
                    Text(
                        "Select Train:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    ExposedDropdownMenuBox(
                        expanded = trainMenuExpanded,
                        onExpandedChange = { trainMenuExpanded = !trainMenuExpanded }
                    ) {
                        TextField(
                            value = selectedTrainText,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = trainMenuExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = modernShape,
                            colors = modernFieldColors
                        )
                        ExposedDropdownMenu(
                            expanded = trainMenuExpanded,
                            onDismissRequest = { trainMenuExpanded = false }
                        ) {
                            val (upcomingTrains, earlierTrains) = remember(trainResults) {
                                val now = java.util.Calendar.getInstance()
                                val nowMinutes = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)
                                val getDep = { dep: String? ->
                                    if (dep.isNullOrBlank()) -1
                                    else {
                                        val p = dep.split(":")
                                        if (p.size == 2) (p[0].toIntOrNull() ?: 0) * 60 + (p[1].toIntOrNull() ?: 0) else -1
                                    }
                                }
                                val upcoming = trainResults.filter { getDep(it.departure) == -1 || getDep(it.departure) >= nowMinutes - 30 }
                                    .sortedBy { val m = getDep(it.departure); if (m == -1) Int.MAX_VALUE else m }
                                val earlier = trainResults.filter { getDep(it.departure) != -1 && getDep(it.departure) < nowMinutes - 30 }
                                    .sortedByDescending { getDep(it.departure) }
                                Pair(upcoming, earlier)
                            }

                            if (upcomingTrains.isNotEmpty()) {
                                Text(
                                    "🟢 UPCOMING & DEPARTING SOON",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                upcomingTrains.forEach { train ->
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
                            if (earlierTrains.isNotEmpty()) {
                                if (upcomingTrains.isNotEmpty()) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                }
                                Text(
                                    "🕒 DEPARTED EARLIER TODAY",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                earlierTrains.forEach { train ->
                                    DropdownMenuItem(
                                        text = { 
                                            Column {
                                                Text("${train.trainNumber} - ${train.trainName}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                if (!train.departure.isNullOrBlank()) {
                                                    Text("Dep: ${train.departure} | Arr: ${train.arrival ?: "N/A"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                }

                if (availableStations.isNotEmpty()) {
                    if (destStation.isNotBlank() && selectedStationCode.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Destination: $selectedDestination ($selectedStationCode)",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Matched from 'To Station'. Ready to track!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            "Select Destination Station:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        ExposedDropdownMenuBox(
                            expanded = expanded,
                            onExpandedChange = { expanded = !expanded }
                        ) {
                            TextField(
                                value = selectedDestination,
                                onValueChange = {},
                                readOnly = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                shape = modernShape,
                                colors = modernFieldColors
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
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("${station.stationName ?: station.stationCode} (${station.stationCode})")
                                                if (index == 0) {
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.primaryContainer,
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = "NEXT",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                            fontWeight = FontWeight.Bold
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
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    onStartTracking(trainNumber, selectedDestination, selectedStationCode, selectedLat, selectedLon) 
                    onDismiss()
                },
                enabled = selectedStationCode.isNotEmpty(),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.height(44.dp)
            ) {
                Text("Start Tracking", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
