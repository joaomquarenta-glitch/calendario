package com.quarenta.calendario.data

import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.CommonDataKinds.SipAddress
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import java.time.LocalDate
import java.time.MonthDay

/** One choice for a field's kind, e.g. "Telemóvel" for a phone. [type] is Android's own constant. */
data class FieldType(val type: Int, val label: String)

/** Every kind of information the phone's contacts can hold, in the order the card shows them. */
enum class FieldKind(val title: String, val mime: String, val types: List<FieldType>) {
    Phone("Telefone", android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE, listOf(
        FieldType(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE, "Telemóvel"),
        FieldType(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_HOME, "Casa"),
        FieldType(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_WORK, "Trabalho"),
        FieldType(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MAIN, "Principal"),
        FieldType(android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_OTHER, "Outro"),
    )),
    Email("Email", android.provider.ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE, listOf(
        FieldType(android.provider.ContactsContract.CommonDataKinds.Email.TYPE_HOME, "Pessoal"),
        FieldType(android.provider.ContactsContract.CommonDataKinds.Email.TYPE_WORK, "Trabalho"),
        FieldType(android.provider.ContactsContract.CommonDataKinds.Email.TYPE_OTHER, "Outro"),
    )),
    Address("Morada", StructuredPostal.CONTENT_ITEM_TYPE, listOf(
        FieldType(StructuredPostal.TYPE_HOME, "Domicílio"),
        FieldType(StructuredPostal.TYPE_WORK, "Trabalho"),
        FieldType(StructuredPostal.TYPE_OTHER, "Outra"),
    )),
    Organization("Organização", android.provider.ContactsContract.CommonDataKinds.Organization.CONTENT_ITEM_TYPE, listOf(
        FieldType(android.provider.ContactsContract.CommonDataKinds.Organization.TYPE_WORK, "Trabalho"),
    )),
    Event("Datas", android.provider.ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE, listOf(
        FieldType(android.provider.ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY, "Data de nascimento"),
        FieldType(android.provider.ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY, "Aniversário de casamento"),
        FieldType(android.provider.ContactsContract.CommonDataKinds.Event.TYPE_OTHER, "Outra data"),
    )),
    Website("Página de Internet", android.provider.ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE, listOf(
        FieldType(android.provider.ContactsContract.CommonDataKinds.Website.TYPE_HOMEPAGE, "Página pessoal"),
        FieldType(android.provider.ContactsContract.CommonDataKinds.Website.TYPE_WORK, "Trabalho"),
        FieldType(android.provider.ContactsContract.CommonDataKinds.Website.TYPE_BLOG, "Blogue"),
        FieldType(android.provider.ContactsContract.CommonDataKinds.Website.TYPE_OTHER, "Outra"),
    )),
    Im("Mensagens instantâneas", android.provider.ContactsContract.CommonDataKinds.Im.CONTENT_ITEM_TYPE, listOf(
        // For instant messages the choice is the service (protocol), not home/work.
        FieldType(android.provider.ContactsContract.CommonDataKinds.Im.PROTOCOL_SKYPE, "Skype"),
        FieldType(android.provider.ContactsContract.CommonDataKinds.Im.PROTOCOL_GOOGLE_TALK, "Google"),
        FieldType(android.provider.ContactsContract.CommonDataKinds.Im.PROTOCOL_AIM, "AIM"),
        FieldType(android.provider.ContactsContract.CommonDataKinds.Im.PROTOCOL_ICQ, "ICQ"),
        FieldType(android.provider.ContactsContract.CommonDataKinds.Im.PROTOCOL_JABBER, "Jabber"),
    )),
    Sip("Chamada via Internet", SipAddress.CONTENT_ITEM_TYPE, listOf(
        FieldType(SipAddress.TYPE_HOME, "Casa"),
        FieldType(SipAddress.TYPE_WORK, "Trabalho"),
        FieldType(SipAddress.TYPE_OTHER, "Outra"),
    )),
    Nickname("Alcunha", android.provider.ContactsContract.CommonDataKinds.Nickname.CONTENT_ITEM_TYPE, listOf(
        FieldType(android.provider.ContactsContract.CommonDataKinds.Nickname.TYPE_DEFAULT, "Alcunha"),
    )),
    Relation("Relação", android.provider.ContactsContract.CommonDataKinds.Relation.CONTENT_ITEM_TYPE, listOf(
        FieldType(android.provider.ContactsContract.CommonDataKinds.Relation.TYPE_MOTHER, "Mãe"),
        FieldType(android.provider.ContactsContract.CommonDataKinds.Relation.TYPE_FATHER, "Pai"),
        FieldType(android.provider.ContactsContract.CommonDataKinds.Relation.TYPE_SPOUSE, "Cônjuge"),
        FieldType(android.provider.ContactsContract.CommonDataKinds.Relation.TYPE_PARTNER, "Companheiro(a)"),
        FieldType(android.provider.ContactsContract.CommonDataKinds.Relation.TYPE_CHILD, "Filho(a)"),
        FieldType(android.provider.ContactsContract.CommonDataKinds.Relation.TYPE_BROTHER, "Irmão"),
        FieldType(android.provider.ContactsContract.CommonDataKinds.Relation.TYPE_SISTER, "Irmã"),
        FieldType(android.provider.ContactsContract.CommonDataKinds.Relation.TYPE_RELATIVE, "Familiar"),
        FieldType(android.provider.ContactsContract.CommonDataKinds.Relation.TYPE_FRIEND, "Amigo(a)"),
        FieldType(android.provider.ContactsContract.CommonDataKinds.Relation.TYPE_ASSISTANT, "Assistente"),
        FieldType(android.provider.ContactsContract.CommonDataKinds.Relation.TYPE_MANAGER, "Chefe"),
        FieldType(android.provider.ContactsContract.CommonDataKinds.Relation.TYPE_REFERRED_BY, "Recomendado por"),
    )),
    Note("Nota", android.provider.ContactsContract.CommonDataKinds.Note.CONTENT_ITEM_TYPE, emptyList()),
    Group("Grupos", GroupMembership.CONTENT_ITEM_TYPE, emptyList());

    companion object {
        fun forMime(mime: String?): FieldKind? = entries.firstOrNull { it.mime == mime }
    }
}

/**
 * One piece of information stored in the phone's contacts.
 * [dataId] is the row in the phone's contacts, used to change or delete it.
 * [secondary] holds the job title for an organisation; [type] is Android's type or IM protocol.
 */
data class ContactField(
    val dataId: Long,
    val kind: FieldKind,
    val type: Int,
    val label: String,
    val value: String,
    val secondary: String? = null,
)

data class ContactGroup(val id: Long, val title: String)

/** Text helpers with no Android dependencies, so they can be unit-tested. */
object ContactFormat {

    private val monthsPt = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")

    /** Contact dates look like "1985-03-12", "--03-12", "12/03/1985" or "20250312". */
    fun parseDate(raw: String): Pair<MonthDay, Int?>? = runCatching {
        val s = raw.trim()
        when {
            s.startsWith("--") -> MonthDay.parse(s.take(7)) to null
            Regex("\\d{4}-\\d{2}-\\d{2}.*").matches(s) -> LocalDate.parse(s.take(10)).let { MonthDay.from(it) to it.year }
            Regex("\\d{1,2}/\\d{1,2}/\\d{4}").matches(s) -> s.split('/').let {
                MonthDay.of(it[1].toInt(), it[0].toInt()) to it[2].toInt()
            }
            Regex("\\d{8}").matches(s) -> LocalDate.of(s.take(4).toInt(), s.substring(4, 6).toInt(), s.takeLast(2).toInt())
                .let { MonthDay.from(it) to it.year }
            else -> null
        }
    }.getOrNull()

    /** "12 mar 1980", or "12 mar" when the year is unknown. */
    fun formatDate(raw: String): String {
        val (md, year) = parseDate(raw) ?: return raw
        return "${md.dayOfMonth} ${monthsPt[md.monthValue - 1]}" + (year?.let { " $it" } ?: "")
    }

    /**
     * What the person types ("12/03/1980", "12-3", "12.03") turned into the format the phone stores:
     * "1980-03-12", or "--03-12" without a year. Null when it is not a real date.
     */
    fun dateForContacts(typed: String): String? = runCatching {
        val parts = typed.trim().split('/', '-', '.', ' ').filter { it.isNotBlank() }
        if (parts.size !in 2..3 || parts.any { p -> !p.all(Char::isDigit) }) return null
        val day = parts[0].toInt()
        val month = parts[1].toInt()
        if (parts.size == 3) {
            var year = parts[2].toInt()
            if (parts[2].length == 2) year += if (year > LocalDate.now().year % 100) 1900 else 2000
            LocalDate.of(year, month, day).toString()
        } else {
            val md = MonthDay.of(month, day)
            "--%02d-%02d".format(md.monthValue, md.dayOfMonth)
        }
    }.getOrNull()

    /** A stored date shown back in the editor as "12/03/1980". */
    fun dateForEditing(raw: String): String {
        val (md, year) = parseDate(raw) ?: return raw
        return "%02d/%02d".format(md.dayOfMonth, md.monthValue) + (year?.let { "/$it" } ?: "")
    }

    /**
     * Phone number in the international form WhatsApp links need ("351912345678").
     * Portuguese numbers written without a country code get +351.
     */
    fun whatsAppNumber(raw: String, defaultCountry: String = "351"): String? {
        val trimmed = raw.trim()
        val digits = trimmed.filter(Char::isDigit)
        if (digits.length < 6) return null
        return when {
            trimmed.startsWith("+") -> digits
            digits.startsWith("00") -> digits.drop(2)
            digits.length == 9 -> defaultCountry + digits
            else -> digits
        }
    }

    /** Initials for a photo-less card, ignoring emoji and symbols in the name. */
    fun initials(name: String): String {
        val words = name.split(' ').map { w -> w.filter { it.isLetter() } }.filter { it.isNotEmpty() }
        val first = words.firstOrNull()?.take(1).orEmpty()
        val last = if (words.size > 1) words.last().take(1) else ""
        return (first + last).uppercase()
    }
}
