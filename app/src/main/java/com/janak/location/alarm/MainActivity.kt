package com.janak.location.alarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.janak.location.alarm.alarm.AlarmEngine
import com.janak.location.alarm.alarm.AlarmSchedulerImpl
import com.janak.location.alarm.api.RetrofitClient
import com.janak.location.alarm.location.LocationTrackingManager
import com.janak.location.alarm.viewmodel.MapViewModel
import com.janak.location.alarm.viewmodel.MapViewModelFactory
import com.janak.location.alarm.ui.theme.LocationAlarmTheme
import com.janak.location.alarm.ui.map.MapScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current.applicationContext
            
            // These components are remembered to stay consistent during recompositions,
            // but they use applicationContext to survive Activity recreations via the ViewModel.
            val alarmEngine = remember { AlarmEngine(context) }
            val alarmScheduler = remember { AlarmSchedulerImpl(context) }
            val locationTrackingManager = remember { LocationTrackingManager(context) }
            val photonApiService = remember { RetrofitClient.photonApiService }
            
            val viewModel: MapViewModel = viewModel(
                factory = MapViewModelFactory(
                    locationTrackingManager, 
                    alarmEngine, 
                    alarmScheduler,
                    photonApiService,
                    context
                )
            )

            val themeMode by viewModel.themeMode.collectAsState()
            val darkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }

            LocationAlarmTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MapScreen(viewModel = viewModel)
                }
            }
        }
    }
}
