package com.example.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class WeatherResult(
    val summary: String,
    val detail: String,
)

@Serializable
private data class OpenMeteoResponse(
    @SerialName("current_weather")
    val currentWeather: CurrentWeatherPayload? = null,
)

@Serializable
private data class CurrentWeatherPayload(
    val temperature: Double,
    val windspeed: Double,
)

class WeatherRepository {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchWeather(context: Context): Result<WeatherResult> = withContext(Dispatchers.IO) {
        runCatching {
            val (lat, lon) = resolveCoordinates(context)
            val endpoint =
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current_weather=true"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
            }
            try {
                val code = conn.responseCode
                if (code !in 200..299) {
                    throw IllegalStateException("Open-Meteo HTTP $code")
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val parsed = json.decodeFromString<OpenMeteoResponse>(body)
                val weather = parsed.currentWeather
                    ?: throw IllegalStateException("Sin datos de clima")
                val temp = weather.temperature.roundToInt()
                val wind = weather.windspeed.roundToInt()
                WeatherResult(
                    summary = "$temp°C · viento $wind km/h",
                    detail = "Temperatura actual de $temp°C con viento de $wind km/h.",
                )
            } finally {
                conn.disconnect()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun resolveCoordinates(context: Context): Pair<Double, Double> {
        val defaultCoords = -33.45 to -70.66
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasCoarse) return defaultCoords

        return try {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                ?: return defaultCoords
            val loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            if (loc != null) loc.latitude to loc.longitude else defaultCoords
        } catch (_: Exception) {
            defaultCoords
        }
    }
}
