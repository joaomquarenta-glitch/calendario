package com.quarenta.calendario.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.MonthDay

/** A person from the phone's contacts (Google, Samsung, WhatsApp-synced, etc.). */
data class Person(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val photoUri: String?,
    val thumbUri: String?,
    val starred: Boolean,
)

data class LabeledValue(val label: String, val value: String)

data class PersonDetails(
    val person: Person,
    val phones: List<LabeledValue>,
    val emails: List<LabeledValue>,
    val addresses: List<LabeledValue>,
    val company: String?,
    val jobTitle: String?,
    val birthday: MonthDay?,
    val birthYear: Int?,
)

/** Reads people from the phone's contacts. Nothing is copied: edits in Google Contacts show up here. */
class ContactsRepository(private val context: Context) {

    private val resolver get() = context.contentResolver

    fun all(): List<Person> {
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_URI,
            ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
            ContactsContract.Contacts.STARRED,
        )
        val out = mutableListOf<Person>()
        resolver.query(
            ContactsContract.Contacts.CONTENT_URI, projection,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} IS NOT NULL", null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                out += Person(
                    id = c.getLong(0),
                    lookupKey = c.getString(1) ?: "",
                    name = c.getString(2) ?: "",
                    photoUri = c.getString(3),
                    thumbUri = c.getString(4),
                    starred = c.getInt(5) == 1,
                )
            }
        }
        return out
    }

    /** Adds or removes the star in the phone's contacts, so Google/Samsung Contacts show the same favourites. */
    fun setStarred(person: Person, starred: Boolean): Boolean {
        val values = android.content.ContentValues().apply {
            put(ContactsContract.Contacts.STARRED, if (starred) 1 else 0)
        }
        val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, person.id)
        return resolver.update(uri, values, null, null) > 0
    }

    fun details(person: Person): PersonDetails {
        val phones = mutableListOf<LabeledValue>()
        val emails = mutableListOf<LabeledValue>()
        val addresses = mutableListOf<LabeledValue>()
        var company: String? = null
        var jobTitle: String? = null
        var birthday: MonthDay? = null
        var birthYear: Int? = null

        val projection = arrayOf(
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.DATA2,
            ContactsContract.Data.DATA3,
            ContactsContract.Data.DATA4,
        )
        resolver.query(
            ContactsContract.Data.CONTENT_URI, projection,
            "${ContactsContract.Data.CONTACT_ID} = ?", arrayOf(person.id.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val mime = c.getString(0)
                val data1 = c.getString(1)?.trim().orEmpty()
                if (data1.isEmpty()) continue
                val type = if (c.isNull(2)) 0 else c.getInt(2)
                val custom = c.getString(3)
                when (mime) {
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE -> phones += LabeledValue(
                        ContactsContract.CommonDataKinds.Phone.getTypeLabel(context.resources, type, custom).toString(), data1,
                    )
                    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> emails += LabeledValue(
                        ContactsContract.CommonDataKinds.Email.getTypeLabel(context.resources, type, custom).toString(), data1,
                    )
                    ContactsContract.CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE -> addresses += LabeledValue(
                        ContactsContract.CommonDataKinds.StructuredPostal.getTypeLabel(context.resources, type, custom).toString(), data1,
                    )
                    ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE -> {
                        company = data1
                        jobTitle = c.getString(4)?.takeIf { it.isNotBlank() }
                    }
                    ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE ->
                        if (type == ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY) {
                            parseBirthday(data1)?.let { (md, year) -> birthday = md; birthYear = year }
                        }
                }
            }
        }
        return PersonDetails(
            person, phones.distinctBy { it.value.filter(Char::isDigit) }, emails.distinctBy { it.value.lowercase() },
            addresses.distinctBy { it.value }, company, jobTitle, birthday, birthYear,
        )
    }

    /** The largest photo the phone has for this person (full-size when synced, not the thumbnail). */
    fun photo(person: Person): Bitmap? = runCatching {
        val contactUri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, person.id)
        ContactsContract.Contacts.openContactPhotoInputStream(resolver, contactUri, true)?.use {
            BitmapFactory.decodeStream(it)
        } ?: person.photoUri?.let { uri ->
            resolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
        }
    }.getOrNull()

    /** Contact birthdays look like "1985-03-12", "--03-12" or "12/03/1985". */
    private fun parseBirthday(raw: String): Pair<MonthDay, Int?>? = runCatching {
        when {
            raw.startsWith("--") -> MonthDay.parse(raw) to null
            Regex("\\d{4}-\\d{2}-\\d{2}.*").matches(raw) -> LocalDate.parse(raw.take(10)).let { MonthDay.from(it) to it.year }
            Regex("\\d{1,2}/\\d{1,2}/\\d{4}").matches(raw) -> raw.split('/').let {
                MonthDay.of(it[1].toInt(), it[0].toInt()) to it[2].toInt()
            }
            else -> null
        }
    }.getOrNull()
}

/** Free-form information the person writes about someone, kept in this app. */
@Entity(tableName = "person_notes")
data class PersonNote(
    @PrimaryKey val lookupKey: String,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
)

/** Extra fields the person adds to a record, Bento style ("Filhos", "Prefere", "Como nos conhecemos"...). */
@Entity(tableName = "person_fields")
data class PersonField(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val lookupKey: String,
    val label: String,
    val value: String,
)

@Dao
interface PersonDao {
    @Query("SELECT * FROM person_notes WHERE lookupKey = :key")
    fun observeNote(key: String): Flow<PersonNote?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveNote(note: PersonNote)

    @Query("SELECT * FROM person_fields WHERE lookupKey = :key ORDER BY id")
    fun observeFields(key: String): Flow<List<PersonField>>

    @Insert
    suspend fun addField(field: PersonField)

    @Query("DELETE FROM person_fields WHERE id = :id")
    suspend fun deleteField(id: Long)
}
