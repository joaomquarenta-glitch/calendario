package com.quarenta.calendario.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A backup in one .zip file the person keeps where they like (Drive, e-mail, the phone):
 * tasks, notes and fields of people, plans, gift ideas, settings and the HD photos.
 * Calendar events are not in it: they live in the calendars themselves (Google, Outlook...).
 */
object Backup {

    private const val KEY_LAST = "last_backup"

    fun lastBackup(context: Context): LocalDate? =
        context.getSharedPreferences("forty", Context.MODE_PRIVATE).getString(KEY_LAST, null)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Writes the backup to [uri]. Call off the main thread. */
    fun export(context: Context, uri: Uri) {
        AppDatabase.checkpoint(context)
        val base = context.dataDir
        context.contentResolver.openOutputStream(uri, "wt").use { raw ->
            requireNotNull(raw) { "Não foi possível escrever o ficheiro" }
            ZipOutputStream(raw.buffered()).use { zip ->
                fun put(entry: String, file: File) {
                    if (!file.isFile) return
                    zip.putNextEntry(ZipEntry(entry))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
                zip.putNextEntry(ZipEntry(BackupPlan.MANIFEST))
                zip.write("${BackupPlan.SIGNATURE}\n${LocalDate.now()}\n".toByteArray())
                zip.closeEntry()
                put(BackupPlan.databaseEntry(), context.getDatabasePath(BackupPlan.DATABASE))
                BackupPlan.prefs.forEach { put(BackupPlan.prefsEntry(it), File(base, "shared_prefs/$it.xml")) }
                File(context.filesDir, "people-photos").listFiles()?.filter { it.isFile && !it.name.endsWith(".tmp") }
                    ?.forEach { put(BackupPlan.photoEntry(it.name), it) }
            }
        }
        context.getSharedPreferences("forty", Context.MODE_PRIVATE).edit().putString(KEY_LAST, LocalDate.now().toString()).commit()
    }

    /**
     * Puts everything back from [uri]. The file is first read whole into a scratch folder; only if it
     * really is one of our backups is anything replaced. Returns false for a file that is not.
     * Afterwards the app must restart ([restart]) so nothing old stays in memory.
     */
    fun restore(context: Context, uri: Uri): Boolean {
        val scratch = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        var manifest: String? = null
        val staged = mutableMapOf<String, File>()
        context.contentResolver.openInputStream(uri).use { raw ->
            requireNotNull(raw) { "Não foi possível ler o ficheiro" }
            ZipInputStream(raw.buffered()).use { zip ->
                var i = 0
                while (true) {
                    val e = zip.nextEntry ?: break
                    if (e.isDirectory) continue
                    if (e.name == BackupPlan.MANIFEST) {
                        manifest = zip.readBytes().decodeToString()
                        continue
                    }
                    val target = BackupPlan.target(e.name) ?: continue
                    val tmp = File(scratch, "part${i++}")
                    tmp.outputStream().use { zip.copyTo(it) }
                    staged[target] = tmp
                }
            }
        }
        if (!BackupPlan.isBackup(manifest) || "databases/${BackupPlan.DATABASE}" !in staged) {
            scratch.deleteRecursively()
            return false
        }
        AppDatabase.closeForRestore()
        val base = context.dataDir
        File(base, "databases/${BackupPlan.DATABASE}-wal").delete()
        File(base, "databases/${BackupPlan.DATABASE}-shm").delete()
        File(context.filesDir, "people-photos").listFiles()?.forEach { it.delete() }
        staged.forEach { (target, tmp) ->
            val dest = File(base, target)
            dest.parentFile?.mkdirs()
            tmp.copyTo(dest, overwrite = true)
        }
        scratch.deleteRecursively()
        return true
    }

    /** Starts the app again from scratch, reading the restored data. */
    fun restart(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        context.startActivity(Intent.makeRestartActivityTask(launch.component))
        Runtime.getRuntime().exit(0)
    }
}
