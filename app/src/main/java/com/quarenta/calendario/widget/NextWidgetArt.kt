package com.quarenta.calendario.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.core.graphics.ColorUtils

/** Pictures shared by the widgets. */
object NextWidgetArt {
    /** No photo: a rich gradient in the event's colour, like the cards in the app. */
    fun gradient(color: Int): Bitmap {
        val size = 400
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply {
            shader = LinearGradient(
                0f, 0f, size.toFloat(), size.toFloat(),
                intArrayOf(ColorUtils.blendARGB(color, Color.WHITE, 0.25f), color, ColorUtils.blendARGB(color, Color.BLACK, 0.45f)),
                null, Shader.TileMode.CLAMP,
            )
        }
        Canvas(bmp).drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        return bmp
    }
}
