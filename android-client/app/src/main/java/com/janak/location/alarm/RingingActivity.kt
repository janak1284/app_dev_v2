package com.janak.location.alarm

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.janak.location.alarm.service.LocationAlarmService

class RingingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Force screen on and bypass lock screen (Crucial for alarms)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguardManager = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
            keyguardManager.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val transferName = intent.getStringExtra("TRANSFER_NAME")
        val isTransfer = intent.getBooleanExtra("IS_TRANSFER", false)
        val isCritical = intent.getBooleanExtra("FORCE_MAX_VOLUME", false)
        val triggerDistance = intent.getDoubleExtra("TRIGGER_DISTANCE", -1.0)
        val triggerEta = intent.getDoubleExtra("TRIGGER_ETA", -1.0)

        setContent {
            // A simple full-screen UI to dismiss the alarm
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = if (isCritical) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isCritical) {
                        Text(
                            "CRITICAL!",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    val mainText = if (isTransfer) "Transfer!" else "Approaching!"
                    val subText = buildString {
                        if (triggerDistance >= 0) {
                            append(formatDistance(triggerDistance.toInt()))
                            append(" away")
                        }
                        if (triggerEta >= 0 && triggerEta < 1000) { // arbitrary sanity check for ETA
                            if (isNotEmpty()) append(" | ")
                            append("${triggerEta.toInt()} min left")
                        }
                    }

                    Text(
                        mainText, 
                        fontSize = 48.sp, 
                        fontWeight = FontWeight.Bold,
                        color = if (isTransfer) MaterialTheme.colorScheme.primary else (if (isCritical) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
                    )

                    if (subText.isNotEmpty()) {
                        Text(
                            text = subText,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    
                    if (isTransfer && !transferName.isNullOrBlank()) {
                        Text(
                            text = "Next: $transferName",
                            fontSize = 24.sp,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                    
                    Button(
                        onClick = { dismissAlarm() },
                        modifier = Modifier.fillMaxWidth(0.8f).height(64.dp)
                    ) {
                        Text(if (isTransfer) "Dismiss (Continue Leg)" else "Dismiss (Keep Tracking)", fontSize = 18.sp)
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedButton(
                        onClick = { endJourney() },
                        modifier = Modifier.fillMaxWidth(0.8f).height(64.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("End Journey", fontSize = 18.sp)
                    }
                }
            }
        }
    }

    private fun formatDistance(meters: Int): String {
        return if (meters >= 1000) String.format(java.util.Locale.getDefault(), "%.1fkm", meters / 1000f) else "${meters}m"
    }

    private fun dismissAlarm() {
        val intent = Intent(this, LocationAlarmService::class.java).apply {
            action = LocationAlarmService.ACTION_STOP_ALARM
        }
        startService(intent)
        stopRingingAndFinish()
    }

    private fun endJourney() {
        val intent = Intent(this, LocationAlarmService::class.java).apply {
            action = LocationAlarmService.ACTION_END_JOURNEY
        }
        startService(intent)
        stopRingingAndFinish()
    }

    private fun stopRingingAndFinish() {
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
