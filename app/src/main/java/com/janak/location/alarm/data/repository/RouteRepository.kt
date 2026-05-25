package com.janak.location.alarm.data.repository

import androidx.room.withTransaction
import com.janak.location.alarm.api.RetrofitClient
import com.janak.location.alarm.api.ValhallaApiService
import com.janak.location.alarm.data.AppDatabase
import com.janak.location.alarm.data.entity.JourneyLegEntity
import com.janak.location.alarm.data.entity.RouteBreadcrumbEntity
import com.janak.location.alarm.data.entity.SavedRouteEntity
import com.janak.location.alarm.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class RouteRepository(private val database: AppDatabase) {
    private val routeDao = database.routeDao()
    
    val allSavedRoutes: Flow<List<SavedRouteEntity>> = routeDao.getAllSavedRoutes()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun saveJourney(route: SavedRouteEntity, breadcrumbs: List<RouteBreadcrumbEntity>): Long {
        return database.withTransaction {
            val id = routeDao.insertSavedRoute(route)
            val breadcrumbsWithId = breadcrumbs.map { it.copy(routeId = id) }
            routeDao.insertBreadcrumbs(breadcrumbsWithId)
            id
        }
    }

    suspend fun saveRouteWithLegs(route: SavedRouteEntity, legs: List<JourneyLegEntity>): Long {
        return database.withTransaction {
            val id = routeDao.insertSavedRoute(route)
            val legsWithId = legs.map { it.copy(routeId = id) }
            routeDao.insertLegs(legsWithId)
            id
        }
    }

    fun getBreadcrumbsForRoute(routeId: Long): Flow<List<RouteBreadcrumbEntity>> {
        return routeDao.getBreadcrumbsForRoute(routeId)
    }

    fun getLegsForRoute(routeId: Long): Flow<List<JourneyLegEntity>> {
        return routeDao.getLegsForRoute(routeId)
    }

    suspend fun deleteRoute(route: SavedRouteEntity) {
        routeDao.deleteRoute(route)
    }

    suspend fun updateSavedRoute(route: SavedRouteEntity) {
        routeDao.updateSavedRoute(route)
    }

    suspend fun deleteAllRoutes() {
        routeDao.deleteAllRoutes()
    }

    suspend fun getTransitItinerary(startLat: Double, startLng: Double, destLat: Double, destLng: Double): TransitItinerary? {
        val request = ValhallaRequest(
            locations = listOf(
                ValhallaLocation(startLat, startLng),
                ValhallaLocation(destLat, destLng)
            ),
            costing = "multimodal"
        )
        val jsonRequest = json.encodeToString(request)
        
        for (baseUrl in ValhallaApiService.INSTANCES) {
            try {
                val response = RetrofitClient.valhallaApiService.getRoute("${baseUrl}route", jsonRequest)
                if (response.isSuccessful) {
                    return response.body()?.let { mapValhallaToItinerary(it) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }

    private fun mapValhallaToItinerary(response: ValhallaResponse): TransitItinerary {
        val legs = response.trip.legs.map { valhallaLeg ->
            // For now, simplify 1 Valhalla Leg -> 1 JourneyLeg.
            // In a full implementation, we'd split by maneuver mode.
            val firstManeuver = valhallaLeg.maneuvers.firstOrNull()
            val transitInfo = valhallaLeg.maneuvers.firstNotNullOfOrNull { it.transit_info }
            
            val mode = when (firstManeuver?.travel_mode?.lowercase()) {
                "walk" -> TransportMode.WALK
                "bus" -> TransportMode.BUS
                "rail", "train" -> TransportMode.TRAIN
                "subway" -> TransportMode.SUBWAY
                "tram" -> TransportMode.TRAM
                else -> TransportMode.ROAD
            }

            JourneyLeg(
                mode = mode,
                geometry = valhallaLeg.shape,
                distanceMeters = valhallaLeg.summary.length * 1000.0,
                durationMillis = (valhallaLeg.summary.time * 1000.0).toLong(),
                startName = valhallaLeg.maneuvers.firstOrNull()?.instruction,
                endName = valhallaLeg.maneuvers.lastOrNull()?.instruction,
                startLat = response.trip.locations.first().lat, // This is simplified
                startLng = response.trip.locations.first().lon,
                endLat = response.trip.locations.last().lat,
                endLng = response.trip.locations.last().lon,
                lineName = transitInfo?.long_name ?: transitInfo?.short_name,
                headsign = transitInfo?.headsign
            )
        }
        
        return TransitItinerary(
            legs = legs,
            totalDistanceMeters = response.trip.summary.length * 1000.0,
            totalDurationMillis = (response.trip.summary.time * 1000.0).toLong()
        )
    }
}
