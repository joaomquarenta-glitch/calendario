package com.quarenta.calendario.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Photos the person chose for someone (from the gallery, or shared from Instagram, WhatsApp...),
 * kept inside this app in high resolution. Google and Samsung shrink contact photos to 720 px,
 * so these are never written back to the contact.
 */
class PersonPhotos(context: Context) {

    private val app = context.applicationContext
    private val dir = File(app.filesDir, "people-photos").apply { mkdirs() }
    private val prefs = app.getSharedPreferences("people_photos", Context.MODE_PRIVATE)

    fun has(person: Person): Boolean = fileFor(person).exists()

    /** When the photo was taken (or, when the file doesn't say, when it was chosen here). */
    fun takenAt(person: Person): LocalDate? {
        if (!has(person)) return null
        val ms = prefs.getLong(person.lookupKey + ".taken", 0L).takeIf { it > 0 }
            ?: prefs.getLong(person.lookupKey + ".added", 0L).takeIf { it > 0 }
            ?: fileFor(person).lastModified()
        return java.time.Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    /** Decodes the stored photo; [maxPx] limits the longest side to save memory in small tiles. */
    fun load(person: Person, maxPx: Int? = null): Bitmap? {
        val f = fileFor(person)
        if (!f.exists()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            val opts = BitmapFactory.Options().apply {
                inSampleSize = PhotoMath.sampleSize(bounds.outWidth, bounds.outHeight, maxPx ?: Int.MAX_VALUE)
            }
            BitmapFactory.decodeFile(f.path, opts)
        }.getOrNull()
    }

    /** Copies [uri] in up to [PhotoMath.STORE_PX] px, upright, at near-lossless quality. */
    fun save(person: Person, uri: Uri): Boolean = runCatching { store(person, uri) }.getOrDefault(false)

    private fun store(person: Person, uri: Uri): Boolean {
        val bitmap = decodeUpright(uri) ?: return false
        val taken = takenDate(uri)
        val tmp = File(dir, fileFor(person).name + ".tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        bitmap.recycle()
        if (!tmp.renameTo(fileFor(person))) { tmp.delete(); return false }
        val edit = prefs.edit().putLong(person.lookupKey + ".added", System.currentTimeMillis())
        if (taken != null) edit.putLong(person.lookupKey + ".taken", taken) else edit.remove(person.lookupKey + ".taken")
        edit.apply()
        bump()
        return true
    }

    /** Goes back to the phone contact's own photo. */
    fun remove(person: Person): Boolean {
        val ok = fileFor(person).delete()
        prefs.edit().remove(person.lookupKey + ".taken").remove(person.lookupKey + ".added").apply()
        bump()
        return ok
    }

    private fun fileFor(person: Person): File {
        val hash = MessageDigest.getInstance("SHA-256").digest(person.lookupKey.toByteArray())
            .take(16).joinToString("") { "%02x".format(it) }
        return File(dir, "$hash.jpg")
    }

    private fun decodeUpright(uri: Uri): Bitmap? {
        val resolver = app.contentResolver
        if (Build.VERSION.SDK_INT >= 28) {
            // ImageDecoder reads HEIF/AVIF/WebP and already turns the picture the right way up.
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
                    val (w, h) = PhotoMath.fit(info.size.width, info.size.height, PhotoMath.STORE_PX)
                    decoder.setTargetSize(w, h)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                }
            }.getOrNull()?.let { return it }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = PhotoMath.sampleSize(bounds.outWidth, bounds.outHeight, PhotoMath.STORE_PX)
        }
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val degrees = runCatching {
            resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrNull() ?: 0f
        val (w, h) = PhotoMath.fit(raw.width, raw.height, PhotoMath.STORE_PX)
        val m = Matrix().apply { postScale(w.toFloat() / raw.width, h.toFloat() / raw.height); postRotate(degrees) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true)
    }

    /** The date in the photo's own data, or the gallery's "date taken". Shared pictures often have neither. */
    private fun takenDate(uri: Uri): Long? {
        val resolver = app.contentResolver
        runCatching {
            resolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                PhotoMath.parseExifDate(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: exif.getAttribute(ExifInterface.TAG_DATETIME))
            }
        }.getOrNull()?.let { return it }
        return runCatching {
            resolver.query(uri, arrayOf(MediaStore.Images.ImageColumns.DATE_TAKEN), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0).takeIf { it > 0 } else null
            }
        }.getOrNull()
    }

    companion object {
        private val _version = MutableStateFlow(0)
        /** Goes up every time a chosen photo changes, so every screen showing it reloads. */
        val version: StateFlow<Int> = _version.asStateFlow()
        private fun bump() { _version.value++ }
    }
}

/** The arithmetic behind [PersonPhotos], kept apart so it can be tested. */
object PhotoMath {
    /** Longest side kept: sharp on the largest phone screens, even when cropped to fill. */
    const val STORE_PX = 2560

    /** Largest power of two that keeps the longest side at or above [maxPx]. */
    fun sampleSize(width: Int, height: Int, maxPx: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var s = 1
        while (maxOf(width, height) / (s * 2) >= maxPx) s *= 2
        return s
    }

    /** [width] x [height] scaled down (never up) so the longest side is at most [maxPx]. */
    fun fit(width: Int, height: Int, maxPx: Int): Pair<Int, Int> {
        val longest = maxOf(width, height)
        if (longest <= maxPx || longest <= 0) return width to height
        return (width.toLong() * maxPx / longest).toInt().coerceAtLeast(1) to
            (height.toLong() * maxPx / longest).toInt().coerceAtLeast(1)
    }

    /** EXIF dates look like "2024:05:17 18:42:03". */
    fun parseExifDate(raw: String?, zone: ZoneId = ZoneId.systemDefault()): Long? = runCatching {
        val t = LocalDateTime.parse(raw!!.trim(), DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
        t.atZone(zone).toInstant().toEpochMilli()
    }.getOrNull()

    /** "Foto de há 2 anos" once the photo is a year old, otherwise null. */
    fun ageWarning(taken: LocalDate?, today: LocalDate): String? {
        if (taken == null) return null
        val years = ChronoUnit.YEARS.between(taken, today)
        return when {
            years < 1 -> null
            years == 1L -> "Foto de há mais de 1 ano"
            else -> "Foto de há $years anos"
        }
    }
}
