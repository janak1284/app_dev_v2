package com.janak.location.alarm.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.janak.location.alarm.viewmodel.MapViewModel
import com.janak.location.alarm.data.entity.JourneyHistoryEntity
import com.janak.location.alarm.model.TransportMode
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun JourneyHistoryScreen(
    viewModel: MapViewModel,
    onBackClick: () -> Unit,
    onHistoryItemClick: (Long) -> Unit,
    onReactivateClick: () -> Unit
) {
    val historyEntries by viewModel.journeyHistory.collectAsState(initial = emptyList())
    val selectedJourneys = remember { mutableStateMapOf<Long, JourneyHistoryEntity>() }
    var isSelectionMode by remember { mutableStateOf(false) }
    var showClearAllDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isSelectionMode) "${selectedJourneys.size} selected" else "Journey History") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isSelectionMode) {
                            isSelectionMode = false
                            selectedJourneys.clear()
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
                            viewModel.deleteJourneys(selectedJourneys.values.toList())
                            isSelectionMode = false
                            selectedJourneys.clear()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                        }
                    } else if (historyEntries.isNotEmpty()) {
                        IconButton(onClick = { showClearAllDialog = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear All")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (historyEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("No journey logs yet.", color = MaterialTheme.colorScheme.outline)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(historyEntries) { entry ->
                    val isSelected = selectedJourneys.containsKey(entry.historyId)
                    
                    HistoryCard(
                        entry = entry,
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        onReactivateClick = {
                            viewModel.startJourneyFromHistory(entry)
                            onReactivateClick()
                        },
                        onLongClick = {
                            isSelectionMode = true
                            if (isSelected) selectedJourneys.remove(entry.historyId) else selectedJourneys[entry.historyId] = entry
                            if (selectedJourneys.isEmpty()) isSelectionMode = false
                        },
                        onClick = {
                            if (isSelectionMode) {
                                if (isSelected) selectedJourneys.remove(entry.historyId) else selectedJourneys[entry.historyId] = entry
                                if (selectedJourneys.isEmpty()) isSelectionMode = false
                            } else {
                                onHistoryItemClick(entry.historyId)
                            }
                        }
                    )
                }
            }
        }
        
        if (showClearAllDialog) {
            AlertDialog(
                onDismissRequest = { showClearAllDialog = false },
                title = { Text("Clear All Logs") },
                text = { Text("Are you sure you want to clear your entire journey log history?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearJourneyHistory()
                            showClearAllDialog = false
                        }
                    ) {
                        Text("Clear All")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearAllDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryCard(
    entry: JourneyHistoryEntity,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onReactivateClick: () -> Unit,
    onLongClick: () -> Unit,
    onClick: () -> Unit
) {
    val primaryMode = entry.alarmConfigAtTime.transportMode
    val modeIcon = when (primaryMode) {
        TransportMode.ROAD -> Icons.Default.DirectionsCar
        TransportMode.WALK -> Icons.Default.DirectionsWalk
        else -> Icons.Default.DirectionsTransit
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        modeIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.destinationName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetricBadgeSmall(Icons.Default.Schedule, formatDuration(entry.durationMillis))
                    MetricBadgeSmall(Icons.Default.Straighten, formatDistance(entry.actualDistanceMeters.toInt()))
                    Text(
                        text = SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(entry.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            
            if (isSelectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = null)
            } else {
                IconButton(onClick = onReactivateClick) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Re-activate",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MetricBadgeSmall(icon: ImageVector, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

private fun formatDistance(meters: Int): String {
    return if (meters >= 1000) String.format("%.1f km", meters / 1000f) else "${meters}m"
}

private fun formatDuration(millis: Long): String {
    val minutes = millis / 60000
    return if (minutes >= 60) {
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        "${hours}h ${remainingMinutes}m"
    } else {
        "${minutes} min"
    }
}
