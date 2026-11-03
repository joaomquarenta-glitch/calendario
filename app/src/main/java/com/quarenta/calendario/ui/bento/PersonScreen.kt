package com.quarenta.calendario.ui.bento

import android.content.Context
import android.content.Intent
import android.net.Uri
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
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val photo = rememberContactPhoto(person, full = true)
    val details by produceState<PersonDetails?>(null, person.id) {
        value = withContext(Dispatchers.IO) { runCatching { ContactsRepository(context).details(person) }.getOrNull() }
    }
    val savedNote by noteFlow.collectAsState(initial = null)
    val fields by fieldsFlow.collectAsState(initial = emptyList())
    var note by remember(person.lookupKey) { mutableStateOf<String?>(null) }
    LaunchedEffect(savedNote) { if (note == null && savedNote != null) note = savedNote?.notes }
    var addingField by remember { mutableStateOf(false) }

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

        // Top half: the photo stays fixed while the information scrolls underneath.
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
                    onClick = onClose,
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
            }
        }

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
            val phone = d?.phones?.firstOrNull()?.value
            val email = d?.emails?.firstOrNull()?.value
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionButton(Icons.Rounded.Call, "Ligar", Bento.Green, Modifier.weight(1f), enabled = phone != null) {
                    open(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                }
                ActionButton(Icons.Rounded.Message, "Mensagem", Bento.Blue, Modifier.weight(1f), enabled = phone != null) {
                    open(context, Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$phone")))
                }
                ActionButton(Icons.Rounded.Email, "Email", Bento.Indigo, Modifier.weight(1f), enabled = email != null) {
                    open(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")))
                }
                ActionButton(Icons.Rounded.Share, "Partilhar", Color(0xFFC93400), Modifier.weight(1f), enabled = true) {
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
                    BentoTile(Modifier.weight(1f)) {
                        Text("ANIVERSÁRIO", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Bento.Secondary)
                        Text("${md.dayOfMonth} ${monthShort(next)}", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFC93400))
                        Text(
                            when (days) { 0L -> "é hoje"; 1L -> "amanhã"; else -> "daqui a $days dias" } +
                                (d.birthYear?.let { " · faz ${next.year - it}" } ?: ""),
                            fontSize = 13.sp, color = Bento.Secondary,
                        )
                    }
                }
                if (upcoming.size > 1) {
                    BentoTile(Modifier.weight(1f)) {
                        Text("COMPROMISSOS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Bento.Secondary)
                        Text("${upcoming.size}", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Bento.BlueText)
                        Text("marcados com ${person.name.substringBefore(' ')}", fontSize = 13.sp, color = Bento.Secondary)
                    }
                }
            }

            val contactRows = buildList {
                d?.phones?.forEach { add(it to "tel") }
                d?.emails?.forEach { add(it to "mailto") }
                d?.addresses?.forEach { add(it to "geo") }
            }
            if (contactRows.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Bento.Tile).padding(horizontal = 16.dp)) {
                    contactRows.forEachIndexed { i, (lv, scheme) ->
                        ContactRow(lv) {
                            val uri = when (scheme) {
                                "tel" -> Uri.parse("tel:${lv.value}")
                                "mailto" -> Uri.parse("mailto:${lv.value}")
                                else -> Uri.parse("geo:0,0?q=" + Uri.encode(lv.value))
                            }
                            open(context, Intent(if (scheme == "tel") Intent.ACTION_DIAL else Intent.ACTION_VIEW, uri))
                        }
                        if (i < contactRows.lastIndex) HorizontalDivider(color = Bento.Hairline)
                    }
                }
            }

            // Notes the person writes freely; saved in this app.
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color(0xFFFFF8E1)).padding(top = 14.dp)) {
                Text("NOTAS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF7A5A00), modifier = Modifier.padding(horizontal = 16.dp))
                TextField(
                    value = note ?: "",
                    onValueChange = { note = it },
                    placeholder = { Text("Escreve o que quiseres sobre ${person.name.substringBefore(' ')}…") },
                    minLines = 3,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if ((note ?: "") != (savedNote?.notes ?: "")) {
                    TextButton(onClick = { note?.let(onSaveNote) }, modifier = Modifier.align(Alignment.End).padding(end = 8.dp)) {
                        Text("Guardar notas", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Custom fields, Bento style.
            fields.forEach { f ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Bento.Tile).padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(f.label.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Bento.Secondary)
                        Text(f.value, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    }
                    IconButton(onClick = { onDeleteField(f.id) }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Apagar campo ${f.label}", tint = Bento.Secondary)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(20.dp)).background(Bento.Tile).clickable { addingField = true },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = Bento.Blue)
                Spacer(Modifier.size(6.dp))
                Text("Adicionar campo", color = Bento.BlueText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

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
            Spacer(Modifier.height(24.dp))
        }
    }

    if (addingField) {
        var label by remember { mutableStateOf("") }
        var value by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { addingField = false },
            title = { Text("Novo campo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(label, { label = it }, label = { Text("Nome (ex.: Filhos, Prefere)") }, singleLine = true)
                    OutlinedTextField(value, { value = it }, label = { Text("Valor") })
                }
            },
            confirmButton = {
                TextButton(onClick = { onAddField(label, value); addingField = false }, enabled = label.isNotBlank() || value.isNotBlank()) {
                    Text("Adicionar")
                }
            },
            dismissButton = { TextButton(onClick = { addingField = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, color: Color, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    Column(
        modifier.height(76.dp).clip(RoundedCornerShape(20.dp))
            .background(if (enabled) color else Bento.Hairline)
            .clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) Color.White else Bento.Secondary, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (enabled) Color.White else Bento.Secondary)
    }
}

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

@Composable
private fun ContactRow(lv: LabeledValue, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp)) {
        Text(lv.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Bento.Secondary)
        Text(lv.value, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Bento.BlueText)
    }
}

private fun open(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent) }
}

private fun sharePerson(context: Context, person: Person, d: PersonDetails?) {
    val body = buildString {
        appendLine(person.name)
        d?.phones?.forEach { appendLine("${it.label}: ${it.value}") }
        d?.emails?.forEach { appendLine("${it.label}: ${it.value}") }
        d?.addresses?.forEach { appendLine("${it.label}: ${it.value}") }
    }
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body), "Partilhar contacto"))
}
