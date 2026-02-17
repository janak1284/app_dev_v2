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

import com.janak.location.alarm.alarm.AlarmHandler
import org.maplibre.android.geometry.LatLng
import kotlin.math.roundToInt

class MapViewModel(
    private val locationTrackingManager: LocationTrackingManager,
    private val alarmHandler: AlarmHandler
) : ViewModel() {

    private val _userLocation = MutableStateFlow<Location?>(null)
    val userLocation: StateFlow<Location?> = _userLocation.asStateFlow()

    private val _destination = MutableStateFlow<LatLng?>(null)
    val destination: StateFlow<LatLng?> = _destination.asStateFlow()

    private val _isAlarmSet = MutableStateFlow(false)
    val isAlarmSet: StateFlow<Boolean> = _isAlarmSet.asStateFlow()

    private val _distanceToDestination = MutableStateFlow<String?>(null)
    val distanceToDestination: StateFlow<String?> = _distanceToDestination.asStateFlow()

    private val ALARM_RADIUS_METERS = 500

    fun startLocationUpdates() {
        android.util.Log.d("MapViewModel", "startLocationUpdates: Called")
        viewModelScope.launch {
            locationTrackingManager.getLocationUpdates().collect { location ->
                android.util.Log.d("MapViewModel", "startLocationUpdates: Received location $location")
                _userLocation.value = location
                checkDistance(location)
            }
        }
    }

    fun setDestination(latLng: LatLng) {
        if (!_isAlarmSet.value) {
            _destination.value = latLng
            // Recalculate distance immediately if we have a location
            _userLocation.value?.let { checkDistance(it) }
        }
    }

    fun toggleAlarm() {
        if (_isAlarmSet.value) {
            stopAlarm()
        } else {
            if (_destination.value != null) {
                _isAlarmSet.value = true
                _userLocation.value?.let { checkDistance(it) }
            }
        }
    }

    private fun stopAlarm() {
        _isAlarmSet.value = false
        _distanceToDestination.value = null
        alarmHandler.stopAlarm()
    }

    private fun checkDistance(currentLocation: Location) {
        val dest = _destination.value ?: return

        val results = FloatArray(1)
        Location.distanceBetween(
            currentLocation.latitude,
            currentLocation.longitude,
            dest.latitude,
            dest.longitude,
            results
        )
        val distance = results[0]

        if (_isAlarmSet.value) {
            _distanceToDestination.value = "${distance.roundToInt()}m"
            if (distance <= ALARM_RADIUS_METERS) {
                alarmHandler.startAlarm()
            }
        } else {
            _distanceToDestination.value = null
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        alarmHandler.stopAlarm()
    }
}

class MapViewModelFactory(
    private val locationTrackingManager: LocationTrackingManager,
    private val alarmHandler: AlarmHandler
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MapViewModel(locationTrackingManager, alarmHandler) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
