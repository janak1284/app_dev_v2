package com.janak.location.alarm.location

import android.content.Context
import android.location.Location
import android.os.SystemClock
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

import kotlinx.coroutines.flow.first
import com.janak.location.alarm.data.SettingsDataStore

class GpxMockLocationRepositoryImpl(
    private val context: Context,
    private val settingsDataStore: SettingsDataStore,
    private val fileName: String
) : LocationRepository {

    override fun getLocationUpdates(): Flow<Location> = flow {
        val inputStream = try {
            context.assets.open(fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            return@flow
        }
        val parser = Xml.newPullParser()
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        var currentLat: Double? = null
        var currentLon: Double? = null
        var previousTimeMs: Long? = null
        var previousLocation: Location? = null
        
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "trkpt" -> {
                            currentLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                            currentLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                        }
                        "time" -> {
                            if (currentLat != null && currentLon != null) {
                                val timeString = parser.nextText()
                                val currentTimeMs = try {
                                    dateFormat.parse(timeString)?.time
                                } catch (e: Exception) {
                                    null
                                }

                                if (currentTimeMs != null) {
                                    val location = Location("RoadwayDemoMockProvider").apply {
                                        latitude = currentLat!!
                                        longitude = currentLon!!
                                        time = System.currentTimeMillis()
                                        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                                        accuracy = 5.0f
                                    }

                                    if (previousTimeMs == null) {
                                        emit(location)
                                        settingsDataStore.demoSettingsFlow.first { it.isDemoPlaybackActive }
                                    } else {
                                        val timeDeltaMs = currentTimeMs - previousTimeMs!!
                                        if (timeDeltaMs > 0) {
                                            val dist = previousLocation?.distanceTo(location) ?: 0f
                                            val speedMps = dist / (timeDeltaMs / 1000f)
                                            location.speed = speedMps
                                            if (previousLocation != null) {
                                                location.bearing = previousLocation!!.bearingTo(location)
                                            }
                                            delay(timeDeltaMs / 30L)
                                        }
                                        emit(location)
                                    }
                                    
                                    previousTimeMs = currentTimeMs
                                    previousLocation = location
                                    
                                    currentLat = null
                                    currentLon = null
                                }
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        inputStream.close()
    }.flowOn(Dispatchers.IO)

    override fun updateInterval(intervalMillis: Long, priority: Int) {
        // Ignored in mock mode
    }
}
