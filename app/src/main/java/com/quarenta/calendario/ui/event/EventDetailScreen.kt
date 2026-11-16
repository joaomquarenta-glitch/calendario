package com.quarenta.calendario.ui.event

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.style.TextOverflow
import com.quarenta.calendario.data.EventPlan
import com.quarenta.calendario.data.Person
import com.quarenta.calendario.ui.agenda.Highlights
import java.time.LocalDate
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quarenta.calendario.data.CalendarEvent
import com.quarenta.calendario.ui.components.BentoTile
import com.quarenta.calendario.ui.components.IconTile
import com.quarenta.calendario.ui.composeColor
import com.quarenta.calendario.ui.dayHeading
import com.quarenta.calendario.ui.localDate
import com.quarenta.calendario.ui.theme.Bento
import com.quarenta.calendario.ui.timeRange

/**
 * The event, shown inside our own app: never handed to another calendar.
 * With a photo of the person, the photo fills the top half; the bottom half is for the plan
 * (where, at what time, notes) and one tap to Waze or Google Maps.
 */
@Composable
fun EventDetailScreen(
    event: CalendarEvent,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    /** true deletes only this occurrence of a repeating appointment. */
    onDelete: (onlyThis: Boolean) -> Unit,
    person: Person? = null,
    photoFor: suspend (Person) -> Bitmap? = { null },
    birthYearFor: suspend (Person) -> Int? = { null },
    plan: EventPlan? = null,
    onSavePlan: (EventPlan) -> Unit = {},
    phoneFor: suspend (Person) -> String? = { null },
    weatherFor: suspend (CalendarEvent) -> com.quarenta.calendario.data.DayWeather? = { null },
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val color = event.composeColor()
    val today = LocalDate.now()
    val day = event.start.localDate()
    val birthday = Highlights.isBirthday(event.title, event.sources)
    val photoVersion by com.quarenta.calendario.data.PersonPhotos.version.collectAsState()
    val photo by produceState<Bitmap?>(null, person?.id, photoVersion) { value = person?.let { photoFor(it) } }
    val birthYear by produceState<Int?>(null, person?.id, birthday) {
        value = if (birthday && person != null) birthYearFor(person) else null
    }

    Column(Modifier.fillMaxSize().background(Bento.Ground)) {
        val bitmap = photo
        com.quarenta.calendario.ui.components.HdrWhile(com.quarenta.calendario.ui.components.isHdr(bitmap))
        if (bitmap != null) {
            // Top half: the photo, fixed; nothing is drawn over the face except the buttons.
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Image(
                    bitmap.asImageBitmap(), contentDescription = person?.name,
                    contentScale = ContentScale.Crop, filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundIcon(Icons.Rounded.ArrowBack, "Voltar", onClose)
                    Spacer(Modifier.weight(1f))
                    RoundIcon(Icons.Rounded.Share, "Partilhar") { shareEvent(context, event) }
                    if (event.canEdit) RoundIcon(Icons.Rounded.Edit, "Editar", onEdit)
                }
                Text(
                    if (birthday) Highlights.birthdayLabel(birthYear, day, today).replaceFirstChar { it.uppercase() }
                    else event.title,
                    color = Color.White, fontSize = 26.sp, lineHeight = 30.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
                )
            }
        } else {
            // No photo: the event's colour with its title.
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.linearGradient(listOf(lerp(color, Color.White, 0.18f), color, lerp(color, Color.Black, 0.35f))))
                    .statusBarsPadding().padding(start = 8.dp, end = 8.dp, bottom = 24.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RoundIcon(Icons.Rounded.ArrowBack, "Voltar", onClose)
                    Spacer(Modifier.weight(1f))
                    RoundIcon(Icons.Rounded.Share, "Partilhar") { shareEvent(context, event) }
                    if (event.canEdit) RoundIcon(Icons.Rounded.Edit, "Editar", onEdit)
                }
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (birthday) Highlights.birthdayLabel(birthYear, day, today).replaceFirstChar { it.uppercase() }
                        else dayHeading(day),
                        color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val kind = com.quarenta.calendario.data.EventKinds.of(event.title, event.sources)
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color.White.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(kind.icon(), contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp)) }
                        Text(event.title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 34.sp, modifier = Modifier.weight(1f))
                    }
                    if (!event.allDay) Text(event.timeRange(), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Bottom half: the plan.
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (bitmap != null && !birthday) {
                Text(
                    dayHeading(day) + if (event.allDay) "" else " · ${event.timeRange()}",
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Bento.Secondary, modifier = Modifier.padding(start = 4.dp),
                )
            }
            if (birthday && person != null) {
                WishesCard(
                    person = person,
                    turns = birthYear?.let { day.year - it }?.takeIf { it in 1..130 },
                    daysLeft = java.time.temporal.ChronoUnit.DAYS.between(today, day).toInt().coerceAtLeast(0),
                    phoneFor = phoneFor,
                )
                GiftIdeasCard(person)
            }
            val forecast by produceState<com.quarenta.calendario.data.DayWeather?>(null, event.id, event.location) { value = weatherFor(event) }
            forecast?.let { WeatherCard(it) }
            PlanCard(
                key = Highlights.planKey(event.id, day),
                title = if (birthday) "A FESTA" else "O PLANO",
                plan = plan,
                defaultPlace = event.location.orEmpty(),
                accent = color,
                onSave = onSavePlan,
            )
            val place = (plan?.place?.takeIf { it.isNotBlank() } ?: event.location).orEmpty().trim()
            if (place.isNotEmpty()) MiniMap(place, color)
            event.description?.let { notes ->
                BentoTile(Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconTile(Icons.Rounded.Notes, Bento.Orange)
                        Column(Modifier.weight(1f)) {
                            Text("NOTAS DO CONVITE", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Bento.Secondary)
                            Text(notes, fontSize = 15.sp, lineHeight = 22.sp)
                        }
                    }
                }
            }
            if (event.canEdit) {
                Text(
                    "Apagar compromisso", color = Bento.RedText, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally).clip(RoundedCornerShape(50))
                        .clickable { confirmDelete = true }.padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Apagar \"${event.title}\"?") },
            text = {
                Text(
                    if (event.repeats) "Este compromisso repete-se. Apagas só este dia ou todos?"
                    else "O compromisso é apagado deste calendário em todos os teus dispositivos.",
                )
            },
            confirmButton = {
                if (event.repeats) {
                    Row {
                        TextButton(onClick = { confirmDelete = false; onDelete(true) }) { Text("Só este", color = Bento.RedText) }
                        TextButton(onClick = { confirmDelete = false; onDelete(false) }) { Text("Todos", color = Bento.RedText) }
                    }
                } else {
                    TextButton(onClick = { confirmDelete = false; onDelete(false) }) { Text("Apagar", color = Bento.RedText) }
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

/** Where, at what time, and notes, kept in this app; with one tap to Waze or Google Maps. */
@Composable
private fun PlanCard(
    key: String,
    title: String,
    plan: EventPlan?,
    defaultPlace: String,
    accent: Color,
    onSave: (EventPlan) -> Unit,
) {
    val context = LocalContext.current
    var place by remember(key, plan) { mutableStateOf(plan?.place?.ifBlank { null } ?: defaultPlace) }
    var time by remember(key, plan) { mutableStateOf(plan?.time.orEmpty()) }
    var notes by remember(key, plan) { mutableStateOf(plan?.notes.orEmpty()) }
    val changed = place != (plan?.place?.ifBlank { null } ?: defaultPlace) || time != plan?.time.orEmpty() || notes != plan?.notes.orEmpty()
    val focus = LocalFocusManager.current

    Text(title, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Bento.Secondary, letterSpacing = 0.8.sp, modifier = Modifier.padding(start = 4.dp))
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Bento.Tile).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlanField(Icons.Rounded.LocationOn, "Onde", "Morada ou nome do sítio", place, Bento.Green) { place = it }
        PlanField(Icons.Rounded.Schedule, "A que horas", "ex.: 20:00", time, Bento.Blue) { time = it }
        PlanField(Icons.Rounded.Notes, "Notas", "Prenda, quem vai, o que levar...", notes, Bento.Orange, singleLine = false) { notes = it }
        if (changed) {
            Box(
                Modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(16.dp)).background(accent)
                    .clickable {
                        onSave(EventPlan(key = key, place = place.trim(), time = time.trim(), notes = notes.trim()))
                        focus.clearFocus()
                    },
                contentAlignment = Alignment.Center,
            ) { Text("Guardar", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }
    if (place.isNotBlank()) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NavButton("Waze", Color(0xFF33CCFF), Modifier.weight(1f)) { openUrl(context, Highlights.wazeUrl(place)) }
            NavButton("Google Maps", Color(0xFF34A853), Modifier.weight(1f)) { openMaps(context, place) }
        }
    }
}

@Composable
private fun PlanField(
    icon: ImageVector,
    label: String,
    hint: String,
    value: String,
    tint: Color,
    singleLine: Boolean = true,
    onChange: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, tint, 40.dp, 22.dp)
        Spacer(Modifier.width(12.dp))
        OutlinedTextField(
            value = value, onValueChange = onChange, label = { Text(label) },
            placeholder = { Text(hint, color = Bento.Secondary) },
            singleLine = singleLine, minLines = if (singleLine) 1 else 2,
            shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NavButton(text: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(56.dp).clip(RoundedCornerShape(18.dp)).background(color).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Navigation, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}

private fun openUrl(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
}

/** Google Maps navigation when the app is there, otherwise directions in the browser. */
private fun openMaps(context: Context, place: String) {
    val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(place.trim())))
        .setPackage("com.google.android.apps.maps")
    if (runCatching { context.startActivity(nav) }.isFailure) openUrl(context, Highlights.mapsUrl(place))
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.padding(4.dp).size(44.dp).clip(CircleShape),
        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.18f), contentColor = Color.White),
    ) { Icon(icon, contentDescription = label) }
}

fun shareEvent(context: Context, event: CalendarEvent) {
    val body = buildString {
        appendLine(event.title)
        appendLine("${dayHeading(event.start.localDate())} · ${event.timeRange()}")
        event.location?.let { appendLine(it) }
        event.description?.let { appendLine(); appendLine(it) }
    }
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body)
    context.startActivity(Intent.createChooser(send, "Partilhar compromisso"))
}

/** The forecast for the day of the appointment, with a word of warning when it will rain. */
@Composable
private fun WeatherCard(w: com.quarenta.calendario.data.DayWeather) {
    BentoTile(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(w.symbol, fontSize = 36.sp)
            Column(Modifier.weight(1f)) {
                Text("PREVISÃO", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Bento.Secondary)
                Text("${w.label} · ${w.range}", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Bento.Ink)
                if (com.quarenta.calendario.data.Weather.isWet(w.code)) {
                    Text("Leva guarda-chuva, e se for na esplanada pensa num plano B.", fontSize = 13.sp, color = Bento.Secondary)
                }
            }
        }
    }
}
