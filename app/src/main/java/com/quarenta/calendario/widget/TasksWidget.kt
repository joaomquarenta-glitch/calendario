package com.quarenta.calendario.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import com.quarenta.calendario.MainActivity
import com.quarenta.calendario.R
import com.quarenta.calendario.data.AppDatabase
import com.quarenta.calendario.data.TaskRules
import com.quarenta.calendario.reminders.TaskReminders
import kotlinx.coroutines.runBlocking
import java.time.LocalDate

/** Home-screen widget: the tasks to do first, ticked off with one tap and without opening the app. */
class TasksWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Thread { try { render(context.applicationContext, manager, ids) } finally { pending.finish() } }.start()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DONE) return super.onReceive(context, intent)
        val id = intent.getLongExtra(EXTRA_TASK, -1L)
        val pending = goAsync()
        Thread {
            try {
                val app = context.applicationContext
                runBlocking {
                    val dao = AppDatabase.get(app).tasks()
                    val task = dao.get(id) ?: return@runBlocking
                    if (task.done) return@runBlocking
                    // The same rule as in the app: a repeating task moves on, with its steps undone.
                    val updated = TaskRules.complete(task, LocalDate.now())
                    dao.update(updated)
                    if (updated.dueEpochDay != task.dueEpochDay) dao.reopenSteps(task.id)
                    if (updated.done) TaskReminders.dismiss(app, task.id)
                }
                TaskReminders.syncFromDatabase(app)
                val manager = AppWidgetManager.getInstance(app)
                render(app, manager, manager.getAppWidgetIds(ComponentName(app, TasksWidget::class.java)))
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        private const val ACTION_DONE = "com.quarenta.calendario.widget.TASK_DONE"
        private const val EXTRA_TASK = "task_id"
        private val rows = intArrayOf(R.id.task_row1, R.id.task_row2, R.id.task_row3, R.id.task_row4, R.id.task_row5)
        private val checks = intArrayOf(R.id.task_check1, R.id.task_check2, R.id.task_check3, R.id.task_check4, R.id.task_check5)
        private val titles = intArrayOf(R.id.task_title1, R.id.task_title2, R.id.task_title3, R.id.task_title4, R.id.task_title5)
        private val whens = intArrayOf(R.id.task_when1, R.id.task_when2, R.id.task_when3, R.id.task_when4, R.id.task_when5)

        /** Redraws every tasks widget. Safe to call from any thread, and cheap when there is none. */
        fun refresh(context: Context) {
            val app = context.applicationContext
            val manager = AppWidgetManager.getInstance(app)
            val ids = manager.getAppWidgetIds(ComponentName(app, TasksWidget::class.java))
            if (ids.isEmpty()) return
            Thread { runCatching { render(app, manager, ids) } }.start()
        }

        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val today = LocalDate.now()
            val all = runCatching { runBlocking { AppDatabase.get(context).tasks().all() } }.getOrDefault(emptyList())
            val shown = WidgetPicks.tasks(all, today)
            val views = RemoteViews(context.packageName, R.layout.widget_tasks)
            val due = WidgetPicks.dueCount(all, today)
            views.setTextViewText(R.id.tasks_count, if (due > 0) "$due para hoje" else "")
            views.setViewVisibility(R.id.tasks_empty, if (shown.isEmpty()) View.VISIBLE else View.GONE)
            rows.forEachIndexed { i, rowId ->
                val task = shown.getOrNull(i)
                if (task == null) { views.setViewVisibility(rowId, View.GONE); return@forEachIndexed }
                views.setViewVisibility(rowId, View.VISIBLE)
                views.setTextViewText(titles[i], task.title)
                val whenText = WidgetPicks.taskWhen(task, today)
                views.setTextViewText(whens[i], whenText)
                views.setViewVisibility(whens[i], if (whenText.isEmpty()) View.GONE else View.VISIBLE)
                views.setTextColor(whens[i], if (whenText == "Atrasada") 0xFFFF6961.toInt() else 0xB3FFFFFF.toInt())
                val done = Intent(context, TasksWidget::class.java).setAction(ACTION_DONE)
                    .setData(Uri.parse("forty://task/${task.id}")).putExtra(EXTRA_TASK, task.id)
                views.setOnClickPendingIntent(
                    checks[i],
                    PendingIntent.getBroadcast(context, 8100 + i, done, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
                )
                views.setOnClickPendingIntent(titles[i], openTasks(context, 8110 + i))
            }
            views.setOnClickPendingIntent(R.id.tasks_header, openTasks(context, 8120))
            views.setOnClickPendingIntent(R.id.tasks_empty, openTasks(context, 8121))
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun openTasks(context: Context, requestCode: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_TAB, "tasks")
            return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
