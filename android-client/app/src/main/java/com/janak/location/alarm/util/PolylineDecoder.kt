package com.janak.location.alarm.util

import com.mapbox.geojson.Point
import kotlin.math.roundToInt

object PolylineDecoder {
    fun decode(encoded: String): List<Point> {
        val poly = ArrayList<Point>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = Point.fromLngLat(
                (lng.toDouble() / 1E5), 
                (lat.toDouble() / 1E5)
            )
            poly.add(p)
        }
        return poly
    }

    fun encode(path: List<Point>): String {
        val result = StringBuilder()
        var prevLat = 0
        var prevLng = 0

        for (point in path) {
            val lat = (point.latitude() * 1e5).roundToInt()
            val lng = (point.longitude() * 1e5).roundToInt()

            encodeValue(lat - prevLat, result)
            encodeValue(lng - prevLng, result)

            prevLat = lat
            prevLng = lng
        }

        return result.toString()
    }

    private fun encodeValue(v: Int, result: StringBuilder) {
        var value = v
        value = if (value < 0) (value shl 1).inv() else value shl 1
        while (value >= 0x20) {
            result.append(((0x20 or (value and 0x1f)) + 63).toChar())
            value = value shr 5
        }
        result.append((value + 63).toChar())
    }
}
