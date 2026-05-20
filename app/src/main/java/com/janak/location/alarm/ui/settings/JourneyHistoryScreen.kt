package com.janak.location.alarm.ui.settings

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.janak.location.alarm.viewmodel.MapViewModel
import com.janak.location.alarm.data.entity.JourneyHistoryEntity
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
                title = { Text(if (isSelectionMode) "${selectedJourneys.size} selected" else "Saved Journeys") },
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
                    .padding(padding)
            ) {
                items(historyEntries) { entry ->
                    val isSelected = selectedJourneys.containsKey(entry.historyId)
                    ListItem(
                        headlineContent = {
                            Text(
                                text = entry.destinationName,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        supportingContent = {
                            Text(
                                text = SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(entry.timestamp))
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Default.History, contentDescription = null, tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        },
                        trailingContent = {
                            if (!isSelectionMode) {
                                IconButton(onClick = {
                                    viewModel.startJourneyFromHistory(entry)
                                    onReactivateClick()
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.PlayCircle,
                                        contentDescription = "Re-activate",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) {
                                        if (isSelected) selectedJourneys.remove(entry.historyId) else selectedJourneys[entry.historyId] = entry
                                        if (selectedJourneys.isEmpty()) isSelectionMode = false
                                    } else {
                                        onHistoryItemClick(entry.historyId)
                                    }
                                },
                                onLongClick = {
                                    isSelectionMode = true
                                    if (isSelected) selectedJourneys.remove(entry.historyId) else selectedJourneys[entry.historyId] = entry
                                    if (selectedJourneys.isEmpty()) isSelectionMode = false
                                }
                            )
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
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
