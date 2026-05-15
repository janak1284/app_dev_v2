package com.janak.location.alarm.data.repository

import com.janak.location.alarm.data.dao.RouteDao
import com.janak.location.alarm.data.entity.RouteBreadcrumbEntity
import com.janak.location.alarm.data.entity.SavedRouteEntity
import kotlinx.coroutines.flow.Flow

class RouteRepository(private val routeDao: RouteDao) {
    val allSavedRoutes: Flow<List<SavedRouteEntity>> = routeDao.getAllSavedRoutes()

    suspend fun saveJourney(route: SavedRouteEntity, breadcrumbs: List<RouteBreadcrumbEntity>) {
        routeDao.saveJourney(route, breadcrumbs)
    }

    fun getBreadcrumbsForRoute(routeId: Long): Flow<List<RouteBreadcrumbEntity>> {
        return routeDao.getBreadcrumbsForRoute(routeId)
    }

    suspend fun deleteRoute(route: SavedRouteEntity) {
        routeDao.deleteRoute(route)
    }
}
