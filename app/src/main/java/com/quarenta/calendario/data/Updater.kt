package com.quarenta.calendario.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A newer version published on GitHub. */
data class AppUpdate(val version: String, val apkUrl: String, val sizeBytes: Long)

/**
 * Looks for a newer FortyCalendar on the GitHub releases page and installs it over the
 * current one. Android always shows its own "Install?" confirmation; nothing is lost.
 */
object Updater {

    private const val LATEST = "https://api.github.com/repos/joaomquarenta-glitch/calendario/releases/latest"
    private const val APK_NAME = "FortyCalendar.apk"

    /** "v0.3.12" -> [0, 3, 12]; anything unreadable -> null. */
    fun parseVersion(raw: String): List<Int>? {
        val parts = raw.trim().removePrefix("v").removePrefix("V").split('.')
        if (parts.isEmpty()) return null
        return parts.map { it.toIntOrNull() ?: return null }
    }

    /** True when [candidate] is a higher version than [current]. */
    fun isNewer(candidate: String, current: String): Boolean {
        val a = parseVersion(candidate) ?: return false
        val b = parseVersion(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** Network call: run off the main thread. Returns null when up to date or offline. */
    fun check(currentVersion: String): AppUpdate? = runCatching {
        val conn = (URL(LATEST).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (conn.responseCode != 200) return null
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val tag = json.getString("tag_name")
            if (!isNewer(tag, currentVersion)) return null
            val assets = json.getJSONArray("assets")
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.getString("name") == APK_NAME) {
                    return AppUpdate(tag.removePrefix("v"), a.getString("browser_download_url"), a.optLong("size"))
                }
            }
            null
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** Downloads the APK into the app's cache, reporting progress from 0 to 1. */
    fun download(context: Context, update: AppUpdate, onProgress: (Float) -> Unit): File? = runCatching {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, APK_NAME)
        val conn = openFollowingRedirects(URL(update.apkUrl)) ?: return null
        val total = conn.contentLengthLong.takeIf { it > 0 } ?: update.sizeBytes
        conn.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var done = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    output.write(buffer, 0, n)
                    done += n
                    if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
            }
        }
        conn.disconnect()
        target
    }.getOrNull()

    /** GitHub answers downloads with a redirect to its file storage (possibly another host). */
    private fun openFollowingRedirects(start: URL): HttpURLConnection? {
        var url = start
        repeat(6) {
            val conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 30_000
            }
            val code = conn.responseCode
            if (code == 200) return conn
            val location = conn.getHeaderField("Location")
            conn.disconnect()
            if (code !in 300..399 || location == null) return null
            url = URL(url, location)
        }
        return null
    }

    /** Android asks once per app for permission to install updates. */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Hands the APK to Android's installer, which updates the app in place. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
