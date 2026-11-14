package com.quarenta.calendario.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A present to give someone; ticked once it is bought. */
data class GiftIdea(val text: String, val bought: Boolean = false)

/** Gift ideas for each person, kept on this phone and remembered from one birthday to the next. */
object GiftIdeas {

    private const val PREFS = "gift_ideas"
    private val _version = MutableStateFlow(0)
    /** Changes every time any list is saved, so screens can read again. */
    val version: StateFlow<Int> = _version

    fun get(context: Context, person: Person): List<GiftIdea> =
        decode(prefs(context).getString(person.lookupKey.ifBlank { person.id.toString() }, null))

    fun set(context: Context, person: Person, ideas: List<GiftIdea>) {
        val key = person.lookupKey.ifBlank { person.id.toString() }
        prefs(context).edit().apply { if (ideas.isEmpty()) remove(key) else putString(key, encode(ideas)) }.apply()
        _version.value++
    }

    /** One idea per line: "x " when bought, "- " when not. */
    fun encode(ideas: List<GiftIdea>): String =
        ideas.filter { it.text.isNotBlank() }
            .joinToString("\n") { (if (it.bought) "x " else "- ") + it.text.replace(Regex("\\s+"), " ").trim() }

    fun decode(text: String?): List<GiftIdea> =
        text.orEmpty().lines().mapNotNull { line ->
            when {
                line.startsWith("x ") -> GiftIdea(line.drop(2), bought = true)
                line.startsWith("- ") -> GiftIdea(line.drop(2))
                line.isNotBlank() -> GiftIdea(line.trim())
                else -> null
            }
        }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
