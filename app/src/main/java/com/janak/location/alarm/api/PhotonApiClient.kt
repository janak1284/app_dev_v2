package com.janak.location.alarm.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class PlaceSuggestion(
    val name: String,
    val detailedName: String,
    val lat: Double,
    val lng: Double
)

class PhotonApiClient {
    suspend fun search(query: String): List<PlaceSuggestion> = withContext(Dispatchers.IO) {
        val results = mutableListOf<PlaceSuggestion>()
        if (query.isBlank()) return@withContext results
        
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://photon.komoot.io/api/?q=$encodedQuery&limit=5")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = StringBuilder()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    response.append(line)
                }
                reader.close()
                
                val json = JSONObject(response.toString())
                val features = json.optJSONArray("features")
                if (features != null) {
                    for (i in 0 until features.length()) {
                        val feature = features.getJSONObject(i)
                        val properties = feature.optJSONObject("properties")
                        val geometry = feature.optJSONObject("geometry")
                        
                        if (properties != null && geometry != null) {
                            val coordinates = geometry.optJSONArray("coordinates")
                            if (coordinates != null && coordinates.length() >= 2) {
                                val lng = coordinates.getDouble(0)
                                val lat = coordinates.getDouble(1)
                                
                                val name = properties.optString("name", "Unknown")
                                val city = properties.optString("city", "")
                                val state = properties.optString("state", "")
                                val country = properties.optString("country", "")
                                
                                val details = listOf(city, state, country).filter { it.isNotBlank() }.joinToString(", ")
                                
                                results.add(PlaceSuggestion(name, details, lat, lng))
                            }
                        }
                    }
                }
            }
            connection.disconnect()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext results
    }
}
