package com.janak.location.alarm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.janak.location.alarm.data.dao.HistoryDao
import com.janak.location.alarm.data.dao.RouteDao
import com.janak.location.alarm.data.dao.StationDao
import com.janak.location.alarm.data.dao.RailwayTrackCacheDao
import com.janak.location.alarm.data.entity.*

@Database(
    entities = [
        SavedRouteEntity::class, 
        RouteBreadcrumbEntity::class, 
        JourneyHistoryEntity::class, 
        JourneyLegEntity::class,
        StationEntity::class,
        RailwayTrackCacheEntity::class
    ], 
    version = 10, 
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun routeDao(): RouteDao
    abstract fun historyDao(): HistoryDao
    abstract fun stationDao(): StationDao
    abstract fun railwayTrackCacheDao(): RailwayTrackCacheDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "location_alarm_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
