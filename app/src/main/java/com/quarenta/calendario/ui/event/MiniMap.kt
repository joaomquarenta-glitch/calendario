package com.quarenta.calendario.ui.event

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.location.Geocoder
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quarenta.calendario.BuildConfig
import com.quarenta.calendario.data.MapTiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A small map of where an event happens. Nothing shows when the place can't be found or there's no internet. */
@Composable
fun MiniMap(place: String, accent: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val map by produceState<Bitmap?>(null, place) { value = MapLoader.load(context, place) }
    val bitmap = map ?: return
    Box(
        modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(26.dp))
            .clickable { MapLoader.openDirections(context, place) },
    ) {
        Image(
            bitmap.asImageBitmap(), contentDescription = "Mapa de $place",
            contentScale = ContentScale.Crop, filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize(),
        )
        // The pin sits on the exact spot: the picture is centred on the place.
        Box(
            Modifier.align(Alignment.Center).size(34.dp).shadow(8.dp, CircleShape).clip(CircleShape).background(accent),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Place, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) }
        Text(
            place, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp).clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 6.dp),
        )
        Text(
            "© OpenStreetMap", color = Color.Black.copy(alpha = 0.7f), fontSize = 9.sp,
            modifier = Modifier.align(Alignment.BottomEnd).background(Color.White.copy(alpha = 0.7f)).padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

/** Finds a place on the map and draws the tiles around it, keeping both on the phone so it's quick next time. */
object MapLoader {
    private const val ZOOM = 16
    private const val WIDTH = 768
    private const val HEIGHT = 384

    suspend fun load(context: Context, place: String): Bitmap? = withContext(Dispatchers.IO) {
        if (place.isBlank()) return@withContext null
        runCatching {
            val (lat, lon) = locate(context, place) ?: return@runCatching null
            val picture = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(picture)
            var drawn = 0
            for (t in MapTiles.cover(lat, lon, ZOOM, WIDTH, HEIGHT)) {
                val tile = tile(context, t.x, t.y) ?: continue
                canvas.drawBitmap(tile, t.drawX.toFloat(), t.drawY.toFloat(), null)
                drawn++
            }
            picture.takeIf { drawn > 0 }
        }.getOrNull()
    }

    fun openDirections(context: Context, place: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(place)))
        runCatching { context.startActivity(intent) }
    }

    @Suppress("DEPRECATION")
    private fun locate(context: Context, place: String): Pair<Double, Double>? {
        val prefs = context.getSharedPreferences("map_places", Context.MODE_PRIVATE)
        prefs.getString(place, null)?.split(",")?.mapNotNull { it.toDoubleOrNull() }?.takeIf { it.size == 2 }?.let { return it[0] to it[1] }
        if (!Geocoder.isPresent()) return null
        val found = Geocoder(context).getFromLocationName(place, 1)?.firstOrNull() ?: return null
        prefs.edit().putString(place, "${found.latitude},${found.longitude}").apply()
        return found.latitude to found.longitude
    }

    private fun tile(context: Context, x: Int, y: Int): Bitmap? {
        val file = File(File(context.cacheDir, "map"), "${ZOOM}_${x}_$y.png")
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            val conn = URL("https://tile.openstreetmap.org/$ZOOM/$x/$y.png").openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                // The map's rules ask every app to say who it is.
                conn.setRequestProperty("User-Agent", "FortyCalendar/${BuildConfig.VERSION_NAME} (Android)")
                if (conn.responseCode != 200) return null
                val tmp = File(file.parentFile, file.name + ".part")
                conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                tmp.renameTo(file)
            } finally {
                conn.disconnect()
            }
        }
        return BitmapFactory.decodeFile(file.path)
    }
}
