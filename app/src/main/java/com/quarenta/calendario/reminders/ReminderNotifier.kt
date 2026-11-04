package com.quarenta.calendario.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.quarenta.calendario.R
import com.quarenta.calendario.ui.PT
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Posts the reminder: full screen when the phone is locked, a banner with buttons when it is in use. */
object ReminderNotifier {

    const val CHANNEL = "lembretes"
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", PT)

    fun notificationId(a: ReminderAlert): Int = "${a.eventId}@${a.beginMs}".hashCode()

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Lembretes", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Lembretes dos compromissos, em ecrã inteiro"
                enableVibration(true)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
    }

    fun canPost(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Android 14+ asks the person to allow full-screen reminders for apps that are not alarm clocks. */
    fun canUseFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    fun show(context: Context, a: ReminderAlert) {
        ensureChannel(context)
        val id = notificationId(a)
        val screen = PendingIntent.getActivity(
            context, id,
            ReminderIntents.put(Intent(context, ReminderActivity::class.java), a)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        fun button(action: String, code: Int) = PendingIntent.getBroadcast(
            context, id * 4 + code,
            ReminderIntents.put(Intent(context, ReminderReceiver::class.java).setAction(action), a),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val time = if (a.allDay) "Todo o dia" else timeFmt.format(Instant.ofEpochMilli(a.beginMs).atZone(ZoneId.systemDefault()))
        val text = listOfNotNull(
            "$time · ${ReminderPlan.whenText(a.beginMs, System.currentTimeMillis(), a.allDay)}",
            a.location,
        ).joinToString(" · ")

        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setColor(ReminderScheduler.color(context))
            .setContentTitle(a.title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
            .setAutoCancel(true)
            .addAction(0, "Adiar 10 min", button(ReminderReceiver.ACTION_SNOOZE_10, 1))
            .addAction(0, "Dispensar", button(ReminderReceiver.ACTION_DISMISS, 2))
            .build()
        if (canPost(context)) {
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(context).notify(id, n)
        }
    }

    fun cancel(context: Context, a: ReminderAlert) =
        NotificationManagerCompat.from(context).cancel(notificationId(a))
}
