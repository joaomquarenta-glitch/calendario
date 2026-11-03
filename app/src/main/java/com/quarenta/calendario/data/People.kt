package com.quarenta.calendario.data

import android.content.ContentUris
import android.content.ContentValues
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
    /** Everything in the phone's contact, in card order, ready to show and edit. */
    val fields: List<ContactField> = emptyList(),
    /** WhatsApp's own row for this person, when WhatsApp has synced them. */
    val whatsAppDataId: Long? = null,
    val whatsAppMime: String? = null,
    val ringtone: String? = null,
)

const val WHATSAPP_MIME = "vnd.android.cursor.item/vnd.com.whatsapp.profile"
const val WHATSAPP_BUSINESS_MIME = "vnd.android.cursor.item/vnd.com.whatsapp.w4b.profile"

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
        val fields = mutableListOf<ContactField>()
        var whatsAppId: Long? = null
        var whatsAppMime: String? = null
        val groupIds = mutableListOf<Pair<Long, Long>>() // data row -> group

        val projection = arrayOf(
            ContactsContract.Data._ID,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.DATA2,
            ContactsContract.Data.DATA3,
            ContactsContract.Data.DATA4,
            ContactsContract.Data.DATA5,
            ContactsContract.Data.DATA6,
        )
        val res = context.resources
        resolver.query(
            ContactsContract.Data.CONTENT_URI, projection,
            "${ContactsContract.Data.CONTACT_ID} = ?", arrayOf(person.id.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val mime = c.getString(1)
                val data1 = c.getString(2)?.trim().orEmpty()
                val type = if (c.isNull(3)) 0 else c.getInt(3)
                val custom = c.getString(4)
                if (mime == WHATSAPP_MIME || mime == WHATSAPP_BUSINESS_MIME) {
                    if (whatsAppId == null || mime == WHATSAPP_MIME) { whatsAppId = id; whatsAppMime = mime }
                    continue
                }
                val kind = FieldKind.forMime(mime) ?: continue
                if (kind == FieldKind.Organization) {
                    val title = c.getString(5)?.trim()?.takeIf { it.isNotEmpty() }
                    if (data1.isEmpty() && title == null) continue
                    fields += ContactField(id, kind, type, "Organização", data1, title)
                    continue
                }
                if (data1.isEmpty()) continue
                val label = when (kind) {
                    FieldKind.Phone -> labelFor(kind, type) ?: ContactsContract.CommonDataKinds.Phone.getTypeLabel(res, type, custom).toString()
                    FieldKind.Email -> labelFor(kind, type) ?: ContactsContract.CommonDataKinds.Email.getTypeLabel(res, type, custom).toString()
                    FieldKind.Address -> labelFor(kind, type) ?: ContactsContract.CommonDataKinds.StructuredPostal.getTypeLabel(res, type, custom).toString()
                    FieldKind.Relation -> labelFor(kind, type) ?: ContactsContract.CommonDataKinds.Relation.getTypeLabel(res, type, custom).toString()
                    FieldKind.Event -> labelFor(kind, type) ?: custom ?: "Data"
                    FieldKind.Im -> {
                        val protocol = if (c.isNull(6)) ContactsContract.CommonDataKinds.Im.PROTOCOL_CUSTOM else c.getInt(6)
                        if (protocol == ContactsContract.CommonDataKinds.Im.PROTOCOL_CUSTOM) c.getString(7) ?: "Mensagens"
                        else ContactsContract.CommonDataKinds.Im.getProtocolLabel(res, protocol, null).toString()
                    }
                    FieldKind.Website, FieldKind.Sip -> labelFor(kind, type) ?: custom ?: kind.title
                    else -> kind.title
                }
                val fieldType = if (kind == FieldKind.Im) (if (c.isNull(6)) -1 else c.getInt(6)) else type
                if (kind == FieldKind.Group) {
                    data1.toLongOrNull()?.let { groupIds += id to it }
                    continue
                }
                fields += ContactField(id, kind, fieldType, label, data1)
            }
        }
        // Group rows only hold a number: look up the group names, leaving out automatic ones ("My Contacts").
        if (groupIds.isNotEmpty()) {
            val titles = groupTitles(groupIds.map { it.second })
            groupIds.forEach { (rowId, groupId) ->
                titles[groupId]?.let { fields += ContactField(rowId, FieldKind.Group, 0, "Grupo", it) }
            }
        }

        val unique = fields
            .distinctBy { f ->
                when (f.kind) {
                    FieldKind.Phone -> "p" + f.value.filter(Char::isDigit).takeLast(9)
                    FieldKind.Email -> "e" + f.value.lowercase()
                    else -> f.kind.name + f.label + f.value.lowercase() + (f.secondary ?: "")
                }
            }
            .sortedBy { it.kind.ordinal }

        val birthday = unique.firstOrNull { it.kind == FieldKind.Event && it.type == ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY }
            ?.let { ContactFormat.parseDate(it.value) }
        val org = unique.firstOrNull { it.kind == FieldKind.Organization }
        return PersonDetails(
            person = person,
            phones = unique.filter { it.kind == FieldKind.Phone }.map { LabeledValue(it.label, it.value) },
            emails = unique.filter { it.kind == FieldKind.Email }.map { LabeledValue(it.label, it.value) },
            addresses = unique.filter { it.kind == FieldKind.Address }.map { LabeledValue(it.label, it.value) },
            company = org?.value?.takeIf { it.isNotBlank() },
            jobTitle = org?.secondary,
            birthday = birthday?.first,
            birthYear = birthday?.second,
            fields = unique,
            whatsAppDataId = whatsAppId,
            whatsAppMime = whatsAppMime,
            ringtone = ringtoneOf(person),
        )
    }

    private fun labelFor(kind: FieldKind, type: Int): String? = kind.types.firstOrNull { it.type == type }?.label

    private fun groupTitles(ids: List<Long>): Map<Long, String> {
        val out = mutableMapOf<Long, String>()
        resolver.query(
            ContactsContract.Groups.CONTENT_URI,
            arrayOf(ContactsContract.Groups._ID, ContactsContract.Groups.TITLE, ContactsContract.Groups.AUTO_ADD),
            "${ContactsContract.Groups._ID} IN (${ids.joinToString(",")})", null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val title = c.getString(1)?.trim().orEmpty()
                if (title.isNotEmpty() && c.getInt(2) == 0) out[c.getLong(0)] = title
            }
        }
        return out
    }

    private fun ringtoneOf(person: Person): String? = runCatching {
        resolver.query(
            ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, person.id),
            arrayOf(ContactsContract.Contacts.CUSTOM_RINGTONE), null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    /**
     * The contact entry that new information is written to: the Google one when there is one,
     * never the copies kept by WhatsApp, MB WAY or other apps.
     */
    private fun writableRawContact(person: Person): Pair<Long, Pair<String?, String?>>? {
        val rows = mutableListOf<Triple<Long, String?, String?>>()
        resolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID, ContactsContract.RawContacts.ACCOUNT_TYPE, ContactsContract.RawContacts.ACCOUNT_NAME),
            "${ContactsContract.RawContacts.CONTACT_ID} = ? AND ${ContactsContract.RawContacts.DELETED} = 0",
            arrayOf(person.id.toString()), null,
        )?.use { c -> while (c.moveToNext()) rows += Triple(c.getLong(0), c.getString(1), c.getString(2)) }
        val appCopies = listOf("whatsapp", "mbway", "telegram", "signal", "viber", "facebook", "skype", "linkedin")
        val best = rows.firstOrNull { it.second == "com.google" }
            ?: rows.firstOrNull { r -> appCopies.none { r.second.orEmpty().contains(it, ignoreCase = true) } }
            ?: rows.firstOrNull()
        return best?.let { it.first to (it.second to it.third) }
    }

    private fun valuesFor(kind: FieldKind, type: Int, value: String, secondary: String?): ContentValues = ContentValues().apply {
        put(ContactsContract.Data.MIMETYPE, kind.mime)
        when (kind) {
            FieldKind.Organization -> {
                put(ContactsContract.CommonDataKinds.Organization.COMPANY, value)
                put(ContactsContract.CommonDataKinds.Organization.TITLE, secondary)
                put(ContactsContract.CommonDataKinds.Organization.TYPE, type)
            }
            FieldKind.Address -> {
                put(ContactsContract.CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS, value)
                put(ContactsContract.CommonDataKinds.StructuredPostal.STREET, value)
                put(ContactsContract.CommonDataKinds.StructuredPostal.TYPE, type)
            }
            FieldKind.Im -> {
                put(ContactsContract.CommonDataKinds.Im.DATA, value)
                put(ContactsContract.CommonDataKinds.Im.TYPE, ContactsContract.CommonDataKinds.Im.TYPE_OTHER)
                put(ContactsContract.CommonDataKinds.Im.PROTOCOL, type)
            }
            FieldKind.Note -> put(ContactsContract.CommonDataKinds.Note.NOTE, value)
            FieldKind.Group -> put(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID, value.toLong())
            else -> {
                put(ContactsContract.Data.DATA1, value)
                put(ContactsContract.Data.DATA2, type)
            }
        }
    }

    /** Adds information to the person's contact on the phone. Returns false if nothing was written. */
    fun addField(person: Person, kind: FieldKind, type: Int, value: String, secondary: String? = null): Boolean {
        val raw = writableRawContact(person) ?: return false
        val values = valuesFor(kind, type, value, secondary).apply { put(ContactsContract.Data.RAW_CONTACT_ID, raw.first) }
        return resolver.insert(ContactsContract.Data.CONTENT_URI, values) != null
    }

    fun updateField(field: ContactField, type: Int, value: String, secondary: String? = null): Boolean {
        val values = valuesFor(field.kind, type, value, secondary).apply { remove(ContactsContract.Data.MIMETYPE) }
        return resolver.update(ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, field.dataId), values, null, null) > 0
    }

    fun deleteField(field: ContactField): Boolean =
        resolver.delete(ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, field.dataId), null, null) > 0

    /** Groups the person can be put in: the ones of the same account, without automatic ones. */
    fun groupsFor(person: Person): List<ContactGroup> {
        val account = writableRawContact(person)?.second ?: return emptyList()
        val out = mutableListOf<ContactGroup>()
        val (type, name) = account
        val selection = buildString {
            append("${ContactsContract.Groups.DELETED} = 0 AND ${ContactsContract.Groups.AUTO_ADD} = 0")
            append(if (type == null) " AND ${ContactsContract.Groups.ACCOUNT_TYPE} IS NULL" else " AND ${ContactsContract.Groups.ACCOUNT_TYPE} = ?")
            append(if (name == null) " AND ${ContactsContract.Groups.ACCOUNT_NAME} IS NULL" else " AND ${ContactsContract.Groups.ACCOUNT_NAME} = ?")
        }
        resolver.query(
            ContactsContract.Groups.CONTENT_URI,
            arrayOf(ContactsContract.Groups._ID, ContactsContract.Groups.TITLE),
            selection, listOfNotNull(type, name).toTypedArray(),
            "${ContactsContract.Groups.TITLE} COLLATE LOCALIZED ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val title = c.getString(1)?.trim().orEmpty()
                if (title.isNotEmpty()) out += ContactGroup(c.getLong(0), title)
            }
        }
        return out.distinctBy { it.title }
    }

    /** Sets the person's ringtone; null goes back to the phone's default. */
    fun setRingtone(person: Person, uri: String?): Boolean {
        val values = ContentValues().apply { put(ContactsContract.Contacts.CUSTOM_RINGTONE, uri) }
        return resolver.update(ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, person.id), values, null, null) > 0
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
