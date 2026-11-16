package com.quarenta.calendario.ui.components

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView

/** The few kinds of touch the app answers with a small vibration. */
enum class Touch {
    /** A light tick: opening a tile, changing tab, picking a day or a chip. */
    Tick,
    /** Something was done: a task ticked, an event saved, a copy kept. */
    Confirm,
    /** Something was taken away or refused: a delete. */
    Reject,
    /** A long press that opens more choices. */
    Long,
}

/** Which Android vibration answers each [Touch], with older phones falling back to what they have. */
object HapticRules {
    const val PREF = "haptics_on"

    fun constant(touch: Touch, sdk: Int): Int = when (touch) {
        Touch.Tick -> HapticFeedbackConstants.CONTEXT_CLICK
        Touch.Confirm -> if (sdk >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        Touch.Reject -> if (sdk >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
        Touch.Long -> HapticFeedbackConstants.LONG_PRESS
    }
}

/** The app's vibration on touch. It is on by default and can be turned off in Aparência. */
object Haptics {
    var enabled by mutableStateOf(true)
        private set

    fun load(prefs: SharedPreferences) {
        enabled = prefs.getBoolean(HapticRules.PREF, true)
    }

    fun setEnabled(context: Context, on: Boolean) {
        enabled = on
        context.getSharedPreferences("forty", Context.MODE_PRIVATE).edit().putBoolean(HapticRules.PREF, on).apply()
    }

    fun perform(view: View, touch: Touch) {
        if (!enabled) return
        view.performHapticFeedback(HapticRules.constant(touch, Build.VERSION.SDK_INT))
    }
}

/** A function that vibrates for a [Touch], tied to the current screen. */
@Composable
fun rememberHaptics(): (Touch) -> Unit {
    val view = LocalView.current
    return remember(view) { { touch: Touch -> Haptics.perform(view, touch) } }
}
