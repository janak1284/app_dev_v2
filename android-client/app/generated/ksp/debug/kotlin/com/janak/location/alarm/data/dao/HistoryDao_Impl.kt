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
import com.janak.location.alarm.`data`.entity.JourneyHistoryEntity
import com.janak.location.alarm.`data`.entity.JourneyLegEntity
import com.janak.location.alarm.`data`.entity.RouteBreadcrumbEntity
import com.janak.location.alarm.model.AlarmSettings
import com.janak.location.alarm.model.TransportMode
import javax.`annotation`.processing.Generated
import kotlin.Double
import kotlin.Float
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class HistoryDao_Impl(
  __db: RoomDatabase,
) : HistoryDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfJourneyHistoryEntity: EntityInsertAdapter<JourneyHistoryEntity>

  private val __converters: Converters = Converters()

  private val __insertAdapterOfRouteBreadcrumbEntity: EntityInsertAdapter<RouteBreadcrumbEntity>

  private val __insertAdapterOfJourneyLegEntity: EntityInsertAdapter<JourneyLegEntity>

  private val __deleteAdapterOfJourneyHistoryEntity:
      EntityDeleteOrUpdateAdapter<JourneyHistoryEntity>
  init {
    this.__db = __db
    this.__insertAdapterOfJourneyHistoryEntity = object :
        EntityInsertAdapter<JourneyHistoryEntity>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `journey_history` (`historyId`,`routeId`,`startingPointLat`,`startingPointLng`,`destinationLat`,`destinationLng`,`destinationName`,`mapDestinationName`,`alarmConfigAtTime`,`timestamp`,`durationMillis`,`actualDistanceMeters`,`actualRouteGeoJson`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: JourneyHistoryEntity) {
        statement.bindLong(1, entity.historyId)
        val _tmpRouteId: Long? = entity.routeId
        if (_tmpRouteId == null) {
          statement.bindNull(2)
        } else {
          statement.bindLong(2, _tmpRouteId)
        }
        statement.bindDouble(3, entity.startingPointLat)
        statement.bindDouble(4, entity.startingPointLng)
        statement.bindDouble(5, entity.destinationLat)
        statement.bindDouble(6, entity.destinationLng)
        statement.bindText(7, entity.destinationName)
        val _tmpMapDestinationName: String? = entity.mapDestinationName
        if (_tmpMapDestinationName == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpMapDestinationName)
        }
        val _tmp: String = __converters.fromAlarmSettings(entity.alarmConfigAtTime)
        statement.bindText(9, _tmp)
        statement.bindLong(10, entity.timestamp)
        statement.bindLong(11, entity.durationMillis)
        statement.bindDouble(12, entity.actualDistanceMeters)
        val _tmpActualRouteGeoJson: String? = entity.actualRouteGeoJson
        if (_tmpActualRouteGeoJson == null) {
          statement.bindNull(13)
        } else {
          statement.bindText(13, _tmpActualRouteGeoJson)
        }
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
    this.__deleteAdapterOfJourneyHistoryEntity = object :
        EntityDeleteOrUpdateAdapter<JourneyHistoryEntity>() {
      protected override fun createQuery(): String =
          "DELETE FROM `journey_history` WHERE `historyId` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: JourneyHistoryEntity) {
        statement.bindLong(1, entity.historyId)
      }
    }
  }

  public override suspend fun insertJourney(journey: JourneyHistoryEntity): Long =
      performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfJourneyHistoryEntity.insertAndReturnId(_connection,
        journey)
    _result
  }

  public override suspend fun insertBreadcrumbs(breadcrumbs: List<RouteBreadcrumbEntity>):
      List<Long> = performSuspending(__db, false, true) { _connection ->
    val _result: List<Long> =
        __insertAdapterOfRouteBreadcrumbEntity.insertAndReturnIdsList(_connection, breadcrumbs)
    _result
  }

  public override suspend fun insertBreadcrumb(breadcrumb: RouteBreadcrumbEntity): Long =
      performSuspending(__db, false, true) { _connection ->
    val _result: Long = __insertAdapterOfRouteBreadcrumbEntity.insertAndReturnId(_connection,
        breadcrumb)
    _result
  }

  public override suspend fun insertLegs(legs: List<JourneyLegEntity>): List<Long> =
      performSuspending(__db, false, true) { _connection ->
    val _result: List<Long> = __insertAdapterOfJourneyLegEntity.insertAndReturnIdsList(_connection,
        legs)
    _result
  }

  public override suspend fun deleteJourneys(journeys: List<JourneyHistoryEntity>): Unit =
      performSuspending(__db, false, true) { _connection ->
    __deleteAdapterOfJourneyHistoryEntity.handleMultiple(_connection, journeys)
  }

  public override suspend fun getBreadcrumbsForHistorySync(historyId: Long):
      List<RouteBreadcrumbEntity> {
    val _sql: String = "SELECT * FROM route_breadcrumbs WHERE historyId = ? ORDER BY timestamp ASC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, historyId)
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

  public override fun getAllJourneys(): Flow<List<JourneyHistoryEntity>> {
    val _sql: String = "SELECT * FROM journey_history ORDER BY timestamp DESC"
    return createFlow(__db, false, arrayOf("journey_history")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfHistoryId: Int = getColumnIndexOrThrow(_stmt, "historyId")
        val _columnIndexOfRouteId: Int = getColumnIndexOrThrow(_stmt, "routeId")
        val _columnIndexOfStartingPointLat: Int = getColumnIndexOrThrow(_stmt, "startingPointLat")
        val _columnIndexOfStartingPointLng: Int = getColumnIndexOrThrow(_stmt, "startingPointLng")
        val _columnIndexOfDestinationLat: Int = getColumnIndexOrThrow(_stmt, "destinationLat")
        val _columnIndexOfDestinationLng: Int = getColumnIndexOrThrow(_stmt, "destinationLng")
        val _columnIndexOfDestinationName: Int = getColumnIndexOrThrow(_stmt, "destinationName")
        val _columnIndexOfMapDestinationName: Int = getColumnIndexOrThrow(_stmt,
            "mapDestinationName")
        val _columnIndexOfAlarmConfigAtTime: Int = getColumnIndexOrThrow(_stmt, "alarmConfigAtTime")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfDurationMillis: Int = getColumnIndexOrThrow(_stmt, "durationMillis")
        val _columnIndexOfActualDistanceMeters: Int = getColumnIndexOrThrow(_stmt,
            "actualDistanceMeters")
        val _columnIndexOfActualRouteGeoJson: Int = getColumnIndexOrThrow(_stmt,
            "actualRouteGeoJson")
        val _result: MutableList<JourneyHistoryEntity> = mutableListOf()
        while (_stmt.step()) {
          val _item: JourneyHistoryEntity
          val _tmpHistoryId: Long
          _tmpHistoryId = _stmt.getLong(_columnIndexOfHistoryId)
          val _tmpRouteId: Long?
          if (_stmt.isNull(_columnIndexOfRouteId)) {
            _tmpRouteId = null
          } else {
            _tmpRouteId = _stmt.getLong(_columnIndexOfRouteId)
          }
          val _tmpStartingPointLat: Double
          _tmpStartingPointLat = _stmt.getDouble(_columnIndexOfStartingPointLat)
          val _tmpStartingPointLng: Double
          _tmpStartingPointLng = _stmt.getDouble(_columnIndexOfStartingPointLng)
          val _tmpDestinationLat: Double
          _tmpDestinationLat = _stmt.getDouble(_columnIndexOfDestinationLat)
          val _tmpDestinationLng: Double
          _tmpDestinationLng = _stmt.getDouble(_columnIndexOfDestinationLng)
          val _tmpDestinationName: String
          _tmpDestinationName = _stmt.getText(_columnIndexOfDestinationName)
          val _tmpMapDestinationName: String?
          if (_stmt.isNull(_columnIndexOfMapDestinationName)) {
            _tmpMapDestinationName = null
          } else {
            _tmpMapDestinationName = _stmt.getText(_columnIndexOfMapDestinationName)
          }
          val _tmpAlarmConfigAtTime: AlarmSettings
          val _tmp: String
          _tmp = _stmt.getText(_columnIndexOfAlarmConfigAtTime)
          _tmpAlarmConfigAtTime = __converters.toAlarmSettings(_tmp)
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpDurationMillis: Long
          _tmpDurationMillis = _stmt.getLong(_columnIndexOfDurationMillis)
          val _tmpActualDistanceMeters: Double
          _tmpActualDistanceMeters = _stmt.getDouble(_columnIndexOfActualDistanceMeters)
          val _tmpActualRouteGeoJson: String?
          if (_stmt.isNull(_columnIndexOfActualRouteGeoJson)) {
            _tmpActualRouteGeoJson = null
          } else {
            _tmpActualRouteGeoJson = _stmt.getText(_columnIndexOfActualRouteGeoJson)
          }
          _item =
              JourneyHistoryEntity(_tmpHistoryId,_tmpRouteId,_tmpStartingPointLat,_tmpStartingPointLng,_tmpDestinationLat,_tmpDestinationLng,_tmpDestinationName,_tmpMapDestinationName,_tmpAlarmConfigAtTime,_tmpTimestamp,_tmpDurationMillis,_tmpActualDistanceMeters,_tmpActualRouteGeoJson)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getBreadcrumbsForHistory(historyId: Long): Flow<List<RouteBreadcrumbEntity>> {
    val _sql: String = "SELECT * FROM route_breadcrumbs WHERE historyId = ? ORDER BY timestamp ASC"
    return createFlow(__db, false, arrayOf("route_breadcrumbs")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, historyId)
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

  public override fun getLegsForHistory(historyId: Long): Flow<List<JourneyLegEntity>> {
    val _sql: String = "SELECT * FROM journey_legs WHERE historyId = ? ORDER BY sequenceIndex ASC"
    return createFlow(__db, false, arrayOf("journey_legs")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, historyId)
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

  public override suspend fun getLatestJourney(): JourneyHistoryEntity? {
    val _sql: String = "SELECT * FROM journey_history ORDER BY timestamp DESC LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfHistoryId: Int = getColumnIndexOrThrow(_stmt, "historyId")
        val _columnIndexOfRouteId: Int = getColumnIndexOrThrow(_stmt, "routeId")
        val _columnIndexOfStartingPointLat: Int = getColumnIndexOrThrow(_stmt, "startingPointLat")
        val _columnIndexOfStartingPointLng: Int = getColumnIndexOrThrow(_stmt, "startingPointLng")
        val _columnIndexOfDestinationLat: Int = getColumnIndexOrThrow(_stmt, "destinationLat")
        val _columnIndexOfDestinationLng: Int = getColumnIndexOrThrow(_stmt, "destinationLng")
        val _columnIndexOfDestinationName: Int = getColumnIndexOrThrow(_stmt, "destinationName")
        val _columnIndexOfMapDestinationName: Int = getColumnIndexOrThrow(_stmt,
            "mapDestinationName")
        val _columnIndexOfAlarmConfigAtTime: Int = getColumnIndexOrThrow(_stmt, "alarmConfigAtTime")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfDurationMillis: Int = getColumnIndexOrThrow(_stmt, "durationMillis")
        val _columnIndexOfActualDistanceMeters: Int = getColumnIndexOrThrow(_stmt,
            "actualDistanceMeters")
        val _columnIndexOfActualRouteGeoJson: Int = getColumnIndexOrThrow(_stmt,
            "actualRouteGeoJson")
        val _result: JourneyHistoryEntity?
        if (_stmt.step()) {
          val _tmpHistoryId: Long
          _tmpHistoryId = _stmt.getLong(_columnIndexOfHistoryId)
          val _tmpRouteId: Long?
          if (_stmt.isNull(_columnIndexOfRouteId)) {
            _tmpRouteId = null
          } else {
            _tmpRouteId = _stmt.getLong(_columnIndexOfRouteId)
          }
          val _tmpStartingPointLat: Double
          _tmpStartingPointLat = _stmt.getDouble(_columnIndexOfStartingPointLat)
          val _tmpStartingPointLng: Double
          _tmpStartingPointLng = _stmt.getDouble(_columnIndexOfStartingPointLng)
          val _tmpDestinationLat: Double
          _tmpDestinationLat = _stmt.getDouble(_columnIndexOfDestinationLat)
          val _tmpDestinationLng: Double
          _tmpDestinationLng = _stmt.getDouble(_columnIndexOfDestinationLng)
          val _tmpDestinationName: String
          _tmpDestinationName = _stmt.getText(_columnIndexOfDestinationName)
          val _tmpMapDestinationName: String?
          if (_stmt.isNull(_columnIndexOfMapDestinationName)) {
            _tmpMapDestinationName = null
          } else {
            _tmpMapDestinationName = _stmt.getText(_columnIndexOfMapDestinationName)
          }
          val _tmpAlarmConfigAtTime: AlarmSettings
          val _tmp: String
          _tmp = _stmt.getText(_columnIndexOfAlarmConfigAtTime)
          _tmpAlarmConfigAtTime = __converters.toAlarmSettings(_tmp)
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpDurationMillis: Long
          _tmpDurationMillis = _stmt.getLong(_columnIndexOfDurationMillis)
          val _tmpActualDistanceMeters: Double
          _tmpActualDistanceMeters = _stmt.getDouble(_columnIndexOfActualDistanceMeters)
          val _tmpActualRouteGeoJson: String?
          if (_stmt.isNull(_columnIndexOfActualRouteGeoJson)) {
            _tmpActualRouteGeoJson = null
          } else {
            _tmpActualRouteGeoJson = _stmt.getText(_columnIndexOfActualRouteGeoJson)
          }
          _result =
              JourneyHistoryEntity(_tmpHistoryId,_tmpRouteId,_tmpStartingPointLat,_tmpStartingPointLng,_tmpDestinationLat,_tmpDestinationLng,_tmpDestinationName,_tmpMapDestinationName,_tmpAlarmConfigAtTime,_tmpTimestamp,_tmpDurationMillis,_tmpActualDistanceMeters,_tmpActualRouteGeoJson)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updateJourneySummary(
    id: Long,
    startLat: Double,
    startLng: Double,
    distance: Double,
    duration: Long,
    geoJson: String?,
  ) {
    val _sql: String = """
        |
        |        UPDATE journey_history 
        |        SET startingPointLat = ?, 
        |            startingPointLng = ?,
        |            actualDistanceMeters = ?,
        |            durationMillis = ?,
        |            actualRouteGeoJson = ?
        |        WHERE historyId = ?
        |    
        """.trimMargin()
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindDouble(_argIndex, startLat)
        _argIndex = 2
        _stmt.bindDouble(_argIndex, startLng)
        _argIndex = 3
        _stmt.bindDouble(_argIndex, distance)
        _argIndex = 4
        _stmt.bindLong(_argIndex, duration)
        _argIndex = 5
        if (geoJson == null) {
          _stmt.bindNull(_argIndex)
        } else {
          _stmt.bindText(_argIndex, geoJson)
        }
        _argIndex = 6
        _stmt.bindLong(_argIndex, id)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun pruneHistory(limit: Int) {
    val _sql: String =
        "DELETE FROM journey_history WHERE historyId NOT IN (SELECT historyId FROM journey_history ORDER BY timestamp DESC LIMIT ?)"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, limit.toLong())
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAllHistory(): Int {
    val _sql: String = "DELETE FROM journey_history"
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
