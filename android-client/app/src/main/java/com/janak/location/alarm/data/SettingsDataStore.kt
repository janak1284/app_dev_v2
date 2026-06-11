package com.janak.location.alarm.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsDataStore(private val context: Context) {
    companion object {
        val IS_DEMO_ENABLED = booleanPreferencesKey("is_demo_enabled")
        val SELECTED_ROUTE = stringPreferencesKey("selected_route")
        val IS_DEMO_PLAYBACK_ACTIVE = booleanPreferencesKey("is_demo_playback_active")
    }

    val demoSettingsFlow: Flow<DemoSettings> = context.dataStore.data
        .map { preferences ->
            val isDemoEnabled = preferences[IS_DEMO_ENABLED] ?: false
            val selectedRoute = preferences[SELECTED_ROUTE] ?: "555S"
            val isDemoPlaybackActive = preferences[IS_DEMO_PLAYBACK_ACTIVE] ?: false
            DemoSettings(isDemoEnabled, selectedRoute, isDemoPlaybackActive)
        }

    suspend fun setDemoEnabled(isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_DEMO_ENABLED] = isEnabled
            if (!isEnabled) {
                preferences[IS_DEMO_PLAYBACK_ACTIVE] = false
            }
        }
    }

    suspend fun setSelectedRoute(route: String) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_ROUTE] = route
        }
    }

    suspend fun setDemoPlaybackActive(isActive: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[IS_DEMO_PLAYBACK_ACTIVE] = isActive
        }
    }
}

data class DemoSettings(
    val isDemoEnabled: Boolean,
    val selectedRoute: String,
    val isDemoPlaybackActive: Boolean
)
