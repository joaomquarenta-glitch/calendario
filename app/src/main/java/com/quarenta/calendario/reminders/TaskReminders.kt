package com.quarenta.calendario.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.quarenta.calendario.MainActivity
import com.quarenta.calendario.R
import com.quarenta.calendario.data.AppDatabase
import com.quarenta.calendario.data.Task
import com.quarenta.calendario.data.TaskRules
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.ZoneId

/** One alarm per task that has a reminder; the notification has a "Feita" button. */
object TaskReminders {

    private const val PREFS = "forty"
    private const val KEY_IDS = "task_alarm_ids"
    private const val CHANNEL = "tarefas"
    const val ACTION_FIRE = "com.quarenta.calendario.TASK_REMINDER"
    const val ACTION_DONE = "com.quarenta.calendario.TASK_DONE"
    const val EXTRA_ID = "task_id"

    /** Sets the alarms for [tasks] and cancels the ones no longer needed. Safe to call often. */
    fun sync(context: Context, tasks: List<Task>) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val wanted = tasks.mapNotNull { t ->
            TaskRules.reminderAt(t, zone)?.toEpochMilli()?.takeIf { it > now }?.let { t.id to it }
        }.toMap()
        val before = prefs.getStringSet(KEY_IDS, emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }
        (before - wanted.keys).forEach { alarms.cancel(fireIntent(context, it)) }
        wanted.forEach { (id, at) ->
            val pending = fireIntent(context, id)
            runCatching {
                if (ReminderScheduler.canUseExactAlarms(context)) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
                else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        }
        prefs.edit().putStringSet(KEY_IDS, wanted.keys.map { it.toString() }.toSet()).apply()
    }

    /** After a restart or a clock change: reads the tasks and sets their alarms again. Call off the main thread. */
    fun syncFromDatabase(context: Context) {
        val tasks = runBlocking { AppDatabase.get(context).tasks().all() }
        sync(context, tasks)
    }

    fun post(context: Context, task: Task) {
        if (!ReminderNotifier.canPost(context)) return
        ensureChannel(context)
        val id = notificationId(task.id)
        val open = PendingIntent.getActivity(
            context, id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val done = PendingIntent.getBroadcast(
            context, id,
            Intent(context, TaskReminderReceiver::class.java).setAction(ACTION_DONE).putExtra(EXTRA_ID, task.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val time = task.timeMinutes?.let { TaskRules.timeText(it) }
        val builder = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(task.title)
            .setContentText(if (time != null) "Tarefa às $time" else "Tarefa para hoje")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "Feita", done)
        runCatching { NotificationManagerCompat.from(context).notify(id, builder.build()) }
    }

    fun dismiss(context: Context, taskId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(taskId))
    }

    private fun notificationId(taskId: Long) = 300_000 + (taskId % 600_000).toInt()

    private fun fireIntent(context: Context, taskId: Long): PendingIntent = PendingIntent.getBroadcast(
        context, notificationId(taskId),
        Intent(context, TaskReminderReceiver::class.java).setAction(ACTION_FIRE).putExtra(EXTRA_ID, taskId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Tarefas", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Lembretes das tarefas com hora"
            },
        )
    }
}

/** A task's reminder going off, or its "Feita" button being pressed. */
class TaskReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(TaskReminders.EXTRA_ID, -1)
        if (id < 0) return
        val pending = goAsync()
        Thread {
            try {
                val app = context.applicationContext
                val dao = AppDatabase.get(app).tasks()
                runBlocking {
                    val task = dao.get(id) ?: return@runBlocking
                    when (intent.action) {
                        TaskReminders.ACTION_FIRE -> if (!task.done) TaskReminders.post(app, task)
                        TaskReminders.ACTION_DONE -> {
                            val updated = TaskRules.complete(task, LocalDate.now())
                            dao.update(updated)
                            if (updated.dueEpochDay != task.dueEpochDay) dao.reopenSteps(task.id)
                            TaskReminders.dismiss(app, id)
                        }
                    }
                }
                runCatching { TaskReminders.syncFromDatabase(app) }
            } finally { pending.finish() }
        }.start()
    }
}
