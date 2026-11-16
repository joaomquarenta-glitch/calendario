package com.quarenta.calendario.data

import java.time.LocalDate

/**
 * What goes into a backup file and where each part goes back on restore. Only the names listed
 * here are ever written, so a strange or damaged file can never put anything elsewhere.
 */
object BackupPlan {
    const val MANIFEST = "fortycalendar.txt"
    const val SIGNATURE = "FortyCalendar cópia 1"
    const val DATABASE = "calendario.db"

    /** The app's settings files: looks, reminders, gift ideas, calendar links, hidden calendars, photos. */
    val prefs = listOf("forty", "gift_ideas", "subscriptions", "calendar_choice", "people_photos")

    private val safeName = Regex("^[A-Za-z0-9][A-Za-z0-9._-]{0,120}$")

    fun databaseEntry() = "db/$DATABASE"
    fun prefsEntry(name: String) = "prefs/$name.xml"
    fun photoEntry(file: String) = "photos/$file"

    /** Where an entry of the file goes, relative to the app's own folder; null for anything unknown. */
    fun target(entry: String): String? = when {
        entry == databaseEntry() -> "databases/$DATABASE"
        entry.startsWith("prefs/") -> {
            val name = entry.removePrefix("prefs/").removeSuffix(".xml")
            if (entry.endsWith(".xml") && name in prefs) "shared_prefs/$name.xml" else null
        }
        entry.startsWith("photos/") -> {
            val file = entry.removePrefix("photos/")
            if (safeName.matches(file) && !file.contains("..")) "files/people-photos/$file" else null
        }
        else -> null
    }

    fun isBackup(manifest: String?): Boolean = manifest?.lineSequence()?.firstOrNull()?.trim() == SIGNATURE

    fun fileName(date: LocalDate): String = "FortyCalendar-copia-$date.zip"
}
