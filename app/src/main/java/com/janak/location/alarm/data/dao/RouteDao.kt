package com.janak.location.alarm.data.dao

import androidx.room.*
import com.janak.location.alarm.data.entity.RouteBreadcrumbEntity
import com.janak.location.alarm.data.entity.SavedRouteEntity
import kotlinx.coroutines.flow.Flow

@Dao
@JvmSuppressWildcards
interface RouteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedRoute(route: SavedRouteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBreadcrumbs(breadcrumbs: List<RouteBreadcrumbEntity>): List<Long>

    @Query("SELECT * FROM saved_routes ORDER BY dateSaved DESC")
    fun getAllSavedRoutes(): Flow<List<SavedRouteEntity>>

    @Query("SELECT * FROM route_breadcrumbs WHERE routeId = :routeId ORDER BY timestamp ASC")
    fun getBreadcrumbsForRoute(routeId: Long): Flow<List<RouteBreadcrumbEntity>>

    @Update
    suspend fun updateSavedRoute(route: SavedRouteEntity): Int

    @Delete
    suspend fun deleteRoute(route: SavedRouteEntity): Int

    @Query("DELETE FROM saved_routes")
    suspend fun deleteAllRoutes(): Int
}
