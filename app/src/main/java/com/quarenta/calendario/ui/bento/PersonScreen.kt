package com.quarenta.calendario.ui.bento

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.app.Activity
import android.content.ContentUris
import android.media.RingtoneManager
import android.provider.ContactsContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.quarenta.calendario.data.ContactField
import com.quarenta.calendario.data.ContactFormat
import com.quarenta.calendario.data.ContactGroup
import com.quarenta.calendario.data.FieldKind
import com.quarenta.calendario.data.WHATSAPP_BUSINESS_MIME
import kotlinx.coroutines.launch
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quarenta.calendario.data.CalendarEvent
import com.quarenta.calendario.data.ContactsRepository
import com.quarenta.calendario.data.Dedup
import com.quarenta.calendario.data.LabeledValue
import com.quarenta.calendario.data.Person
import com.quarenta.calendario.data.PersonDetails
import com.quarenta.calendario.data.PersonField
import com.quarenta.calendario.data.PersonNote
import com.quarenta.calendario.ui.components.BentoTile
import com.quarenta.calendario.ui.components.IconTile
import com.quarenta.calendario.ui.composeColor
import com.quarenta.calendario.ui.dayHeading
import com.quarenta.calendario.ui.localDate
import com.quarenta.calendario.ui.monthShort
import com.quarenta.calendario.ui.theme.Bento
import com.quarenta.calendario.ui.timeRange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Events that mention this person by full name (or first + last name) in the title or notes. */
fun eventsWith(person: Person, events: List<CalendarEvent>): List<CalendarEvent> {
    val tokens = Dedup.normalize(person.name).split(' ').filter { it.length >= 2 }
    if (tokens.isEmpty()) return emptyList()
    val full = tokens.joinToString(" ")
    val firstLast = if (tokens.size >= 2) "${tokens.first()} ${tokens.last()}" else full
    return events.filter { e ->
        val text = " " + Dedup.normalize(e.title + " " + (e.description ?: "")) + " "
        text.contains(" $full ") || text.contains(" $firstLast ") ||
            (tokens.size >= 2 && text.contains(" ${tokens.first()} ") && text.contains(" ${tokens.last()} "))
    }
}

/** What the bottom half shows: the card, the list of things to add, or one editor. */
private sealed interface Panel {
    data object Card : Panel
    data object Picker : Panel
    data class Edit(val kind: FieldKind, val presetType: Int?, val existing: ContactField?) : Panel
    data object Custom : Panel
}

@Composable
fun PersonScreen(
    person: Person,
    events: List<CalendarEvent>,
    noteFlow: Flow<PersonNote?>,
    fieldsFlow: Flow<List<PersonField>>,
    onSaveNote: (String) -> Unit,
    onAddField: (String, String) -> Unit,
    onDeleteField: (Long) -> Unit,
    onOpenEvent: (CalendarEvent) -> Unit,
    onToggleFavourite: () -> Unit,
    withWriteAccess: (() -> Unit) -> Unit,
    onClose: () -> Unit,
) {
    var panel by remember(person.id) { mutableStateOf<Panel>(Panel.Card) }
    BackHandler { if (panel != Panel.Card) panel = Panel.Card else onClose() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { ContactsRepository(context) }
    val photo = rememberContactPhoto(person, full = true)
    var reload by remember { mutableIntStateOf(0) }
    val details by produceState<PersonDetails?>(null, person.id, reload) {
        value = withContext(Dispatchers.IO) { runCatching { repo.details(person) }.getOrNull() }
    }
    val groups by produceState(emptyList<ContactGroup>(), person.id, panel) {
        if (panel is Panel.Edit && (panel as Panel.Edit).kind == FieldKind.Group) {
            value = withContext(Dispatchers.IO) { runCatching { repo.groupsFor(person) }.getOrDefault(emptyList()) }
        }
    }
    val ringtoneName by produceState<String?>(null, details?.ringtone) {
        value = withContext(Dispatchers.IO) {
            details?.ringtone?.let { uri ->
                runCatching { RingtoneManager.getRingtone(context, Uri.parse(uri))?.getTitle(context) }.getOrNull()
            }
        }
    }
    val savedNote by noteFlow.collectAsState(initial = null)
    val fields by fieldsFlow.collectAsState(initial = emptyList())
    var note by remember(person.lookupKey) { mutableStateOf<String?>(null) }
    LaunchedEffect(savedNote) { if (note == null && savedNote != null) note = savedNote?.notes }

    // Writes to the phone's contacts off the main thread, then reloads the card.
    fun write(action: (ContactsRepository) -> Boolean) = withWriteAccess {
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { action(repo) }.getOrDefault(false) }
            if (ok) { panel = Panel.Card; reload++ }
            else Toast.makeText(context, "Não foi possível gravar nos contactos", Toast.LENGTH_LONG).show()
        }
    }

    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        @Suppress("DEPRECATION")
        val picked = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        write { it.setRingtone(person, picked?.toString()) }
    }
    val pickRingtone = {
        ringtonePicker.launch(
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Som de toque de ${person.name.substringBefore(' ')}")
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, details?.ringtone?.let(Uri::parse)),
        )
    }

    // Save the notes when leaving the screen.
    val latestNote by rememberUpdatedState(note)
    val latestSaved by rememberUpdatedState(savedNote?.notes)
    DisposableEffect(person.lookupKey) {
        onDispose { latestNote?.let { if (it != (latestSaved ?: "")) onSaveNote(it) } }
    }

    val now = Instant.now()
    val upcoming = eventsWith(person, events).filter { it.end.isAfter(now) }.take(5)

    BoxWithConstraints(Modifier.fillMaxSize().background(Bento.Ground)) {
        val photoHeight = maxHeight * 0.5f

        // Top half: the photo stays fixed while the information scrolls underneath. Nothing ever covers it.
        Box(Modifier.fillMaxWidth().height(photoHeight).background(avatarColor(person.name))) {
            if (photo != null) {
                Image(
                    photo, contentDescription = "Fotografia de ${person.name}",
                    contentScale = ContentScale.Crop, filterQuality = FilterQuality.High,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    initials(person.name), color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            Box(
                Modifier.fillMaxWidth().height(180.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.62f)))),
            )
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp)) {
                IconButton(
                    onClick = { if (panel != Panel.Card) panel = Panel.Card else onClose() },
                    modifier = Modifier.size(44.dp).clip(CircleShape),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.35f), contentColor = Color.White),
                ) { Icon(Icons.Rounded.ArrowBack, contentDescription = "Voltar") }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = onToggleFavourite,
                    modifier = Modifier.size(44.dp).clip(CircleShape),
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.35f),
                        contentColor = if (person.starred) Bento.Yellow else Color.White,
                    ),
                ) {
                    Icon(
                        if (person.starred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (person.starred) "Tirar dos favoritos" else "Pôr nos favoritos",
                    )
                }
            }
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                Text(person.name, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 40.sp)
                val role = listOfNotNull(details?.jobTitle, details?.company).joinToString(" · ")
                if (role.isNotBlank()) Text(role, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                details?.fields?.firstOrNull { it.kind == FieldKind.Nickname }?.let {
                    Text("“${it.value}”", color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Bottom half: the card, or an editor in its place.
        Column(
            Modifier
                .padding(top = photoHeight)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val d = details
            when (val p = panel) {
                Panel.Picker -> AddPicker(
                    onPick = { choice ->
                        when (choice) {
                            is AddChoice.Field -> panel = Panel.Edit(choice.kind, choice.type, null)
                            AddChoice.Ringtone -> pickRingtone()
                            AddChoice.Custom -> panel = Panel.Custom
                        }
                    },
                    onClose = { panel = Panel.Card },
                )
                is Panel.Edit -> key(p) {
                    FieldEditor(
                        kind = p.kind, presetType = p.presetType, existing = p.existing, groups = groups,
                        onSave = { type, value, secondary ->
                            val existing = p.existing
                            write { r ->
                                if (existing == null) r.addField(person, p.kind, type, value, secondary)
                                else r.updateField(existing, type, value, secondary)
                            }
                        },
                        onDelete = p.existing?.let { f -> { write { it.deleteField(f) } } },
                        onClose = { panel = Panel.Card },
                    )
                }
                Panel.Custom -> CustomFieldEditor(
                    onSave = { label, value -> onAddField(label, value); panel = Panel.Card },
                    onClose = { panel = Panel.Card },
                )
                Panel.Card -> {
                    val phone = d?.phones?.firstOrNull()?.value
                    val email = d?.emails?.firstOrNull()?.value
                    val hasWhatsApp = d?.whatsAppDataId != null || phone?.let(ContactFormat::whatsAppNumber) != null
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        QuickAction(Icons.Rounded.Call, "Ligar", Pastels.Mint, phone != null, Modifier.weight(1f)) {
                            open(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                        }
                        QuickAction(Icons.Rounded.Chat, "WhatsApp", Pastels.WhatsApp, hasWhatsApp, Modifier.weight(1f)) {
                            openWhatsApp(context, d, phone)
                        }
                        QuickAction(Icons.Rounded.Message, "Mensagem", Pastels.Sky, phone != null, Modifier.weight(1f)) {
                            open(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")))
                        }
                        QuickAction(Icons.Rounded.Email, "Email", Pastels.Lavender, email != null, Modifier.weight(1f)) {
                            open(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")))
                        }
                        QuickAction(Icons.Rounded.Share, "Partilhar", Pastels.Peach, true, Modifier.weight(1f)) {
                            sharePerson(context, person, d)
                        }
                    }

                    upcoming.firstOrNull()?.let { next ->
                        BentoTile(Modifier.fillMaxWidth(), onClick = { onOpenEvent(next) }) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                DateBadge(next.start.localDate(), next.composeColor())
                                Column(Modifier.weight(1f)) {
                                    Text("PRÓXIMO · ${next.timeRange().uppercase()}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Bento.BlueText)
                                    Text(next.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        d?.birthday?.let { md ->
                            val today = LocalDate.now()
                            var next = md.atYear(today.year)
                            if (next.isBefore(today)) next = md.atYear(today.year + 1)
                            val days = ChronoUnit.DAYS.between(today, next)
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(Pastels.Rose.first).padding(16.dp),
                            ) {
                                Text("ANIVERSÁRIO", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Pastels.Rose.second, letterSpacing = 0.6.sp)
                                Text("${md.dayOfMonth} ${monthShort(next)}", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Pastels.Rose.second)
                                Text(
                                    when (days) { 0L -> "é hoje 🎉"; 1L -> "amanhã"; else -> "daqui a $days dias" } +
                                        (d.birthYear?.let { " · faz ${next.year - it}" } ?: ""),
                                    fontSize = 13.sp, color = Bento.Secondary,
                                )
                            }
                        }
                        if (upcoming.size > 1) {
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(Pastels.Sky.first).padding(16.dp)) {
                                Text("COMPROMISSOS", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Pastels.Sky.second, letterSpacing = 0.6.sp)
                                Text("${upcoming.size}", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = Pastels.Sky.second)
                                Text("marcados com ${person.name.substringBefore(' ')}", fontSize = 13.sp, color = Bento.Secondary)
                            }
                        }
                    }

                    // Everything stored in the phone's contact, one pastel card per kind.
                    d?.fields.orEmpty().groupBy { it.kind }.forEach { (kind, rows) ->
                        FieldCard(
                            title = kind.title, style = kind.pastel(rows.first().type), rows = rows,
                            display = { f -> if (f.kind == FieldKind.Event) ContactFormat.formatDate(f.value) else f.value },
                            onTap = { f -> if (!openField(context, f)) panel = Panel.Edit(f.kind, null, f) },
                            onEdit = { f -> panel = Panel.Edit(f.kind, null, f) },
                        )
                    }

                    // Ringtone, always shown so it can be changed in one tap.
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Pastels.Stone.first).clickable { pickRingtone() }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(28.dp).clip(CircleShape).background(Pastels.Stone.second), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.size(10.dp))
                        Text("SOM DE TOQUE", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Pastels.Stone.second, letterSpacing = 0.6.sp)
                        Spacer(Modifier.weight(1f))
                        Text(
                            if (d?.ringtone == null) "Predefinido" else ringtoneName ?: "Personalizado",
                            fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Bento.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Notes the person writes freely; saved in this app.
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Pastels.Butter.first).padding(top = 14.dp)) {
                        Text(
                            "AS MINHAS NOTAS", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Pastels.Butter.second,
                            letterSpacing = 0.6.sp, modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        TextField(
                            value = note ?: "",
                            onValueChange = { note = it },
                            placeholder = { Text("Escreve o que quiseres sobre ${firstName(person.name)}…") },
                            minLines = 3,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if ((note ?: "") != (savedNote?.notes ?: "")) {
                            TextButton(onClick = { note?.let(onSaveNote) }, modifier = Modifier.align(Alignment.End).padding(end = 8.dp)) {
                                Text("Guardar notas", fontWeight = FontWeight.Bold, color = Pastels.Butter.second)
                            }
                        }
                    }

                    // Free fields kept only in this app.
                    if (fields.isNotEmpty()) {
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Bento.Tile).padding(vertical = 6.dp)) {
                            fields.forEachIndexed { i, f ->
                                if (i > 0) HorizontalDivider(color = Bento.Hairline, modifier = Modifier.padding(start = 16.dp))
                                Row(
                                    Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(f.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Bento.Secondary)
                                        Text(f.value, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                    IconButton(onClick = { onDeleteField(f.id) }) {
                                        Icon(Icons.Rounded.Close, contentDescription = "Apagar campo ${f.label}", tint = Bento.Secondary)
                                    }
                                }
                            }
                        }
                    }

                    AddButton { panel = Panel.Picker }

                    if (upcoming.size > 1) {
                        Text("PRÓXIMOS COMPROMISSOS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Bento.Secondary, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
                        upcoming.drop(1).forEach { e ->
                            BentoTile(Modifier.fillMaxWidth(), onClick = { onOpenEvent(e) }) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    DateBadge(e.start.localDate(), e.composeColor())
                                    Column(Modifier.weight(1f)) {
                                        Text(e.timeRange(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Bento.Secondary)
                                        Text(e.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** First real word of the name, skipping emoji ("🌸 Ana" -> "Ana"). */
private fun firstName(name: String): String =
    name.split(' ').firstOrNull { w -> w.any { it.isLetter() } } ?: name

@Composable
private fun DateBadge(date: LocalDate, color: Color) {
    Column(
        Modifier.size(52.dp).clip(RoundedCornerShape(15.dp)).background(color),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(monthShort(date).uppercase(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
        Text("${date.dayOfMonth}", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 22.sp)
    }
}

private fun open(context: Context, intent: Intent): Boolean = runCatching { context.startActivity(intent) }.isSuccess

/** Opens what a value points to (call, email, map, site). False when there is nothing to open. */
private fun openField(context: Context, f: ContactField): Boolean = when (f.kind) {
    FieldKind.Phone -> open(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:${f.value}")))
    FieldKind.Email -> open(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${f.value}")))
    FieldKind.Address -> open(context, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(f.value))))
    FieldKind.Website -> open(
        context,
        Intent(Intent.ACTION_VIEW, Uri.parse(if (f.value.contains("://")) f.value else "https://${f.value}")),
    )
    else -> false
}

/** Opens the WhatsApp chat: through WhatsApp's own contact entry when synced, otherwise by number. */
private fun openWhatsApp(context: Context, d: PersonDetails?, phone: String?) {
    val id = d?.whatsAppDataId
    if (id != null) {
        val pkg = if (d.whatsAppMime == WHATSAPP_BUSINESS_MIME) "com.whatsapp.w4b" else "com.whatsapp"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, id), d.whatsAppMime)
            .setPackage(pkg)
        if (open(context, intent)) return
    }
    val number = phone?.let(ContactFormat::whatsAppNumber)
    if (number == null || !open(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number")))) {
        Toast.makeText(context, "Não foi possível abrir o WhatsApp", Toast.LENGTH_LONG).show()
    }
}

private fun sharePerson(context: Context, person: Person, d: PersonDetails?) {
    val body = buildString {
        appendLine(person.name)
        d?.fields?.filter { it.kind != FieldKind.Group && it.kind != FieldKind.Note }?.forEach { f ->
            val value = if (f.kind == FieldKind.Event) ContactFormat.formatDate(f.value) else f.value
            appendLine("${f.label}: $value" + (f.secondary?.let { " · $it" } ?: ""))
        }
    }
    open(context, Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body), "Partilhar contacto"))
}
