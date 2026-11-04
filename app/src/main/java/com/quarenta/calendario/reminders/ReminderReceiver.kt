package com.quarenta.calendario.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Wakes up for: the next reminder, a snoozed reminder, the buttons on the reminder banner,
 * and anything that can move reminders (phone restart, clock or time-zone change,
 * app update, or a change in any calendar).
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Thread {
            try {
                handle(context.applicationContext, intent)
            } finally {
                pending.finish()
            }
        }.start()
    }

    private fun handle(context: Context, intent: Intent) {
        val alert = ReminderIntents.get(intent)
        when (intent.action) {
            ReminderScheduler.ACTION_SNOOZED -> {
                if (alert != null && ReminderScheduler.isEnabled(context)) ReminderNotifier.show(context, alert)
            }
            ACTION_SNOOZE_10 -> if (alert != null) {
                ReminderNotifier.cancel(context, alert)
                ReminderScheduler.snooze(context, alert, 10)
            }
            ACTION_DISMISS -> if (alert != null) ReminderNotifier.cancel(context, alert)
            else -> ReminderScheduler.reschedule(context)
        }
    }

    companion object {
        const val ACTION_SNOOZE_10 = "com.quarenta.calendario.REMINDER_SNOOZE_10"
        const val ACTION_DISMISS = "com.quarenta.calendario.REMINDER_DISMISS"
    }
}
