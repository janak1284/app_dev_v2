package com.janak.location.alarm.ui.alarm

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.janak.location.alarm.model.AlarmSettings
import com.janak.location.alarm.ui.components.InfiniteWheelPicker
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernConfigurationSheet(
    initialSettings: AlarmSettings = AlarmSettings(),
    onDismissRequest: () -> Unit,
    onSaveSettings: (AlarmSettings) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    // Form States
    var distanceMeters by remember { mutableStateOf(initialSettings.distanceMeters.toFloat()) }
    var backupHour by remember { mutableStateOf(initialSettings.backupHour) }
    var backupMinute by remember { mutableStateOf(initialSettings.backupMinute) }
    var vibrateEnabled by remember { mutableStateOf(initialSettings.isVibrateEnabled) }
    var timerEnabled by remember { mutableStateOf(initialSettings.backupHour != 0 || initialSettings.backupMinute != 0) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Ringtone States
    var selectedRingtoneUri by remember { mutableStateOf<Uri?>(initialSettings.ringtoneUri) }
    var ringtoneName by remember { mutableStateOf("Default sound") }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            selectedRingtoneUri = uri
            if (uri != null) {
                val ringtone = RingtoneManager.getRingtone(context, uri)
                ringtoneName = ringtone.getTitle(context) ?: "Custom sound"
            } else {
                ringtoneName = "Silent"
            }
        }
    }

    val launchRingtonePicker = {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            selectedRingtoneUri?.let { currentUri ->
                putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri)
            }
        }
        ringtonePickerLauncher.launch(intent)
    }

    LaunchedEffect(showTimePicker) {
        if (showTimePicker) {
            scope.launch {
                delay(200) // Wait for animation
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Guard Configuration",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // --- Section: Distance ---
            AnimatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text(
                                text = "Wake-up Distance",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatDistance(distanceMeters.toInt()),
                                style = MaterialTheme.typography.displaySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Icon(
                            Icons.Default.MyLocation, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Slider(
                        value = distanceMeters,
                        onValueChange = { distanceMeters = it },
                        valueRange = 100f..5000f,
                        steps = 48,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --- Section: Sound & Vibration ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                QuickActionPill(
                    title = "Alarm Sound",
                    value = ringtoneName,
                    icon = Icons.Default.MusicNote,
                    modifier = Modifier.weight(1f),
                    onClick = launchRingtonePicker,
                    active = true
                )
                QuickActionPill(
                    title = "Vibration",
                    value = if (vibrateEnabled) "On" else "Off",
                    icon = if (vibrateEnabled) Icons.Default.Vibration else Icons.Default.DoNotDisturbOn,
                    modifier = Modifier.weight(1f),
                    onClick = { vibrateEnabled = !vibrateEnabled },
                    active = vibrateEnabled,
                    activeColor = MaterialTheme.colorScheme.primaryContainer
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // --- Section: Backup Timer ---
            AnimatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = if (timerEnabled) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Timer, 
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = if (timerEnabled) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Backup Timer",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (timerEnabled) "Active at ${formatTime(backupHour, backupMinute)}" else "Disabled",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = timerEnabled,
                            onCheckedChange = { timerEnabled = it }
                        )
                    }

                    AnimatedVisibility(
                        visible = timerEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showTimePicker = !showTimePicker },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(if (showTimePicker) "Hide Time Picker" else "Set Custom Time")
                            }
                            
                            AnimatedVisibility(
                                visible = showTimePicker,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 16.dp)
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                            RoundedCornerShape(16.dp)
                                        )
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        InfiniteWheelPicker(
                                            items = (0..23).map { it.toString().padStart(2, '0') },
                                            initialIndex = backupHour,
                                            onItemSelected = { _, item -> backupHour = item.toInt() },
                                            modifier = Modifier.width(70.dp),
                                            itemHeight = 44.dp
                                        )
                                        Text(":", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(horizontal = 8.dp))
                                        InfiniteWheelPicker(
                                            items = (0..59).map { it.toString().padStart(2, '0') },
                                            initialIndex = backupMinute,
                                            onItemSelected = { _, item -> backupMinute = item.toInt() },
                                            modifier = Modifier.width(70.dp),
                                            itemHeight = 44.dp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // --- Primary Action ---
            Button(
                onClick = {
                    onSaveSettings(
                        AlarmSettings(
                            distanceMeters = distanceMeters.toInt(),
                            backupHour = if (timerEnabled) backupHour else 0,
                            backupMinute = if (timerEnabled) backupMinute else 0,
                            isVibrateEnabled = vibrateEnabled,
                            ringtoneUri = selectedRingtoneUri
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(20.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Icon(Icons.Default.Shield, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "ACTIVATE GUARD",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
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

@Composable
fun QuickActionPill(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    active: Boolean,
    activeColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
) {
    val scale by animateFloatAsState(if (active) 1f else 0.98f, label = "scale")
    
    Surface(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick() },
        color = if (active) activeColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}

private fun formatDistance(meters: Int): String {
    return if (meters >= 1000) {
        String.format("%.1fkm", meters / 1000f)
    } else {
        "${meters}m"
    }
}

private fun formatTime(hour: Int, minute: Int): String {
    return String.format("%02d:%02d", hour, minute)
}

private suspend fun delay(timeMillis: Long) {
    kotlinx.coroutines.delay(timeMillis)
}
