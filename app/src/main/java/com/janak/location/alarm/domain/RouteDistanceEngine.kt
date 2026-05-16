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
     * Predicts the dynamic ETA in minutes.
     * 
     * @param remainingDistanceMeters Distance left along the route.
     * @return ETA in minutes.
     */
    fun calculateDynamicETA(remainingDistanceMeters: Double): Double {
        if (averageSpeedMps <= 0.5) { // User is stationary or moving very slowly (~1.8 km/h)
            return Double.MAX_VALUE
        }
        val secondsRemaining = remainingDistanceMeters / averageSpeedMps
        return secondsRemaining / 60.0
    }

    fun resetStats() {
        averageSpeedMps = 0.0
    }
}
