package com.janak.location.alarm.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.janak.location.alarm.data.entity.StationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStations(stations: List<StationEntity>)

    @Query("SELECT * FROM stations WHERE code = :code")
    suspend fun getStationByCode(code: String): StationEntity?

    @Query("SELECT * FROM stations WHERE name LIKE '%' || :query || '%' OR code LIKE '%' || :query || '%' LIMIT 20")
    fun searchStations(query: String): Flow<List<StationEntity>>

    @Query("SELECT * FROM stations WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLon AND :maxLon")
    suspend fun getStationsInBox(minLat: Double, maxLat: Double, minLon: Double, maxLon: Double): List<StationEntity>

    @Query("SELECT COUNT(*) FROM stations")
    suspend fun getStationCount(): Int
}
