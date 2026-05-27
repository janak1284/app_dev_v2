package com.janak.location.alarm.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.janak.location.alarm.data.entity.SavedRouteEntity
import com.janak.location.alarm.ui.components.DistanceSection
import com.janak.location.alarm.ui.components.PredictiveSection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRouteSheet(
    route: SavedRouteEntity,
    onDismissRequest: () -> Unit,
    onSaveRoute: (SavedRouteEntity) -> Unit
) {
    var destinationName by remember { mutableStateOf(route.destinationName) }
    
    // Alarm States
    var distanceEnabled by remember { mutableStateOf(route.alarmSettings.isDistanceAlarmEnabled) }
    var distanceMeters by remember { mutableFloatStateOf(route.alarmSettings.distanceMeters.toFloat()) }
    
    var predictiveEnabled by remember { mutableStateOf(route.alarmSettings.isPredictiveAlarmEnabled) }
    var predictiveMinutes by remember { mutableFloatStateOf(route.alarmSettings.predictiveMinutes.toFloat()) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Edit Saved Route",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = destinationName,
                onValueChange = { destinationName = it },
                label = { Text("Route Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            DistanceSection(
                enabled = distanceEnabled,
                onToggle = { distanceEnabled = it },
                distanceMeters = distanceMeters,
                onDistanceChange = { distanceMeters = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            PredictiveSection(
                enabled = predictiveEnabled,
                onToggle = { predictiveEnabled = it },
                minutes = predictiveMinutes,
                onMinutesChange = { predictiveMinutes = it }
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        if (destinationName.isNotBlank()) {
                            val updatedRoute = route.copy(
                                destinationName = destinationName,
                                alarmSettings = route.alarmSettings.copy(
                                    distanceMeters = distanceMeters.toInt(),
                                    isDistanceAlarmEnabled = distanceEnabled,
                                    predictiveMinutes = predictiveMinutes.toInt(),
                                    isPredictiveAlarmEnabled = predictiveEnabled
                                )
                            )
                            onSaveRoute(updatedRoute)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = destinationName.isNotBlank()
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save")
                }
            }
        }
    }
}
