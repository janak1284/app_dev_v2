package com.janak.location.alarm.ui.components

import android.location.Location
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.janak.location.alarm.model.PhotonFeature
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager

enum class SortOrder {
    NEAREST, FURTHEST
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DestinationSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<PhotonFeature>,
    history: List<PhotonFeature>,
    onResultClick: (PhotonFeature) -> Unit,
    isSearching: Boolean,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    userLocation: Location? = null
) {
    val focusManager = LocalFocusManager.current
    var sortOrder by remember { mutableStateOf(SortOrder.NEAREST) }
    var isFocused by remember { mutableStateOf(false) }

    val displayResults = remember(query, results, history, userLocation, sortOrder) {
        val baseResults = if (query.isEmpty()) history else results
        if (userLocation != null) {
            val withDistance = baseResults.map { feature ->
                val featureLoc = Location("").apply {
                    latitude = feature.geometry.coordinates[1]
                    longitude = feature.geometry.coordinates[0]
                }
                val distance = userLocation.distanceTo(featureLoc)
                feature to distance
            }
            if (sortOrder == SortOrder.NEAREST) {
                withDistance.sortedBy { it.second }
            } else {
                withDistance.sortedByDescending { it.second }
            }
        } else {
            baseResults.map { it to null }
        }
    }

    val isShowingHistory = query.isEmpty() && history.isNotEmpty()
    val showSortButton = query.isNotEmpty() && results.size > 1 && userLocation != null

    Column(modifier = modifier) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(28.dp)
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused },
                placeholder = { Text("Search destination...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSearching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        AnimatedVisibility(visible = showSortButton) {
                            TextButton(
                                onClick = {
                                    sortOrder = if (sortOrder == SortOrder.NEAREST) SortOrder.FURTHEST else SortOrder.NEAREST
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (sortOrder == SortOrder.NEAREST) "Nearest" else "Furthest",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                        IconButton(onClick = onMenuClick) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Settings")
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                singleLine = true
            )
        }

        AnimatedVisibility(
            visible = isFocused && displayResults.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .heightIn(max = 300.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp)
            ) {
                LazyColumn {
                    if (isShowingHistory) {
                        item {
                            Text(
                                text = "Recent Searches",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                    items(displayResults) { (feature, distance) ->
                        val isFromHistory = history.any { it.geometry.coordinates == feature.geometry.coordinates }
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = feature.properties.name ?: feature.properties.street ?: "Unknown",
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text(
                                    text = feature.properties.displayName,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = if (isFromHistory) Icons.Default.History else Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = if (isFromHistory) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingContent = {
                                if (distance != null) {
                                    Text(
                                        text = formatDistance(distance),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            modifier = Modifier.clickable { 
                                focusManager.clearFocus()
                                onResultClick(feature)
                            }
                        )
                        if (feature != displayResults.last().first) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDistance(meters: Float): String {
    return if (meters >= 1000) {
        String.format(Locale.getDefault(), "%.1fkm", meters / 1000f)
    } else {
        "${meters.roundToInt()}m"
    }
}
