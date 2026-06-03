package com.janak.location.alarm.data.repository

import androidx.room.withTransaction
import com.janak.location.alarm.data.AppDatabase
import com.janak.location.alarm.data.entity.JourneyHistoryEntity
import com.janak.location.alarm.data.entity.JourneyLegEntity
import com.janak.location.alarm.data.entity.RouteBreadcrumbEntity
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val database: AppDatabase) {
    private val historyDao = database.historyDao()
    
    val allHistory: Flow<List<JourneyHistoryEntity>> = historyDao.getAllJourneys()

    suspend fun saveJourneyLog(journey: JourneyHistoryEntity, breadcrumbs: List<RouteBreadcrumbEntity>): Long {
        return database.withTransaction {
            val id = historyDao.insertJourney(journey)
            val breadcrumbsWithId = breadcrumbs.map { it.copy(historyId = id) }
            
            // Chunk insertion to avoid SQLite parameter limit (default 999)
            breadcrumbsWithId.chunked(100).forEach { batch ->
                historyDao.insertBreadcrumbs(batch)
            }
            
            historyDao.pruneHistory(10)
            id
        }
    }

    suspend fun saveJourneyLogWithLegs(journey: JourneyHistoryEntity, breadcrumbs: List<RouteBreadcrumbEntity>, legs: List<JourneyLegEntity>): Long {
        return database.withTransaction {
            val id = historyDao.insertJourney(journey)
            val breadcrumbsWithId = breadcrumbs.map { it.copy(historyId = id) }
            val legsWithId = legs.map { it.copy(historyId = id) }
            
            breadcrumbsWithId.chunked(100).forEach { batch ->
                historyDao.insertBreadcrumbs(batch)
            }
            
            historyDao.insertLegs(legsWithId)
            historyDao.pruneHistory(10)
            id
        }
    }

    suspend fun saveJourneyLog(id: Long, breadcrumb: RouteBreadcrumbEntity) {
        historyDao.insertBreadcrumb(breadcrumb.copy(historyId = id))
    }

    suspend fun getBreadcrumbsForHistorySync(historyId: Long): List<RouteBreadcrumbEntity> {
        return historyDao.getBreadcrumbsForHistorySync(historyId)
    }

    suspend fun updateJourneySummary(id: Long, startLat: Double, startLng: Double, distance: Double, duration: Long, geoJson: String?) {
        historyDao.updateJourneySummary(id, startLat, startLng, distance, duration, geoJson)
    }

    fun getBreadcrumbsForHistory(historyId: Long): Flow<List<RouteBreadcrumbEntity>> {
        return historyDao.getBreadcrumbsForHistory(historyId)
    }

    fun getLegsForHistory(historyId: Long): Flow<List<JourneyLegEntity>> {
        return historyDao.getLegsForHistory(historyId)
    }

    suspend fun clearAllHistory() {
        historyDao.deleteAllHistory()
    }

    suspend fun deleteJourneys(journeys: List<JourneyHistoryEntity>) {
        historyDao.deleteJourneys(journeys)
    }

    suspend fun getLatestJourney(): JourneyHistoryEntity? {
        return historyDao.getLatestJourney()
    }
}
