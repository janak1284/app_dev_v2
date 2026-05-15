package com.janak.location.alarm.data.dao

import androidx.room.*
import com.janak.location.alarm.data.entity.RouteBreadcrumbEntity
import com.janak.location.alarm.data.entity.SavedRouteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedRoute(route: SavedRouteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBreadcrumbs(breadcrumbs: List<RouteBreadcrumbEntity>)

    @Query("SELECT * FROM saved_routes ORDER BY dateSaved DESC")
    fun getAllSavedRoutes(): Flow<List<SavedRouteEntity>>

    @Query("SELECT * FROM route_breadcrumbs WHERE routeId = :routeId ORDER BY timestamp ASC")
    fun getBreadcrumbsForRoute(routeId: Long): Flow<List<RouteBreadcrumbEntity>>

    @Delete
    suspend fun deleteRoute(route: SavedRouteEntity)

    @Transaction
    suspend fun saveJourney(route: SavedRouteEntity, breadcrumbs: List<RouteBreadcrumbEntity>) {
        val id = insertSavedRoute(route)
        val breadcrumbsWithId = breadcrumbs.map { it.copy(routeId = id) }
        insertBreadcrumbs(breadcrumbsWithId)
    }
}
