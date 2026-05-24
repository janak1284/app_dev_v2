package com.janak.location.alarm.ui.alarm

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ScrollState
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
import com.janak.location.alarm.model.TransportMode
import com.janak.location.alarm.ui.components.DistanceSection
import com.janak.location.alarm.ui.components.PredictiveSection
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernConfigurationSheet(
    initialSettings: AlarmSettings = AlarmSettings(),
    onDismissRequest: () -> Unit,
    onSaveSettings: (AlarmSettings) -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    scrollState: ScrollState = rememberScrollState()
) {
    val context = LocalContext.current
    
    // Form States
    var distanceEnabled by remember { mutableStateOf(initialSettings.isDistanceAlarmEnabled) }
    var distanceMeters by remember { mutableStateOf(initialSettings.distanceMeters.toFloat()) }
    
    var predictiveEnabled by remember { mutableStateOf(initialSettings.isPredictiveAlarmEnabled) }
    var predictiveMinutes by remember { mutableStateOf(initialSettings.predictiveMinutes.toFloat()) }

    var transportMode by remember { mutableStateOf(initialSettings.transportMode) }
    var vibrateEnabled by remember { mutableStateOf(initialSettings.isVibrateEnabled) }

    // Ringtone States
    var selectedRingtoneUri by remember { mutableStateOf<Uri?>(initialSettings.ringtoneUri) }
    var ringtoneName by remember { mutableStateOf("Default sound") }

    val ringtonePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
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

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        tonalElevation = 0.dp,
        windowInsets = WindowInsets(0, 0, 0, 0)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp)
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    text = "Alarm Configuration",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                TransportModeSection(
                    selectedMode = transportMode,
                    onModeSelected = { transportMode = it }
                )

                Spacer(modifier = Modifier.height(24.dp))

                DistanceSection(
                    enabled = distanceEnabled,
                    onToggle = { 
                        distanceEnabled = it
                        if (it) predictiveEnabled = false 
                    },
                    distanceMeters = distanceMeters,
                    onDistanceChange = { distanceMeters = it }
                )

                Spacer(modifier = Modifier.height(20.dp))

                PredictiveSection(
                    enabled = predictiveEnabled,
                    onToggle = { 
                        predictiveEnabled = it
                        if (it) distanceEnabled = false
                    },
                    minutes = predictiveMinutes,
                    onMinutesChange = { predictiveMinutes = it }
                )

                Spacer(modifier = Modifier.height(20.dp))

                SoundVibrationSection(
                    ringtoneName = ringtoneName,
                    vibrateEnabled = vibrateEnabled,
                    onRingtoneClick = launchRingtonePicker,
                    onVibrateToggle = { vibrateEnabled = !vibrateEnabled }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            PrimaryActionButton(
                onClick = {
                    onSaveSettings(
                        AlarmSettings(
                            distanceMeters = distanceMeters.toInt(),
                            isDistanceAlarmEnabled = distanceEnabled,
                            predictiveMinutes = predictiveMinutes.toInt(),
                            isPredictiveAlarmEnabled = predictiveEnabled,
                            isVibrateEnabled = vibrateEnabled,
                            transportMode = transportMode,
                            ringtoneUri = selectedRingtoneUri
                        )
                    )
                }
            )
        }
    }
}

@Composable
fun TransportModeSection(
    selectedMode: TransportMode,
    onModeSelected: (TransportMode) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Transport Mode",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TransportModeButton(
                mode = TransportMode.ROAD,
                label = "Road",
                icon = Icons.Default.DirectionsCar,
                selected = selectedMode == TransportMode.ROAD,
                onClick = { onModeSelected(TransportMode.ROAD) },
                modifier = Modifier.weight(1f)
            )
            TransportModeButton(
                mode = TransportMode.TRAIN,
                label = "Transit",
                icon = Icons.Default.DirectionsTransit,
                selected = selectedMode != TransportMode.ROAD,
                onClick = { onModeSelected(TransportMode.TRAIN) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun TransportModeButton(
    mode: TransportMode,
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        label = "bgColor"
    )
    val contentColor by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "contentColor"
    )

    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        contentColor = contentColor
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SoundVibrationSection(
    ringtoneName: String,
    vibrateEnabled: Boolean,
    onRingtoneClick: () -> Unit,
    onVibrateToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        QuickActionPill(
            title = "Alarm Sound",
            value = ringtoneName,
            icon = Icons.Default.MusicNote,
            modifier = Modifier.weight(1f),
            onClick = onRingtoneClick,
            active = true
        )
        QuickActionPill(
            title = "Vibration",
            value = if (vibrateEnabled) "On" else "Off",
            icon = if (vibrateEnabled) Icons.Default.Vibration else Icons.Default.DoNotDisturbOn,
            modifier = Modifier.weight(1f),
            onClick = onVibrateToggle,
            active = vibrateEnabled,
            activeColor = MaterialTheme.colorScheme.primaryContainer
        )
    }
}

@Composable
fun PrimaryActionButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp)
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(20.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Icon(Icons.Default.Shield, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Activate Alarm",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
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

