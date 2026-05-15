package com.janak.location.alarm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.janak.location.alarm.data.dao.RouteDao
import com.janak.location.alarm.data.entity.RouteBreadcrumbEntity
import com.janak.location.alarm.data.entity.SavedRouteEntity

@Database(entities = [SavedRouteEntity::class, RouteBreadcrumbEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun routeDao(): RouteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "location_alarm_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
