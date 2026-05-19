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

    @Query("SELECT * FROM journey_history ORDER BY timestamp DESC")
    fun getAllJourneys(): Flow<List<JourneyHistoryEntity>>

    @Query("SELECT * FROM route_breadcrumbs WHERE historyId = :historyId ORDER BY timestamp ASC")
    fun getBreadcrumbsForHistory(historyId: Long): Flow<List<RouteBreadcrumbEntity>>

    @Query("DELETE FROM journey_history WHERE historyId NOT IN (SELECT historyId FROM journey_history ORDER BY timestamp DESC LIMIT :limit)")
    suspend fun pruneHistory(limit: Int = 100)

    @Query("DELETE FROM journey_history")
    suspend fun deleteAllHistory(): Int

    @Delete
    suspend fun deleteJourneys(journeys: List<JourneyHistoryEntity>)
}
