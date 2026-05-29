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
        return try {
            val slicedLine = TurfMisc.lineSlice(snappedPoint, route.coordinates().last(), route)
            // 3. Measure the length of the sliced line in meters
            TurfMeasurement.length(slicedLine, TurfConstants.UNIT_METERS)
        } catch (e: Exception) {
            // Slicing fails if start == stop (we are exactly at the destination point)
            0.0
        }
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
     * Finds the expected speed for the current segment the user is on.
     * 
     * @param route The planned path.
     * @param userLocation The raw GPS location.
     * @param segmentSpeeds List of speeds for each segment from OSRM annotations.
     * @return Expected speed in meters per second.
     */
    fun getCurrentSegmentSpeed(route: LineString, userLocation: Point, segmentSpeeds: List<Double>): Double {
        if (segmentSpeeds.isEmpty()) return 0.0

        val feature = TurfMisc.nearestPointOnLine(userLocation, route.coordinates())
        val properties = feature.properties() ?: return segmentSpeeds.first()

        // 'index' property in nearestPointOnLine refers to the segment start point index
        val segmentIndex = properties.get("index")?.asInt ?: 0

        return if (segmentIndex in segmentSpeeds.indices) {
            segmentSpeeds[segmentIndex]
        } else {
            segmentSpeeds.last()
        }
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
     * Predicts the dynamic ETA in minutes using the Performance Ratio model.
     * 
     * @param remainingDistanceMeters Distance left along the route.
     * @param globalExpectedSpeedMps The OSRM expected speed for the entire route (Total Dist / Total Duration).
     * @param currentSegmentSpeedMps The OSRM expected speed for the specific segment the user is currently on.
     * @return ETA in minutes.
     */
    fun calculateCalibratedETA(
        remainingDistanceMeters: Double, 
        globalExpectedSpeedMps: Double,
        currentSegmentSpeedMps: Double
    ): Double {
        if (averageSpeedMps <= 0.5) { // User is stationary
            return Double.MAX_VALUE
        }

        if (globalExpectedSpeedMps <= 0 || currentSegmentSpeedMps <= 0) {
            // Fallback to simple dynamic ETA if OSRM data is missing
            return (remainingDistanceMeters / averageSpeedMps) / 60.0
        }

        // 1. Performance Ratio: How are we doing on the CURRENT road type?
        // e.g., if we are walking 4km/h on a 5km/h path, we are at 80% efficiency (0.8)
        var performanceRatio = averageSpeedMps / currentSegmentSpeedMps
        
        // Clamp ratio to protect against extreme outliers (traffic jam vs speeding)
        performanceRatio = performanceRatio.coerceIn(0.1, 2.5)

        // 2. Base OSRM Time: How long OSRM thinks the rest of the journey takes
        val baseRemainingDurationSeconds = remainingDistanceMeters / globalExpectedSpeedMps

        // 3. Calibrated Time: Scale the OSRM prediction by our current performance ratio
        val calibratedTimeSeconds = baseRemainingDurationSeconds / performanceRatio

        return calibratedTimeSeconds / 60.0
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
