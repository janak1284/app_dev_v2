package com.janak.location.alarm.data.repository

import androidx.room.withTransaction
import com.janak.location.alarm.api.RetrofitClient
import com.janak.location.alarm.data.AppDatabase
import com.janak.location.alarm.data.entity.JourneyLegEntity
import com.janak.location.alarm.data.entity.RouteBreadcrumbEntity
import com.janak.location.alarm.data.entity.SavedRouteEntity
import com.janak.location.alarm.model.*
import kotlinx.coroutines.flow.Flow
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

    suspend fun getRailwayItinerary(startLat: Double, startLng: Double, destLat: Double, destLng: Double): TransitItinerary? {
        try {
            // OpenRailRouting (GraphHopper) expects "lat,lon"
            val points = listOf("$startLat,$startLng", "$destLat,$destLng")
            val response = RetrofitClient.openRailRoutingApiService.getRoute(points = points)
            if (response.isSuccessful) {
                return response.body()?.let { mapGraphHopperToItinerary(it) }
            } else {
                android.util.Log.e("RouteRepository", "Railway API Error: ${response.errorBody()?.string()}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun mapGraphHopperToItinerary(response: GraphHopperResponse): TransitItinerary {
        val legs = response.paths.map { path ->
            // Convert GH points to GeoJSON LineString manually
            val coords = path.points.coordinates.joinToString(",") { "[${it[0]},${it[1]}]" }
            val geoJson = "{\"type\":\"LineString\",\"coordinates\":[$coords]}"

            JourneyLeg(
                mode = TransportMode.TRAIN,
                geometry = geoJson,
                distanceMeters = path.distance,
                durationMillis = path.time,
                startName = "Start",
                endName = "End",
                startLat = path.points.coordinates.first()[1],
                startLng = path.points.coordinates.first()[0],
                endLat = path.points.coordinates.last()[1],
                endLng = path.points.coordinates.last()[0]
            )
        }

        return TransitItinerary(
            legs = legs,
            totalDistanceMeters = response.paths.sumOf { it.distance },
            totalDurationMillis = response.paths.sumOf { it.time }
        )
    }
}
