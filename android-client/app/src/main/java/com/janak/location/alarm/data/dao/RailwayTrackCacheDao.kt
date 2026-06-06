package com.janak.location.alarm.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.janak.location.alarm.data.entity.RailwayTrackCacheEntity

@Dao
interface RailwayTrackCacheDao {
    @Query("SELECT * FROM railway_track_cache WHERE segmentKey = :key")
    suspend fun getTrack(key: String): RailwayTrackCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: RailwayTrackCacheEntity)

    @Query("DELETE FROM railway_track_cache WHERE timestamp < :expiry")
    suspend fun clearExpired(expiry: Long)
}
