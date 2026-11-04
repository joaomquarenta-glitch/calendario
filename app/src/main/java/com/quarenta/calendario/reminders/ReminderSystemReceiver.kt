package com.quarenta.calendario.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Things the phone announces that can move reminders: a restart, a clock or time-zone change,
 * an app update, or any change in a calendar (an event added in Google Calendar, a sync...).
 * Other apps can send these too, so all it ever does is recompute the next alarm and redraw the widget.
 */
class ReminderSystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                runCatching { ReminderScheduler.reschedule(context.applicationContext) }
                // The same changes can move the next event on the home-screen widget.
                runCatching { com.quarenta.calendario.widget.NextWidget.refresh(context.applicationContext) }
            } finally { pending.finish() }
        }.start()
    }
}
