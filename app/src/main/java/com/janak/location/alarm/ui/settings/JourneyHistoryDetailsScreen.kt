package com.janak.location.alarm.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.janak.location.alarm.viewmodel.MapViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JourneyHistoryDetailsScreen(
    viewModel: MapViewModel,
    historyId: Long,
    onBackClick: () -> Unit
) {
    val breadcrumbs by viewModel.getBreadcrumbsForHistory(historyId).collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Journey Details") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            items(breadcrumbs) { breadcrumb ->
                ListItem(
                    headlineContent = {
                        Text("Lat: ${breadcrumb.latitude}, Lng: ${breadcrumb.longitude}")
                    },
                    supportingContent = {
                        Text(
                            text = "Speed: ${breadcrumb.speed} m/s | ${SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(breadcrumb.timestamp))}"
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}
