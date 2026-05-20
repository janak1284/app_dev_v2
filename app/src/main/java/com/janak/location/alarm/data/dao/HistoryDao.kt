package com.janak.location.alarm.data.dao

import androidx.room.*
import com.janak.location.alarm.data.entity.JourneyHistoryEntity
import com.janak.location.alarm.data.entity.RouteBreadcrumbEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJourney(journey: JourneyHistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBreadcrumbs(breadcrumbs: List<RouteBreadcrumbEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBreadcrumb(breadcrumb: RouteBreadcrumbEntity): Long

    @Query("SELECT * FROM route_breadcrumbs WHERE historyId = :historyId ORDER BY timestamp ASC")
    suspend fun getBreadcrumbsForHistorySync(historyId: Long): List<RouteBreadcrumbEntity>

    @Query("""
        UPDATE journey_history 
        SET startingPointLat = :startLat, 
            startingPointLng = :startLng,
            actualDistanceMeters = :distance,
            durationMillis = :duration,
            actualRouteGeoJson = :geoJson
        WHERE historyId = :id
    """)
    suspend fun updateJourneySummary(id: Long, startLat: Double, startLng: Double, distance: Double, duration: Long, geoJson: String?)

    @Query("SELECT * FROM journey_history ORDER BY timestamp DESC")
    fun getAllJourneys(): Flow<List<JourneyHistoryEntity>>

    @Query("SELECT * FROM route_breadcrumbs WHERE historyId = :historyId ORDER BY timestamp ASC")
    fun getBreadcrumbsForHistory(historyId: Long): Flow<List<RouteBreadcrumbEntity>>

    @Query("DELETE FROM journey_history WHERE historyId NOT IN (SELECT historyId FROM journey_history ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun pruneHistory(limit: Int = 100)

    @Query("DELETE FROM journey_history")
    suspend fun deleteAllHistory(): Int

    @Query("SELECT * FROM journey_history ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestJourney(): JourneyHistoryEntity?

    @Delete
    suspend fun deleteJourneys(journeys: List<JourneyHistoryEntity>)
}
