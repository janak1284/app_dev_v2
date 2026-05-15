package com.janak.location.alarm.domain

import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.turf.TurfConstants
import com.mapbox.turf.TurfMeasurement
import com.mapbox.turf.TurfMisc

class RouteDistanceEngine {

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
}
