package com.janak.location.alarm.`data`.dao

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.getTotalChangedRows
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import com.janak.location.alarm.`data`.Converters
import com.janak.location.alarm.`data`.entity.JourneyLegEntity
import com.janak.location.alarm.`data`.entity.RouteBreadcrumbEntity
import com.janak.location.alarm.`data`.entity.SavedRouteEntity
import com.janak.location.alarm.model.AlarmSettings
import com.janak.location.alarm.model.TransportMode
import javax.`annotation`.processing.Generated
import kotlin.Double
import kotlin.Float
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class RouteDao_Impl(
  __db: RoomDatabase,
) : RouteDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfSavedRouteEntity: EntityInsertAdapter<SavedRouteEntity>

  private val __converters: Converters = Converters()

  private val __insertAdapterOfRouteBreadcrumbEntity: EntityInsertAdapter<RouteBreadcrumbEntity>

  private val __insertAdapterOfJourneyLegEntity: EntityInsertAdapter<JourneyLegEntity>

  private val __deleteAdapterOfSavedRouteEntity: EntityDeleteOrUpdateAdapter<SavedRouteEntity>

  private val __updateAdapterOfSavedRouteEntity: EntityDeleteOrUpdateAdapter<SavedRouteEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfSavedRouteEntity = object : EntityInsertAdapter<SavedRouteEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `saved_routes` (`routeId`,`destinationName`,`mapDestinationName`,`destinationLat`,`destinationLng`,`targetTime`,`dateSaved`,`lastTakenTimestamp`,`transportMode`,`alarmSettings`,`routeGeoJson`,`actualDistanceMeters`,`estimatedDurationMillis`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: SavedRouteEntity) {
        statement.bindLong(1, entity.routeId)
        statement.bindText(2, entity.destinationName)
        val _tmpMapDestinationName: String? = entity.mapDestinationName
        if (_tmpMapDestinationName == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpMapDestinationName)
        }
        statement.bindDouble(4, entity.destinationLat)
        statement.bindDouble(5, entity.destinationLng)
        val _tmpTargetTime: Long? = entity.targetTime
        if (_tmpTargetTime == null) {
          statement.bindNull(6)
        } else {
          statement.bindLong(6, _tmpTargetTime)
        }
        statement.bindLong(7, entity.dateSaved)
        statement.bindLong(8, entity.lastTakenTimestamp)
        val _tmp: String = __converters.fromTransportMode(entity.transportMode)
        statement.bindText(9, _tmp)
        val _tmp_1: String = __converters.fromAlarmSettings(entity.alarmSettings)
        statement.bindText(10, _tmp_1)
        val _tmpRouteGeoJson: String? = entity.routeGeoJson
        if (_tmpRouteGeoJson == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpRouteGeoJson)
        }
        statement.bindDouble(12, entity.actualDistanceMeters)
        statement.bindLong(13, entity.estimatedDurationMillis)
      }
    }
    this.__insertAdapterOfRouteBreadcrumbEntity = object :
        EntityInsertAdapter<RouteBreadcrumbEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `route_breadcrumbs` (`pointId`,`historyId`,`routeId`,`latitude`,`longitude`,`speed`,`timestamp`) VALUES (nullif(?, 0),?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: RouteBreadcrumbEntity) {
        statement.bindLong(1, entity.pointId)
        val _tmpHistoryId: Long? = entity.historyId
        if (_tmpHistoryId == null) {
          statement.bindNull(2)
        } else {
          statement.bindLong(2, _tmpHistoryId)
        }
        val _tmpRouteId: Long? = entity.routeId
        if (_tmpRouteId == null) {
          statement.bindNull(3)
        } else {
          statement.bindLong(3, _tmpRouteId)
        }
        statement.bindDouble(4, entity.latitude)
        statement.bindDouble(5, entity.longitude)
        statement.bindDouble(6, entity.speed.toDouble())
        statement.bindLong(7, entity.timestamp)
      }
    }
    this.__insertAdapterOfJourneyLegEntity = object : EntityInsertAdapter<JourneyLegEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `journey_legs` (`legId`,`routeId`,`historyId`,`sequenceIndex`,`mode`,`geometry`,`startName`,`endName`,`startLat`,`startLng`,`endLat`,`endLng`,`distanceMeters`,`durationMillis`,`lineName`,`color`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: JourneyLegEntity) {
        statement.bindLong(1, entity.legId)
        val _tmpRouteId: Long? = entity.routeId
        if (_tmpRouteId == null) {
          statement.bindNull(2)
        } else {
          statement.bindLong(2, _tmpRouteId)
        }
        val _tmpHistoryId: Long? = entity.historyId
        if (_tmpHistoryId == null) {
          statement.bindNull(3)
        } else {
          statement.bindLong(3, _tmpHistoryId)
        }
        statement.bindLong(4, entity.sequenceIndex.toLong())
        val _tmp: String = __converters.fromTransportMode(entity.mode)
        statement.bindText(5, _tmp)
        statement.bindText(6, entity.geometry)
        val _tmpStartName: String? = entity.startName
        if (_tmpStartName == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpStartName)
        }
        val _tmpEndName: String? = entity.endName
        if (_tmpEndName == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpEndName)
        }
        statement.bindDouble(9, entity.startLat)
        statement.bindDouble(10, entity.startLng)
        statement.bindDouble(11, entity.endLat)
        statement.bindDouble(12, entity.endLng)
        statement.bindDouble(13, entity.distanceMeters)
        statement.bindLong(14, entity.durationMillis)
        val _tmpLineName: String? = entity.lineName
        if (_tmpLineName == null) {
          statement.bindNull(15)
        } else {
          statement.bindText(15, _tmpLineName)
        }
        val _tmpColor: String? = entity.color
        if (_tmpColor == null) {
          statement.bindNull(16)
        } else {
          statement.bindText(16, _tmpColor)
        }
      }
    }
    this.__deleteAdapterOfSavedRouteEntity = object :
        EntityDeleteOrUpdateAdapter<SavedRouteEntity>() {
      protected override fun createQuery(): String =
          "DELETE FROM `saved_routes` WHERE `routeId` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: SavedRouteEntity) {
        statement.bindLong(1, entity.routeId)
      }
    }
    this.__updateAdapterOfSavedRouteEntity = object :
        EntityDeleteOrUpdateAdapter<SavedRouteEntity>() {
      protected override fun createQuery(): String =
          "UPDATE OR ABORT `saved_routes` SET `routeId` = ?,`destinationName` = ?,`mapDestinationName` = ?,`destinationLat` = ?,`destinationLng` = ?,`targetTime` = ?,`dateSaved` = ?,`lastTakenTimestamp` = ?,`transportMode` = ?,`alarmSettings` = ?,`routeGeoJson` = ?,`actualDistanceMeters` = ?,`estimatedDurationMillis` = ? WHERE `routeId` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: SavedRouteEntity) {
        statement.bindLong(1, entity.routeId)
        statement.bindText(2, entity.destinationName)
        val _tmpMapDestinationName: String? = entity.mapDestinationName
        if (_tmpMapDestinationName == null) {
          statement.bindNull(3)
        } else {
          statement.bindText(3, _tmpMapDestinationName)
        }
        statement.bindDouble(4, entity.destinationLat)
        statement.bindDouble(5, entity.destinationLng)
        val _tmpTargetTime: Long? = entity.targetTime
        if (_tmpTargetTime == null) {
          statement.bindNull(6)
        } else {
          statement.bindLong(6, _tmpTargetTime)
        }
        statement.bindLong(7, entity.dateSaved)
        statement.bindLong(8, entity.lastTakenTimestamp)
        val _tmp: String = __converters.fromTransportMode(entity.transportMode)
        statement.bindText(9, _tmp)
        val _tmp_1: String = __converters.fromAlarmSettings(entity.alarmSettings)
        statement.bindText(10, _tmp_1)
        val _tmpRouteGeoJson: String? = entity.routeGeoJson
        if (_tmpRouteGeoJson == null) {
          statement.bindNull(11)
        } else {
          statement.bindText(11, _tmpRouteGeoJson)
        }
        statement.bindDouble(12, entity.actualDistanceMeters)
        statement.bindLong(13, entity.estimatedDurationMillis)
        statement.bindLong(14, entity.routeId)
      }
    }
  }

  public override suspend fun insertSavedRoute(route: SavedRouteEntity): Long =
      performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfSavedRouteEntity.insertAndReturnId(_connection, route)
    _result
  }

  public override suspend fun insertBreadcrumbs(breadcrumbs: List<RouteBreadcrumbEntity>):
      List<Long> = performSuspending(__db, false, true) { _connection ->
    val _result: List<Long> =
        __insertAdapterOfRouteBreadcrumbEntity.insertAndReturnIdsList(_connection, breadcrumbs)
    _result
  }

  public override suspend fun insertLegs(legs: List<JourneyLegEntity>): List<Long> =
      performSuspending(__db, false, true) { _connection ->
    val _result: List<Long> = __insertAdapterOfJourneyLegEntity.insertAndReturnIdsList(_connection,
        legs)
    _result
  }

  public override suspend fun deleteRoute(route: SavedRouteEntity): Int = performSuspending(__db,
      false, true) { _connection ->
    var _result: Int = 0
    _result += __deleteAdapterOfSavedRouteEntity.handle(_connection, route)
    _result
  }

  public override suspend fun updateSavedRoute(route: SavedRouteEntity): Int =
      performSuspending(__db, false, true) { _connection ->
    var _result: Int = 0
    _result += __updateAdapterOfSavedRouteEntity.handle(_connection, route)
    _result
  }

  public override fun getAllSavedRoutes(): Flow<List<SavedRouteEntity>> {
    val _sql: String = "SELECT * FROM saved_routes ORDER BY dateSaved DESC"
    return createFlow(__db, false, arrayOf("saved_routes")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfRouteId: Int = getColumnIndexOrThrow(_stmt, "routeId")
        val _columnIndexOfDestinationName: Int = getColumnIndexOrThrow(_stmt, "destinationName")
        val _columnIndexOfMapDestinationName: Int = getColumnIndexOrThrow(_stmt,
            "mapDestinationName")
        val _columnIndexOfDestinationLat: Int = getColumnIndexOrThrow(_stmt, "destinationLat")
        val _columnIndexOfDestinationLng: Int = getColumnIndexOrThrow(_stmt, "destinationLng")
        val _columnIndexOfTargetTime: Int = getColumnIndexOrThrow(_stmt, "targetTime")
        val _columnIndexOfDateSaved: Int = getColumnIndexOrThrow(_stmt, "dateSaved")
        val _columnIndexOfLastTakenTimestamp: Int = getColumnIndexOrThrow(_stmt,
            "lastTakenTimestamp")
        val _columnIndexOfTransportMode: Int = getColumnIndexOrThrow(_stmt, "transportMode")
        val _columnIndexOfAlarmSettings: Int = getColumnIndexOrThrow(_stmt, "alarmSettings")
        val _columnIndexOfRouteGeoJson: Int = getColumnIndexOrThrow(_stmt, "routeGeoJson")
        val _columnIndexOfActualDistanceMeters: Int = getColumnIndexOrThrow(_stmt,
            "actualDistanceMeters")
        val _columnIndexOfEstimatedDurationMillis: Int = getColumnIndexOrThrow(_stmt,
            "estimatedDurationMillis")
        val _result: MutableList<SavedRouteEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: SavedRouteEntity
          val _tmpRouteId: Long
          _tmpRouteId = _stmt.getLong(_columnIndexOfRouteId)
          val _tmpDestinationName: String
          _tmpDestinationName = _stmt.getText(_columnIndexOfDestinationName)
          val _tmpMapDestinationName: String?
          if (_stmt.isNull(_columnIndexOfMapDestinationName)) {
            _tmpMapDestinationName = null
          } else {
            _tmpMapDestinationName = _stmt.getText(_columnIndexOfMapDestinationName)
          }
          val _tmpDestinationLat: Double
          _tmpDestinationLat = _stmt.getDouble(_columnIndexOfDestinationLat)
          val _tmpDestinationLng: Double
          _tmpDestinationLng = _stmt.getDouble(_columnIndexOfDestinationLng)
          val _tmpTargetTime: Long?
          if (_stmt.isNull(_columnIndexOfTargetTime)) {
            _tmpTargetTime = null
          } else {
            _tmpTargetTime = _stmt.getLong(_columnIndexOfTargetTime)
          }
          val _tmpDateSaved: Long
          _tmpDateSaved = _stmt.getLong(_columnIndexOfDateSaved)
          val _tmpLastTakenTimestamp: Long
          _tmpLastTakenTimestamp = _stmt.getLong(_columnIndexOfLastTakenTimestamp)
          val _tmpTransportMode: TransportMode
          val _tmp: String
          _tmp = _stmt.getText(_columnIndexOfTransportMode)
          _tmpTransportMode = __converters.toTransportMode(_tmp)
          val _tmpAlarmSettings: AlarmSettings
          val _tmp_1: String
          _tmp_1 = _stmt.getText(_columnIndexOfAlarmSettings)
          _tmpAlarmSettings = __converters.toAlarmSettings(_tmp_1)
          val _tmpRouteGeoJson: String?
          if (_stmt.isNull(_columnIndexOfRouteGeoJson)) {
            _tmpRouteGeoJson = null
          } else {
            _tmpRouteGeoJson = _stmt.getText(_columnIndexOfRouteGeoJson)
          }
          val _tmpActualDistanceMeters: Double
          _tmpActualDistanceMeters = _stmt.getDouble(_columnIndexOfActualDistanceMeters)
          val _tmpEstimatedDurationMillis: Long
          _tmpEstimatedDurationMillis = _stmt.getLong(_columnIndexOfEstimatedDurationMillis)
          _item =
              SavedRouteEntity(_tmpRouteId,_tmpDestinationName,_tmpMapDestinationName,_tmpDestinationLat,_tmpDestinationLng,_tmpTargetTime,_tmpDateSaved,_tmpLastTakenTimestamp,_tmpTransportMode,_tmpAlarmSettings,_tmpRouteGeoJson,_tmpActualDistanceMeters,_tmpEstimatedDurationMillis)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getBreadcrumbsForRoute(routeId: Long): Flow<List<RouteBreadcrumbEntity>> {
    val _sql: String = "SELECT * FROM route_breadcrumbs WHERE routeId = ? ORDER BY timestamp ASC"
    return createFlow(__db, false, arrayOf("route_breadcrumbs")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, routeId)
        val _columnIndexOfPointId: Int = getColumnIndexOrThrow(_stmt, "pointId")
        val _columnIndexOfHistoryId: Int = getColumnIndexOrThrow(_stmt, "historyId")
        val _columnIndexOfRouteId: Int = getColumnIndexOrThrow(_stmt, "routeId")
        val _columnIndexOfLatitude: Int = getColumnIndexOrThrow(_stmt, "latitude")
        val _columnIndexOfLongitude: Int = getColumnIndexOrThrow(_stmt, "longitude")
        val _columnIndexOfSpeed: Int = getColumnIndexOrThrow(_stmt, "speed")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _result: MutableList<RouteBreadcrumbEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: RouteBreadcrumbEntity
          val _tmpPointId: Long
          _tmpPointId = _stmt.getLong(_columnIndexOfPointId)
          val _tmpHistoryId: Long?
          if (_stmt.isNull(_columnIndexOfHistoryId)) {
            _tmpHistoryId = null
          } else {
            _tmpHistoryId = _stmt.getLong(_columnIndexOfHistoryId)
          }
          val _tmpRouteId: Long?
          if (_stmt.isNull(_columnIndexOfRouteId)) {
            _tmpRouteId = null
          } else {
            _tmpRouteId = _stmt.getLong(_columnIndexOfRouteId)
          }
          val _tmpLatitude: Double
          _tmpLatitude = _stmt.getDouble(_columnIndexOfLatitude)
          val _tmpLongitude: Double
          _tmpLongitude = _stmt.getDouble(_columnIndexOfLongitude)
          val _tmpSpeed: Float
          _tmpSpeed = _stmt.getDouble(_columnIndexOfSpeed).toFloat()
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          _item =
              RouteBreadcrumbEntity(_tmpPointId,_tmpHistoryId,_tmpRouteId,_tmpLatitude,_tmpLongitude,_tmpSpeed,_tmpTimestamp)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getLegsForRoute(routeId: Long): Flow<List<JourneyLegEntity>> {
    val _sql: String = "SELECT * FROM journey_legs WHERE routeId = ? ORDER BY sequenceIndex ASC"
    return createFlow(__db, false, arrayOf("journey_legs")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, routeId)
        val _columnIndexOfLegId: Int = getColumnIndexOrThrow(_stmt, "legId")
        val _columnIndexOfRouteId: Int = getColumnIndexOrThrow(_stmt, "routeId")
        val _columnIndexOfHistoryId: Int = getColumnIndexOrThrow(_stmt, "historyId")
        val _columnIndexOfSequenceIndex: Int = getColumnIndexOrThrow(_stmt, "sequenceIndex")
        val _columnIndexOfMode: Int = getColumnIndexOrThrow(_stmt, "mode")
        val _columnIndexOfGeometry: Int = getColumnIndexOrThrow(_stmt, "geometry")
        val _columnIndexOfStartName: Int = getColumnIndexOrThrow(_stmt, "startName")
        val _columnIndexOfEndName: Int = getColumnIndexOrThrow(_stmt, "endName")
        val _columnIndexOfStartLat: Int = getColumnIndexOrThrow(_stmt, "startLat")
        val _columnIndexOfStartLng: Int = getColumnIndexOrThrow(_stmt, "startLng")
        val _columnIndexOfEndLat: Int = getColumnIndexOrThrow(_stmt, "endLat")
        val _columnIndexOfEndLng: Int = getColumnIndexOrThrow(_stmt, "endLng")
        val _columnIndexOfDistanceMeters: Int = getColumnIndexOrThrow(_stmt, "distanceMeters")
        val _columnIndexOfDurationMillis: Int = getColumnIndexOrThrow(_stmt, "durationMillis")
        val _columnIndexOfLineName: Int = getColumnIndexOrThrow(_stmt, "lineName")
        val _columnIndexOfColor: Int = getColumnIndexOrThrow(_stmt, "color")
        val _result: MutableList<JourneyLegEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: JourneyLegEntity
          val _tmpLegId: Long
          _tmpLegId = _stmt.getLong(_columnIndexOfLegId)
          val _tmpRouteId: Long?
          if (_stmt.isNull(_columnIndexOfRouteId)) {
            _tmpRouteId = null
          } else {
            _tmpRouteId = _stmt.getLong(_columnIndexOfRouteId)
          }
          val _tmpHistoryId: Long?
          if (_stmt.isNull(_columnIndexOfHistoryId)) {
            _tmpHistoryId = null
          } else {
            _tmpHistoryId = _stmt.getLong(_columnIndexOfHistoryId)
          }
          val _tmpSequenceIndex: Int
          _tmpSequenceIndex = _stmt.getLong(_columnIndexOfSequenceIndex).toInt()
          val _tmpMode: TransportMode
          val _tmp: String
          _tmp = _stmt.getText(_columnIndexOfMode)
          _tmpMode = __converters.toTransportMode(_tmp)
          val _tmpGeometry: String
          _tmpGeometry = _stmt.getText(_columnIndexOfGeometry)
          val _tmpStartName: String?
          if (_stmt.isNull(_columnIndexOfStartName)) {
            _tmpStartName = null
          } else {
            _tmpStartName = _stmt.getText(_columnIndexOfStartName)
          }
          val _tmpEndName: String?
          if (_stmt.isNull(_columnIndexOfEndName)) {
            _tmpEndName = null
          } else {
            _tmpEndName = _stmt.getText(_columnIndexOfEndName)
          }
          val _tmpStartLat: Double
          _tmpStartLat = _stmt.getDouble(_columnIndexOfStartLat)
          val _tmpStartLng: Double
          _tmpStartLng = _stmt.getDouble(_columnIndexOfStartLng)
          val _tmpEndLat: Double
          _tmpEndLat = _stmt.getDouble(_columnIndexOfEndLat)
          val _tmpEndLng: Double
          _tmpEndLng = _stmt.getDouble(_columnIndexOfEndLng)
          val _tmpDistanceMeters: Double
          _tmpDistanceMeters = _stmt.getDouble(_columnIndexOfDistanceMeters)
          val _tmpDurationMillis: Long
          _tmpDurationMillis = _stmt.getLong(_columnIndexOfDurationMillis)
          val _tmpLineName: String?
          if (_stmt.isNull(_columnIndexOfLineName)) {
            _tmpLineName = null
          } else {
            _tmpLineName = _stmt.getText(_columnIndexOfLineName)
          }
          val _tmpColor: String?
          if (_stmt.isNull(_columnIndexOfColor)) {
            _tmpColor = null
          } else {
            _tmpColor = _stmt.getText(_columnIndexOfColor)
          }
          _item =
              JourneyLegEntity(_tmpLegId,_tmpRouteId,_tmpHistoryId,_tmpSequenceIndex,_tmpMode,_tmpGeometry,_tmpStartName,_tmpEndName,_tmpStartLat,_tmpStartLng,_tmpEndLat,_tmpEndLng,_tmpDistanceMeters,_tmpDurationMillis,_tmpLineName,_tmpColor)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAllRoutes(): Int {
    val _sql: String = "DELETE FROM saved_routes"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
        getTotalChangedRows(_connection)
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
