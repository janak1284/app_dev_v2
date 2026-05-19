package com.janak.location.alarm.data.repository

import androidx.room.withTransaction
import com.janak.location.alarm.data.AppDatabase
import com.janak.location.alarm.data.entity.JourneyHistoryEntity
import com.janak.location.alarm.data.entity.RouteBreadcrumbEntity
import kotlinx.coroutines.flow.Flow

class HistoryRepository(private val database: AppDatabase) {
    private val historyDao = database.historyDao()
    
    val allHistory: Flow<List<JourneyHistoryEntity>> = historyDao.getAllJourneys()

    suspend fun saveJourneyLog(journey: JourneyHistoryEntity, breadcrumbs: List<RouteBreadcrumbEntity>): Long {
        return database.withTransaction {
            val id = historyDao.insertJourney(journey)
            val breadcrumbsWithId = breadcrumbs.map { it.copy(historyId = id) }
            historyDao.insertBreadcrumbs(breadcrumbsWithId)
            historyDao.pruneHistory(100)
            id
        }
    }

    fun getBreadcrumbsForHistory(historyId: Long): Flow<List<RouteBreadcrumbEntity>> {
        return historyDao.getBreadcrumbsForHistory(historyId)
    }

    suspend fun clearAllHistory() {
        historyDao.deleteAllHistory()
    }

    suspend fun deleteJourneys(journeys: List<JourneyHistoryEntity>) {
        historyDao.deleteJourneys(journeys)
    }
}
