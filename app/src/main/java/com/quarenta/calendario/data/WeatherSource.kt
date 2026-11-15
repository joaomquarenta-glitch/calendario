package com.quarenta.calendario.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import androidx.core.content.ContextCompat
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/** Where the forecast comes from on the phone: the rough home position, a place's name, and a small cache. */
object WeatherSource {
    private const val FRESH_MS = 3 * 60 * 60 * 1000L

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** The forecast where the phone roughly is; empty without permission or internet. Call off the main thread. */
    fun home(context: Context): Map<LocalDate, DayWeather> {
        val (lat, lon) = homePosition(context) ?: return emptyMap()
        return forecast(context, lat, lon)
    }

    /** The forecast at a named place, falling back to home when the place can't be found. */
    fun at(context: Context, place: String?): Map<LocalDate, DayWeather> {
        val spot = place?.takeIf { it.isNotBlank() }?.let { locate(context, it) }
        return if (spot != null) forecast(context, spot.first, spot.second) else home(context)
    }

    @Suppress("MissingPermission")
    private fun homePosition(context: Context): Pair<Double, Double>? {
        val prefs = context.getSharedPreferences("forty", Context.MODE_PRIVATE)
        if (hasPermission(context)) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            val best = lm?.getProviders(true).orEmpty()
                .mapNotNull { runCatching { lm?.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
            if (best != null) {
                prefs.edit().putString("weather_home", "${best.latitude},${best.longitude}").apply()
                return best.latitude to best.longitude
            }
        }
        // The last place we knew, so the forecast stays while the phone has no fresh position.
        return prefs.getString("weather_home", null)?.split(",")?.mapNotNull { it.toDoubleOrNull() }
            ?.takeIf { it.size == 2 }?.let { it[0] to it[1] }
    }

    @Suppress("DEPRECATION")
    private fun locate(context: Context, place: String): Pair<Double, Double>? = runCatching {
        val prefs = context.getSharedPreferences("map_places", Context.MODE_PRIVATE)
        prefs.getString(place, null)?.split(",")?.mapNotNull { it.toDoubleOrNull() }?.takeIf { it.size == 2 }
            ?.let { return@runCatching it[0] to it[1] }
        if (!Geocoder.isPresent()) return@runCatching null
        val found = Geocoder(context).getFromLocationName(place, 1)?.firstOrNull() ?: return@runCatching null
        prefs.edit().putString(place, "${found.latitude},${found.longitude}").apply()
        found.latitude to found.longitude
    }.getOrNull()

    private fun forecast(context: Context, lat: Double, lon: Double): Map<LocalDate, DayWeather> {
        // About 1 km squares, so nearby places share one answer.
        val file = File(File(context.cacheDir, "weather"), "%.2f_%.2f.json".format(java.util.Locale.ROOT, lat, lon))
        val cached = file.takeIf { it.exists() }?.readText()
        if (cached != null && System.currentTimeMillis() - file.lastModified() < FRESH_MS) return Weather.parse(cached)
        val fresh = runCatching {
            val conn = URL(Weather.url(lat, lon)).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                if (conn.responseCode != 200) null else conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
        if (fresh != null && Weather.parse(fresh).isNotEmpty()) {
            file.parentFile?.mkdirs()
            file.writeText(fresh)
            return Weather.parse(fresh)
        }
        return cached?.let(Weather::parse).orEmpty()
    }
}
