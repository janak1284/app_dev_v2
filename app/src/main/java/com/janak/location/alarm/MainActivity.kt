package com.janak.location.alarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
            LocationAlarmTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
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
                            photonApiService
                        )
                    )

                    MapScreen(viewModel = viewModel)
                }
            }
        }
    }
}
