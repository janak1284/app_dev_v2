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
            val startPoint = "$startLat,$startLng"
            val endPoint = "$destLat,$destLng"
            val response = RetrofitClient.openRailRoutingApi.getTrackGeometry(startPoint, endPoint)
            if (response.isSuccessful) {
                val orrResponse = response.body() ?: return null
                val path = orrResponse.paths.firstOrNull() ?: return null
                
                val leg = JourneyLeg(
                    mode = TransportMode.TRAIN,
                    geometry = com.mapbox.geojson.LineString.fromLngLats(
                        com.janak.location.alarm.util.PolylineDecoder.decode(path.points)
                    ).toJson(),
                    startLat = startLat,
                    startLng = startLng,
                    endLat = destLat,
                    endLng = destLng,
                    distanceMeters = path.distanceMeters,
                    durationMillis = path.timeMillis,
                    startName = "Origin Station",
                    endName = "Destination Station"
                )
                
                return TransitItinerary(
                    legs = listOf(leg),
                    totalDistanceMeters = path.distanceMeters,
                    totalDurationMillis = path.timeMillis
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("RouteRepository", "getRailwayItinerary failed", e)
        }
        return null
    }
}
