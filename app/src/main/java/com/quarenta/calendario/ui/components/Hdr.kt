package com.quarenta.calendario.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/** True when [bitmap] is an Ultra HDR photo (it carries a gain map) and this phone can show it. */
fun isHdr(bitmap: Bitmap?): Boolean = bitmap != null && Build.VERSION.SDK_INT >= 34 && bitmap.hasGainmap()

/**
 * While on screen and [enabled], lets the window use the screen's extra brightness so Ultra HDR
 * photos (Pixel, Samsung Galaxy S24 and newer...) glow as they do in Google Photos. The rest of
 * the app keeps its normal colours, and the window goes back to normal when this leaves the screen.
 */
@Composable
fun HdrWhile(enabled: Boolean) {
    if (Build.VERSION.SDK_INT < 34) return
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity, enabled) {
        val window = activity.window
        val before = window.colorMode
        if (enabled) window.colorMode = ActivityInfo.COLOR_MODE_HDR
        onDispose { if (enabled) window.colorMode = before }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
