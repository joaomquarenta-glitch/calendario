package com.quarenta.calendario.reminders

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.provider.CalendarContract
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.quarenta.calendario.data.ContactsRepository
import com.quarenta.calendario.data.FieldKind
import com.quarenta.calendario.data.Person
import com.quarenta.calendario.data.PersonDetails

/** The person a reminder is about, with their photo and how to reach them. */
data class ReminderPerson(
    val person: Person,
    val photo: Bitmap?,
    val details: PersonDetails?,
) {
    val firstName: String get() = person.name.trim().split(' ').firstOrNull().orEmpty()
    /** Mobile number first, since that is the one with WhatsApp and SMS. */
    val phone: String? get() {
        val phones = details?.fields?.filter { it.kind == FieldKind.Phone }.orEmpty()
        return (phones.firstOrNull { it.type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE } ?: phones.firstOrNull())?.value
    }
}

object ReminderPeople {

    /** Looks for the person in the event's guests first, then in its title. */
    fun find(context: Context, a: ReminderAlert): ReminderPerson? = runCatching {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val repo = ContactsRepository(context)
        val people = repo.all()
        val byId = people.associateBy { it.id }
        val contactId = fromGuests(context, a.eventId) ?: PersonMatch.inTitle(a.title, people.map { it.id to it.name })
        val person = contactId?.let { byId[it] } ?: return null
        ReminderPerson(person, repo.photo(person), runCatching { repo.details(person) }.getOrNull())
    }.getOrNull()

    /** A guest of the event who is in the phone's contacts (not the person themself, who is the organiser). */
    private fun fromGuests(context: Context, eventId: Long): Long? {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return null
        val emails = mutableListOf<String>()
        context.contentResolver.query(
            CalendarContract.Attendees.CONTENT_URI,
            arrayOf(CalendarContract.Attendees.ATTENDEE_EMAIL, CalendarContract.Attendees.ATTENDEE_RELATIONSHIP),
            "${CalendarContract.Attendees.EVENT_ID} = ?", arrayOf(eventId.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val email = c.getString(0)?.trim()?.lowercase() ?: continue
                if (c.getInt(1) == CalendarContract.Attendees.RELATIONSHIP_ORGANIZER) continue
                emails += email
            }
        }
        for (email in emails) {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Email.CONTACT_ID),
                "LOWER(${ContactsContract.CommonDataKinds.Email.ADDRESS}) = ?", arrayOf(email), null,
            )?.use { c -> if (c.moveToFirst()) return c.getLong(0) }
        }
        return null
    }
}
