package com.janak.location.alarm.ui.alarm

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.janak.location.alarm.model.AlarmSettings
import com.janak.location.alarm.ui.components.InfiniteWheelPicker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegratedAlarmBottomSheet(
    initialSettings: AlarmSettings = AlarmSettings(),
    onDismissRequest: () -> Unit,
    onSaveSettings: (AlarmSettings) -> Unit
) {
    val context = LocalContext.current
    val darkGray = Color(0xFF2C2C2C)
    val lightPurple = Color(0xFFD0BCFF)
    val lightGray = Color(0xFFAAAAAA)

    // Form States
    var distanceMeters by remember { mutableStateOf(initialSettings.distanceMeters) }
    var backupHour by remember { mutableStateOf(initialSettings.backupHour) }
    var backupMinute by remember { mutableStateOf(initialSettings.backupMinute) }
    var vibrateEnabled by remember { mutableStateOf(initialSettings.isVibrateEnabled) }
    var alarmName by remember { mutableStateOf(initialSettings.alarmName) }

    // Ringtone States
    var selectedRingtoneUri by remember { mutableStateOf<Uri?>(initialSettings.ringtoneUri) }
    var ringtoneName by remember { mutableStateOf("Default alarm sound") }

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

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = Color.Black,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "Destination Selected",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Distance Wheel
            Text(
                text = "Alarm Distance",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = lightGray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp), // Forces width to match the Settings card
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val distanceItems = (100..5000 step 100).map { formatDistance(it) }
                    val currentDistanceIndex = distanceItems.indexOf(formatDistance(distanceMeters)).takeIf { it >= 0 } ?: 4 // Default 500m
                    
                    InfiniteWheelPicker(
                        items = distanceItems, // Use your actual list variable here
                        initialIndex = currentDistanceIndex,
                        itemHeight = 48.dp,
                        modifier = Modifier.width(120.dp), // Constrain wheel width so it centers
                        onItemSelected = { _, item ->
                            distanceMeters = if (item.endsWith("km")) {
                                (item.removeSuffix("km").toFloat() * 1000).toInt()
                            } else {
                                item.removeSuffix("m").toInt()
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Time Wheel (Backup Time)
            Text(
                text = "Backup Time",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                color = lightGray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp), // Forces width to match the Settings card
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center, // Perfectly centers the wheels
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Hours Picker
                    val hourItems = (0..23).map { it.toString().padStart(2, '0') }
                    InfiniteWheelPicker(
                        items = hourItems, // Use your actual list variable here
                        initialIndex = backupHour,
                        itemHeight = 48.dp,
                        modifier = Modifier.width(80.dp), // Fixed width for perfect balance
                        onItemSelected = { _, item -> backupHour = item.toInt() }
                    )
                    
                    Text(
                        text = ":",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    // Minutes Picker
                    val minuteItems = (0..59).map { it.toString().padStart(2, '0') }
                    InfiniteWheelPicker(
                        items = minuteItems, // Use your actual list variable here
                        initialIndex = backupMinute,
                        itemHeight = 48.dp,
                        modifier = Modifier.width(80.dp), // Fixed width for perfect balance
                        onItemSelected = { _, item -> backupMinute = item.toInt() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Settings Card
            Card(
                colors = CardDefaults.cardColors(containerColor = darkGray),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Item 1: Ringtone
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { launchRingtonePicker() }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Ringtone", color = Color.White, fontSize = 16.sp)
                            Text(ringtoneName, color = lightGray, fontSize = 14.sp)
                        }
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Select", tint = lightGray)
                    }
                    HorizontalDivider(color = lightGray.copy(alpha = 0.2f))

                    // Item 3: Vibrate
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Vibrate", color = Color.White, fontSize = 16.sp)
                        Switch(
                            checked = vibrateEnabled,
                            onCheckedChange = { vibrateEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = lightPurple,
                                checkedTrackColor = lightPurple.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Configure Alarm Button
            Button(
                onClick = {
                    onSaveSettings(
                        AlarmSettings(
                            distanceMeters = distanceMeters,
                            backupHour = backupHour,
                            backupMinute = backupMinute,
                            isVibrateEnabled = vibrateEnabled,
                            ringtoneUri = selectedRingtoneUri,
                            alarmName = "" // Field removed from UI
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = lightPurple, contentColor = darkGray),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("Configure Alarm", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

fun formatDistance(meters: Int): String {
    return if (meters >= 1000) {
        String.format("%.1fkm", meters / 1000f)
    } else {
        "${meters}m"
    }
}
