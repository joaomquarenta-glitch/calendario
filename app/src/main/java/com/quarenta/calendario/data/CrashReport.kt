package com.quarenta.calendario.data

import android.content.Context
import android.content.Intent
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * The words of an error report. Only technical facts go in: the app version, the phone model,
 * the Android version and where in the code it broke. Never names, events or notes.
 */
object CrashReport {
    const val MAX_LINES = 80

    fun header(version: String, model: String, sdk: Int, at: LocalDateTime): String =
        "FortyCalendar $version\nTelemóvel: $model\nAndroid: API $sdk\nQuando: ${at.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}"

    /** The stack trace with its causes, cut to [maxLines] so it fits in an email or a WhatsApp message. */
    fun stack(error: Throwable, maxLines: Int = MAX_LINES): String {
        val out = StringWriter()
        error.printStackTrace(PrintWriter(out))
        val lines = out.toString().trimEnd().lines()
        return if (lines.size <= maxLines) lines.joinToString("\n")
        else (lines.take(maxLines) + "… (+${lines.size - maxLines} linhas)").joinToString("\n")
    }

    fun crash(header: String, thread: String, error: Throwable): String =
        "$header\nFio: $thread\n\nErro:\n${stack(error)}"

    /** A report written by hand from the app, with the last crash attached when there is one. */
    fun manual(header: String, lastCrash: String?): String =
        header + "\n\nDescreve aqui o que aconteceu:\n\n" +
            (lastCrash?.let { "\n--- Último erro guardado ---\n$it" } ?: "")
}

/** Keeps the last crash on the phone and offers to send it the next time the app opens. */
object CrashStore {
    private const val PREFS = "crash"
    private const val KEY_UNSEEN = "unseen"

    private fun file(context: Context) = File(context.filesDir, "crash/last.txt")

    fun header(context: Context): String {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
        return CrashReport.header(version, "${Build.MANUFACTURER} ${Build.MODEL}", Build.VERSION.SDK_INT, LocalDateTime.now())
    }

    /** Catches an error that would close the app, writes it down, then lets Android close the app as usual. */
    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val f = file(app)
                f.parentFile?.mkdirs()
                f.writeText(CrashReport.crash(header(app), thread.name, error))
                // commit, not apply: the process is about to end.
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_UNSEEN, true).commit()
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun last(context: Context): String? = file(context).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }

    /** True once after a crash, until the person answers the question about it. */
    fun hasUnseen(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_UNSEEN, false) && last(context) != null

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_UNSEEN, false).apply()
    }

    /** Opens the share sheet (email, WhatsApp...) with the report as plain text. */
    fun send(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "FortyCalendar: relatório de erro")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Enviar relatório").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
