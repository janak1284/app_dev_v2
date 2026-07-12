package com.janak.location.alarm.domain

import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RouteDistanceEngineTest {

    private lateinit var engine: RouteDistanceEngine

    @Before
    fun setUp() {
        engine = RouteDistanceEngine()
    }

    @Test
    fun testRemainingDistanceAndSnapping() {
        // Create a straight horizontal route along equator: (0,0) -> (0.1, 0)
        // 0.1 deg longitude at equator is approx 11,131.9 meters
        val startPoint = Point.fromLngLat(0.0, 0.0)
        val endPoint = Point.fromLngLat(0.1, 0.0)
        val route = LineString.fromLngLats(listOf(startPoint, endPoint))

        // Place user exactly halfway at (0.05, 0.0)
        val userMidPoint = Point.fromLngLat(0.05, 0.0)
        val remainingDist = engine.calculateRemainingDistance(route, userMidPoint)

        // Half remaining distance should be roughly 5,565.9 meters (+/- 50m tolerance)
        assertEquals(5565.9, remainingDist, 50.0)
    }

    @Test
    fun testEmaSpeedTracking() {
        // First update sets the initial speed directly
        val speed1 = engine.updateAverageSpeed(10.0)
        assertEquals(10.0, speed1, 0.001)

        // Second update applies EMA (alpha = 0.2): 0.2 * 20.0 + 0.8 * 10.0 = 12.0
        val speed2 = engine.updateAverageSpeed(20.0)
        assertEquals(12.0, speed2, 0.001)
    }

    @Test
    fun testCalibratedETAUnderTrafficSlowdown() {
        // Set user average speed to 10 m/s
        engine.updateAverageSpeed(10.0)

        // Suppose current segment expected speed is 20 m/s (so user is moving at 50% efficiency ratio = 0.5)
        // Suppose global expected speed is 20 m/s, and remaining distance is 1200 meters.
        // Base duration = 1200 / 20 = 60 seconds.
        // Calibrated duration = 60 / 0.5 = 120 seconds (2.0 minutes).
        val etaMinutes = engine.calculateCalibratedETA(
            remainingDistanceMeters = 1200.0,
            globalExpectedSpeedMps = 20.0,
            currentSegmentSpeedMps = 20.0
        )

        assertEquals(2.0, etaMinutes, 0.01)
    }

    @Test
    fun testSlicedRouteGeneration() {
        val startPoint = Point.fromLngLat(72.80, 18.90)
        val midPoint = Point.fromLngLat(72.85, 18.95)
        val endPoint = Point.fromLngLat(72.90, 19.00)
        val route = LineString.fromLngLats(listOf(startPoint, midPoint, endPoint))

        val userLocation = Point.fromLngLat(72.85, 18.95)
        val slicedRoute = engine.getSlicedRoute(route, userLocation)

        assertNotNull(slicedRoute)
        // Should contain points from midpoint onwards
        assertTrue(slicedRoute!!.coordinates().size >= 2)
    }

    @Test
    fun testStationaryUserReturnsMaxEta() {
        engine.updateAverageSpeed(0.2) // Stationary below threshold 0.5
        val eta = engine.calculateCalibratedETA(1000.0, 10.0, 10.0)
        assertEquals(Double.MAX_VALUE, eta, 0.0)
    }
}
