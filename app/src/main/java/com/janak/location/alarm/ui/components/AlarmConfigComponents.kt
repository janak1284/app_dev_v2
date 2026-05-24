package com.janak.location.alarm.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DistanceSection(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    distanceMeters: Float,
    onDistanceChange: (Float) -> Unit
) {
    var isKm by remember { mutableStateOf(distanceMeters >= 1000) }
    var textValue by remember(distanceMeters) { 
        mutableStateOf(if (isKm) (distanceMeters / 1000f).toString() else distanceMeters.toInt().toString()) 
    }

    AnimatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Distance Alarm",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (enabled) {
                            Text(
                                text = "Wake at ${formatDistance(distanceMeters.toInt())}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            
            AnimatedVisibility(visible = enabled) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = textValue,
                            onValueChange = { newValue ->
                                if (newValue.all { it.isDigit() || it == '.' }) {
                                    textValue = newValue
                                    val numericValue = newValue.toFloatOrNull() ?: 0f
                                    onDistanceChange(if (isKm) numericValue * 1000f else numericValue)
                                }
                            },
                            label = { Text("Distance") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                        
                        SingleChoiceSegmentedButtonRow(modifier = Modifier.width(120.dp)) {
                            SegmentedButton(
                                selected = !isKm,
                                onClick = { 
                                    isKm = false 
                                    onDistanceChange(textValue.toFloatOrNull() ?: 0f)
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) {
                                Text("m")
                            }
                            SegmentedButton(
                                selected = isKm,
                                onClick = { 
                                    isKm = true 
                                    onDistanceChange((textValue.toFloatOrNull() ?: 0f) * 1000f)
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) {
                                Text("km")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PredictiveSection(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    minutes: Float,
    onMinutesChange: (Float) -> Unit
) {
    var textValue by remember(minutes) { mutableStateOf(minutes.toInt().toString()) }

    AnimatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AutoMode,
                        contentDescription = null,
                        tint = if (enabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Smart ETA Alarm",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (enabled) {
                            Text(
                                text = "Wake ${minutes.toInt()} mins before arrival",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
            
            AnimatedVisibility(visible = enabled) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = textValue,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() }) {
                                textValue = newValue
                                val numericValue = newValue.toFloatOrNull() ?: 0f
                                onMinutesChange(numericValue)
                            }
                        },
                        label = { Text("Minutes before arrival") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        suffix = { Text("min") }
                    )
                }
            }
        }
    }
}

@Composable
fun AnimatedCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        content()
    }
}

fun formatDistance(meters: Int): String {
    return if (meters >= 1000) {
        String.format("%.1fkm", meters / 1000f)
    } else {
        "${meters}m"
    }
}
