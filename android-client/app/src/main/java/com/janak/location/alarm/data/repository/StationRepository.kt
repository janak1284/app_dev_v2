package com.janak.location.alarm.data.repository

import android.content.Context
import com.janak.location.alarm.R
import com.janak.location.alarm.data.AppDatabase
import com.janak.location.alarm.data.entity.StationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStreamReader
import com.janak.location.alarm.util.AppLogger

@Serializable
private data class StationFeatureCollection(
    val features: List<StationFeature>
)

@Serializable
private data class StationFeature(
    val geometry: StationGeometry? = null,
    val properties: StationProperties
)

@Serializable
private data class StationGeometry(
    val coordinates: List<Double>
)

@Serializable
private data class StationProperties(
    val code: String,
    val name: String,
    val state: String? = null,
    val zone: String? = null,
    val address: String? = null
)

class StationRepository(private val database: AppDatabase, private val context: Context) {
    private val stationDao = database.stationDao()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun ensureStationsLoaded() {
        withContext(Dispatchers.IO) {
            val count = stationDao.getStationCount()
            if (count == 0) {
                loadStationsFromRaw()
            }
        }
    }

    private suspend fun loadStationsFromRaw() {
        try {
            val inputStream = context.resources.openRawResource(R.raw.stations)
            val reader = InputStreamReader(inputStream)
            val jsonString = reader.readText()
            val collection = json.decodeFromString<StationFeatureCollection>(jsonString)
            
            val entities = collection.features.mapNotNull { feature ->
                val coords = feature.geometry?.coordinates
                if (coords != null && coords.size >= 2) {
                    StationEntity(
                        code = feature.properties.code,
                        name = feature.properties.name,
                        latitude = coords[1],
                        longitude = coords[0],
                        state = feature.properties.state,
                        zone = feature.properties.zone,
                        address = feature.properties.address
                    )
                } else {
                    null
                }
            }
            
            stationDao.insertStations(entities)
        } catch (e: Exception) {
            AppLogger.e("StationRepository", "Failed to load stations from raw", e)
        }
    }

    fun searchStations(query: String): Flow<List<StationEntity>> {
        return stationDao.searchStations(query)
    }

    suspend fun getNearbyStations(lat: Double, lon: Double, radiusKm: Double, limit: Int = 5): List<StationEntity> {
        return withContext(Dispatchers.IO) {
            // Since Room doesn't support spatial queries out of the box without SpatiaLite,
            // and we have a manageable number of stations (~8000), we can do a simple box filter
            // followed by a precise distance calculation.
            
            val latRange = radiusKm / 111.0
            val lonRange = radiusKm / (111.0 * Math.cos(Math.toRadians(lat)))
            
            // This is a rough approximation but good enough for a first pass
            // We'll need to add a DAO method for this.
            stationDao.getStationsInBox(
                minLat = lat - latRange,
                maxLat = lat + latRange,
                minLon = lon - lonRange,
                maxLon = lon + lonRange
            ).map { station ->
                val distance = calculateDistance(lat, lon, station.latitude, station.longitude)
                station to distance
            }.filter { it.second <= radiusKm }
                .sortedBy { it.second }
                .take(limit)
                .map { it.first }
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0] / 1000.0 // Convert to km
    }

    suspend fun getStationByCode(code: String): StationEntity? {
        return stationDao.getStationByCode(code)
    }
}
