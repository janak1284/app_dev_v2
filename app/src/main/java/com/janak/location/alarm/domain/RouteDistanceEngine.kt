package com.janak.location.alarm.domain

import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import com.mapbox.turf.TurfMisc

class RouteDistanceEngine {

    private var averageSpeedMps: Double = 0.0
    private val alpha = 0.2 // Smoothing factor for EMA (higher = more weight to recent speed)

    /**
     * Calculates the distance remaining along a polyline from the user's current snapped position.
     * 
     * @param route The OSRM LineString representing the planned path.
     * @param userLocation The user's current raw GPS point.
     * @return Distance in meters along the route.
     */
    fun calculateRemainingDistance(route: LineString, userLocation: Point): Double {
        // 1. Find the point on the line nearest to the user (Snapping)
        val feature = TurfMisc.nearestPointOnLine(userLocation, route.coordinates())
        val snappedPoint = feature.geometry() as? Point ?: return Double.MAX_VALUE
        
        // 2. Slice the line from the snapped point to the end
        val slicedLine = TurfMisc.lineSlice(snappedPoint, route.coordinates().last(), route)
        
        // 3. Measure the length of the sliced line in meters
        return TurfMeasurement.length(slicedLine, TurfConstants.UNIT_METERS)
    }

    /**
     * Calculates the cross-track distance (perpendicular deviation) from the route.
     * 
     * @param route The planned OSRM path.
     * @param userLocation The raw GPS location.
     * @return Deviation in meters.
     */
    fun calculateDeviation(route: LineString, userLocation: Point): Double {
        val feature = TurfMisc.nearestPointOnLine(userLocation, route.coordinates())
        val snappedPoint = feature.geometry() as? Point ?: return Double.MAX_VALUE
        
        // Distance between raw GPS and snapped point on the path
        return TurfMeasurement.distance(userLocation, snappedPoint, TurfConstants.UNIT_METERS)
    }

    /**
     * Updates the sliding average speed using Exponential Moving Average (EMA).
     * 
     * @param currentSpeedMps The latest speed from GPS in meters per second.
     * @return The updated average speed.
     */
    fun updateAverageSpeed(currentSpeedMps: Double): Double {
        if (averageSpeedMps == 0.0 && currentSpeedMps > 0) {
            averageSpeedMps = currentSpeedMps
        } else if (averageSpeedMps > 0) {
            averageSpeedMps = (alpha * currentSpeedMps) + (1 - alpha) * averageSpeedMps
        }
        return averageSpeedMps
    }

    /**
     * Predicts the dynamic ETA in minutes, calibrated by the ratio of user speed vs OSRM expected speed.
     * 
     * @param remainingDistanceMeters Distance left along the route.
     * @param expectedSpeedMps The speed OSRM expects for this road segment.
     * @return ETA in minutes.
     */
    fun calculateCalibratedETA(remainingDistanceMeters: Double, expectedSpeedMps: Double): Double {
        if (averageSpeedMps <= 0.5) { // User is stationary
            return Double.MAX_VALUE
        }

        return if (expectedSpeedMps > 0) {
            // Speed Ratio = Current User Speed / Road's Expected Speed
            val speedRatio = averageSpeedMps / expectedSpeedMps
            
            // Expected Duration = Remaining Distance / Road's Expected Speed
            val baseDurationSeconds = remainingDistanceMeters / expectedSpeedMps
            
            // Calibrated ETA = Base Duration / Speed Ratio
            (baseDurationSeconds / speedRatio) / 60.0
        } else {
            // Fallback to simple dynamic ETA
            (remainingDistanceMeters / averageSpeedMps) / 60.0
        }
    }

    /**
     * Predicts the simple dynamic ETA in minutes.
     */
    fun calculateDynamicETA(remainingDistanceMeters: Double): Double {
        if (averageSpeedMps <= 0.5) {
            return Double.MAX_VALUE
        }
        val secondsRemaining = remainingDistanceMeters / averageSpeedMps
        return secondsRemaining / 60.0
    }

    fun resetStats() {
        averageSpeedMps = 0.0
    }

    /**
     * Calculates the total distance of a traversed path from a list of locations.
     */
    fun calculateTotalDistance(locations: List<android.location.Location>): Double {
        if (locations.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until locations.size - 1) {
            val results = FloatArray(1)
            android.location.Location.distanceBetween(
                locations[i].latitude, locations[i].longitude,
                locations[i + 1].latitude, locations[i + 1].longitude,
                results
            )
            total += results[0]
        }
        return total
    }
}
