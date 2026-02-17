package com.janak.location.alarm.viewmodel

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.janak.location.alarm.location.LocationTrackingManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MapViewModel(
    private val locationTrackingManager: LocationTrackingManager
) : ViewModel() {

    private val _userLocation = MutableStateFlow<Location?>(null)
    val userLocation: StateFlow<Location?> = _userLocation.asStateFlow()

    fun startLocationUpdates() {
        viewModelScope.launch {
            locationTrackingManager.getLocationUpdates().collect { location ->
                _userLocation.value = location
            }
        }
    }
}

class MapViewModelFactory(
    private val locationTrackingManager: LocationTrackingManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MapViewModel(locationTrackingManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
