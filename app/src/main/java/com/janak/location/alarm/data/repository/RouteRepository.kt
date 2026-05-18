package com.janak.location.alarm.data.repository

import androidx.room.withTransaction
import com.janak.location.alarm.data.AppDatabase
import com.janak.location.alarm.data.entity.RouteBreadcrumbEntity
import com.janak.location.alarm.data.entity.SavedRouteEntity
import kotlinx.coroutines.flow.Flow

class RouteRepository(private val database: AppDatabase) {
    private val routeDao = database.routeDao()
    
    val allSavedRoutes: Flow<List<SavedRouteEntity>> = routeDao.getAllSavedRoutes()

    suspend fun saveJourney(route: SavedRouteEntity, breadcrumbs: List<RouteBreadcrumbEntity>): Long {
        return database.withTransaction {
            val id = routeDao.insertSavedRoute(route)
            val breadcrumbsWithId = breadcrumbs.map { it.copy(routeId = id) }
            routeDao.insertBreadcrumbs(breadcrumbsWithId)
            id
        }
    }

    fun getBreadcrumbsForRoute(routeId: Long): Flow<List<RouteBreadcrumbEntity>> {
        return routeDao.getBreadcrumbsForRoute(routeId)
    }

    suspend fun deleteRoute(route: SavedRouteEntity) {
        routeDao.deleteRoute(route)
    }
}
