package com.janak.location.alarm.util

import com.mapbox.geojson.Point

object ValhallaUtil {
    /**
     * Decodes a Valhalla encoded polyline.
     * Valhalla typically uses 6 decimal places (precision = 6).
     */
    fun decodePolyline(encoded: String, precision: Int = 6): List<Point> {
        val factor = Math.pow(10.0, precision.toDouble())
        val points = mutableListOf<Point>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            points.add(Point.fromLngLat(lng.toDouble() / factor, lat.toDouble() / factor))
        }
        return points
    }
}
