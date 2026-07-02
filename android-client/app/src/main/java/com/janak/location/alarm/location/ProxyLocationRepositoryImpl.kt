package com.janak.location.alarm.location

import android.content.Context
import android.location.Location
import com.janak.location.alarm.data.SettingsDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest

class ProxyLocationRepositoryImpl(
    private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val realLocationRepository: LocationTrackingManager
) : LocationRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getLocationUpdates(): Flow<Location> {
        return settingsDataStore.demoSettingsFlow.flatMapLatest { settings ->
            val prefs = context.getSharedPreferences("service_state", Context.MODE_PRIVATE)
            val transportMode = prefs.getString("transport_mode", "ROAD")

            if (settings.isRailwayDemoEnabled && transportMode == "RAILWAY") {
                RailwayMockLocationRepositoryImpl(context).getLocationUpdates()
            } else if (settings.isDemoEnabled && transportMode != "RAILWAY") {
                val fileName = if (settings.selectedRoute == "555S") {
                    "555S_variable_speed_route.gpx"
                } else {
                    "55v_variable_speed_route.gpx"
                }
                GpxMockLocationRepositoryImpl(context, settingsDataStore, fileName).getLocationUpdates()
            } else {
                realLocationRepository.getLocationUpdates()
            }
        }
    }

    override fun updateInterval(intervalMillis: Long, priority: Int) {
        realLocationRepository.updateInterval(intervalMillis, priority)
    }
}
