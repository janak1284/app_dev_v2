package com.janak.location.alarm.`data`

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import com.janak.location.alarm.`data`.dao.HistoryDao
import com.janak.location.alarm.`data`.dao.HistoryDao_Impl
import com.janak.location.alarm.`data`.dao.RouteDao
import com.janak.location.alarm.`data`.dao.RouteDao_Impl
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class AppDatabase_Impl : AppDatabase() {
  private val _routeDao: Lazy<RouteDao> = lazy {
    RouteDao_Impl(this)
  }

  private val _historyDao: Lazy<HistoryDao> = lazy {
    HistoryDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(8,
        "d7366f0a2216b282c5614e91e83da43e", "feb5cffeae010aef0eb759fb548f68ea") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `saved_routes` (`routeId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `destinationName` TEXT NOT NULL, `mapDestinationName` TEXT, `destinationLat` REAL NOT NULL, `destinationLng` REAL NOT NULL, `targetTime` INTEGER, `dateSaved` INTEGER NOT NULL, `lastTakenTimestamp` INTEGER NOT NULL, `transportMode` TEXT NOT NULL, `alarmSettings` TEXT NOT NULL, `routeGeoJson` TEXT, `actualDistanceMeters` REAL NOT NULL, `estimatedDurationMillis` INTEGER NOT NULL)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `route_breadcrumbs` (`pointId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `historyId` INTEGER, `routeId` INTEGER, `latitude` REAL NOT NULL, `longitude` REAL NOT NULL, `speed` REAL NOT NULL, `timestamp` INTEGER NOT NULL, FOREIGN KEY(`historyId`) REFERENCES `journey_history`(`historyId`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`routeId`) REFERENCES `saved_routes`(`routeId`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_route_breadcrumbs_historyId` ON `route_breadcrumbs` (`historyId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_route_breadcrumbs_routeId` ON `route_breadcrumbs` (`routeId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `journey_history` (`historyId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `routeId` INTEGER, `startingPointLat` REAL NOT NULL, `startingPointLng` REAL NOT NULL, `destinationLat` REAL NOT NULL, `destinationLng` REAL NOT NULL, `destinationName` TEXT NOT NULL, `mapDestinationName` TEXT, `alarmConfigAtTime` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `durationMillis` INTEGER NOT NULL, `actualDistanceMeters` REAL NOT NULL, `actualRouteGeoJson` TEXT)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `journey_legs` (`legId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `routeId` INTEGER, `historyId` INTEGER, `sequenceIndex` INTEGER NOT NULL, `mode` TEXT NOT NULL, `geometry` TEXT NOT NULL, `startName` TEXT, `endName` TEXT, `startLat` REAL NOT NULL, `startLng` REAL NOT NULL, `endLat` REAL NOT NULL, `endLng` REAL NOT NULL, `distanceMeters` REAL NOT NULL, `durationMillis` INTEGER NOT NULL, `lineName` TEXT, `color` TEXT, FOREIGN KEY(`routeId`) REFERENCES `saved_routes`(`routeId`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`historyId`) REFERENCES `journey_history`(`historyId`) ON UPDATE NO ACTION ON DELETE CASCADE )")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_journey_legs_routeId` ON `journey_legs` (`routeId`)")
        connection.execSQL("CREATE INDEX IF NOT EXISTS `index_journey_legs_historyId` ON `journey_legs` (`historyId`)")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'd7366f0a2216b282c5614e91e83da43e')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `saved_routes`")
        connection.execSQL("DROP TABLE IF EXISTS `route_breadcrumbs`")
        connection.execSQL("DROP TABLE IF EXISTS `journey_history`")
        connection.execSQL("DROP TABLE IF EXISTS `journey_legs`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        connection.execSQL("PRAGMA foreign_keys = ON")
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection):
          RoomOpenDelegate.ValidationResult {
        val _columnsSavedRoutes: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsSavedRoutes.put("routeId", TableInfo.Column("routeId", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSavedRoutes.put("destinationName", TableInfo.Column("destinationName", "TEXT", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSavedRoutes.put("mapDestinationName", TableInfo.Column("mapDestinationName", "TEXT",
            false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSavedRoutes.put("destinationLat", TableInfo.Column("destinationLat", "REAL", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSavedRoutes.put("destinationLng", TableInfo.Column("destinationLng", "REAL", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSavedRoutes.put("targetTime", TableInfo.Column("targetTime", "INTEGER", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSavedRoutes.put("dateSaved", TableInfo.Column("dateSaved", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsSavedRoutes.put("lastTakenTimestamp", TableInfo.Column("lastTakenTimestamp",
            "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSavedRoutes.put("transportMode", TableInfo.Column("transportMode", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSavedRoutes.put("alarmSettings", TableInfo.Column("alarmSettings", "TEXT", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSavedRoutes.put("routeGeoJson", TableInfo.Column("routeGeoJson", "TEXT", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSavedRoutes.put("actualDistanceMeters", TableInfo.Column("actualDistanceMeters",
            "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSavedRoutes.put("estimatedDurationMillis",
            TableInfo.Column("estimatedDurationMillis", "INTEGER", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysSavedRoutes: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesSavedRoutes: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoSavedRoutes: TableInfo = TableInfo("saved_routes", _columnsSavedRoutes,
            _foreignKeysSavedRoutes, _indicesSavedRoutes)
        val _existingSavedRoutes: TableInfo = read(connection, "saved_routes")
        if (!_infoSavedRoutes.equals(_existingSavedRoutes)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |saved_routes(com.janak.location.alarm.data.entity.SavedRouteEntity).
              | Expected:
              |""".trimMargin() + _infoSavedRoutes + """
              |
              | Found:
              |""".trimMargin() + _existingSavedRoutes)
        }
        val _columnsRouteBreadcrumbs: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsRouteBreadcrumbs.put("pointId", TableInfo.Column("pointId", "INTEGER", true, 1,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsRouteBreadcrumbs.put("historyId", TableInfo.Column("historyId", "INTEGER", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsRouteBreadcrumbs.put("routeId", TableInfo.Column("routeId", "INTEGER", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsRouteBreadcrumbs.put("latitude", TableInfo.Column("latitude", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsRouteBreadcrumbs.put("longitude", TableInfo.Column("longitude", "REAL", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsRouteBreadcrumbs.put("speed", TableInfo.Column("speed", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsRouteBreadcrumbs.put("timestamp", TableInfo.Column("timestamp", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysRouteBreadcrumbs: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysRouteBreadcrumbs.add(TableInfo.ForeignKey("journey_history", "CASCADE",
            "NO ACTION", listOf("historyId"), listOf("historyId")))
        _foreignKeysRouteBreadcrumbs.add(TableInfo.ForeignKey("saved_routes", "CASCADE",
            "NO ACTION", listOf("routeId"), listOf("routeId")))
        val _indicesRouteBreadcrumbs: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesRouteBreadcrumbs.add(TableInfo.Index("index_route_breadcrumbs_historyId", false,
            listOf("historyId"), listOf("ASC")))
        _indicesRouteBreadcrumbs.add(TableInfo.Index("index_route_breadcrumbs_routeId", false,
            listOf("routeId"), listOf("ASC")))
        val _infoRouteBreadcrumbs: TableInfo = TableInfo("route_breadcrumbs",
            _columnsRouteBreadcrumbs, _foreignKeysRouteBreadcrumbs, _indicesRouteBreadcrumbs)
        val _existingRouteBreadcrumbs: TableInfo = read(connection, "route_breadcrumbs")
        if (!_infoRouteBreadcrumbs.equals(_existingRouteBreadcrumbs)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |route_breadcrumbs(com.janak.location.alarm.data.entity.RouteBreadcrumbEntity).
              | Expected:
              |""".trimMargin() + _infoRouteBreadcrumbs + """
              |
              | Found:
              |""".trimMargin() + _existingRouteBreadcrumbs)
        }
        val _columnsJourneyHistory: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsJourneyHistory.put("historyId", TableInfo.Column("historyId", "INTEGER", true, 1,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsJourneyHistory.put("routeId", TableInfo.Column("routeId", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsJourneyHistory.put("startingPointLat", TableInfo.Column("startingPointLat", "REAL",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsJourneyHistory.put("startingPointLng", TableInfo.Column("startingPointLng", "REAL",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsJourneyHistory.put("destinationLat", TableInfo.Column("destinationLat", "REAL",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsJourneyHistory.put("destinationLng", TableInfo.Column("destinationLng", "REAL",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsJourneyHistory.put("destinationName", TableInfo.Column("destinationName", "TEXT",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsJourneyHistory.put("mapDestinationName", TableInfo.Column("mapDestinationName",
            "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsJourneyHistory.put("alarmConfigAtTime", TableInfo.Column("alarmConfigAtTime",
            "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsJourneyHistory.put("timestamp", TableInfo.Column("timestamp", "INTEGER", true, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsJourneyHistory.put("durationMillis", TableInfo.Column("durationMillis", "INTEGER",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsJourneyHistory.put("actualDistanceMeters", TableInfo.Column("actualDistanceMeters",
            "REAL", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsJourneyHistory.put("actualRouteGeoJson", TableInfo.Column("actualRouteGeoJson",
            "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysJourneyHistory: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesJourneyHistory: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoJourneyHistory: TableInfo = TableInfo("journey_history", _columnsJourneyHistory,
            _foreignKeysJourneyHistory, _indicesJourneyHistory)
        val _existingJourneyHistory: TableInfo = read(connection, "journey_history")
        if (!_infoJourneyHistory.equals(_existingJourneyHistory)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |journey_history(com.janak.location.alarm.data.entity.JourneyHistoryEntity).
              | Expected:
              |""".trimMargin() + _infoJourneyHistory + """
              |
              | Found:
              |""".trimMargin() + _existingJourneyHistory)
        }
        val _columnsJourneyLegs: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsJourneyLegs.put("legId", TableInfo.Column("legId", "INTEGER", true, 1, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsJourneyLegs.put("routeId", TableInfo.Column("routeId", "INTEGER", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsJourneyLegs.put("historyId", TableInfo.Column("historyId", "INTEGER", false, 0,
            null, TableInfo.CREATED_FROM_ENTITY))
        _columnsJourneyLegs.put("sequenceIndex", TableInfo.Column("sequenceIndex", "INTEGER", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsJourneyLegs.put("mode", TableInfo.Column("mode", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsJourneyLegs.put("geometry", TableInfo.Column("geometry", "TEXT", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsJourneyLegs.put("startName", TableInfo.Column("startName", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsJourneyLegs.put("endName", TableInfo.Column("endName", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsJourneyLegs.put("startLat", TableInfo.Column("startLat", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsJourneyLegs.put("startLng", TableInfo.Column("startLng", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsJourneyLegs.put("endLat", TableInfo.Column("endLat", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsJourneyLegs.put("endLng", TableInfo.Column("endLng", "REAL", true, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsJourneyLegs.put("distanceMeters", TableInfo.Column("distanceMeters", "REAL", true,
            0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsJourneyLegs.put("durationMillis", TableInfo.Column("durationMillis", "INTEGER",
            true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsJourneyLegs.put("lineName", TableInfo.Column("lineName", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        _columnsJourneyLegs.put("color", TableInfo.Column("color", "TEXT", false, 0, null,
            TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysJourneyLegs: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        _foreignKeysJourneyLegs.add(TableInfo.ForeignKey("saved_routes", "CASCADE", "NO ACTION",
            listOf("routeId"), listOf("routeId")))
        _foreignKeysJourneyLegs.add(TableInfo.ForeignKey("journey_history", "CASCADE", "NO ACTION",
            listOf("historyId"), listOf("historyId")))
        val _indicesJourneyLegs: MutableSet<TableInfo.Index> = mutableSetOf()
        _indicesJourneyLegs.add(TableInfo.Index("index_journey_legs_routeId", false,
            listOf("routeId"), listOf("ASC")))
        _indicesJourneyLegs.add(TableInfo.Index("index_journey_legs_historyId", false,
            listOf("historyId"), listOf("ASC")))
        val _infoJourneyLegs: TableInfo = TableInfo("journey_legs", _columnsJourneyLegs,
            _foreignKeysJourneyLegs, _indicesJourneyLegs)
        val _existingJourneyLegs: TableInfo = read(connection, "journey_legs")
        if (!_infoJourneyLegs.equals(_existingJourneyLegs)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |journey_legs(com.janak.location.alarm.data.entity.JourneyLegEntity).
              | Expected:
              |""".trimMargin() + _infoJourneyLegs + """
              |
              | Found:
              |""".trimMargin() + _existingJourneyLegs)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "saved_routes",
        "route_breadcrumbs", "journey_history", "journey_legs")
  }

  public override fun clearAllTables() {
    super.performClear(true, "saved_routes", "route_breadcrumbs", "journey_history", "journey_legs")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(RouteDao::class, RouteDao_Impl.getRequiredConverters())
    _typeConvertersMap.put(HistoryDao::class, HistoryDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override
      fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>):
      List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun routeDao(): RouteDao = _routeDao.value

  public override fun historyDao(): HistoryDao = _historyDao.value
}
