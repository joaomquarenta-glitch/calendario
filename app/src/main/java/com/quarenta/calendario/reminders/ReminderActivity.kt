package com.quarenta.calendario.reminders

import android.app.KeyguardManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quarenta.calendario.MainActivity
import com.quarenta.calendario.data.ContactFormat
import com.quarenta.calendario.ui.PT
import com.quarenta.calendario.ui.dayHeading
import com.quarenta.calendario.ui.theme.Bento
import com.quarenta.calendario.ui.theme.CalendarioTheme
import com.quarenta.calendario.ui.theme.ThemeState
import com.quarenta.calendario.ui.theme.Themes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The full-screen reminder: the person's photo in the top half, the event in the bottom half,
 * on the colour the person chose. Shows over the lock screen and turns the screen on.
 */
class ReminderActivity : ComponentActivity() {

    private var alert by mutableStateOf<ReminderAlert?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        ThemeState.selectedId = getSharedPreferences("forty", MODE_PRIVATE).getString("theme", Themes.SYSTEM) ?: Themes.SYSTEM
        ThemeState.systemDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        enableEdgeToEdge()
        alert = ReminderIntents.get(intent)
        if (alert == null) { finish(); return }
        setContent {
            CalendarioTheme {
                alert?.let { a ->
                    ReminderScreen(
                        alert = a,
                        color = Color(ReminderScheduler.color(this)),
                        onOpen = { open(a) },
                        onSnooze = { minutes -> snooze(a, minutes) },
                        onDismiss = { dismiss(a) },
                        onMessage = { person, text -> sendMessage(person, text) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        ReminderIntents.get(intent)?.let { alert = it }
    }

    private fun dismiss(a: ReminderAlert) {
        ReminderNotifier.cancel(this, a)
        finish()
    }

    private fun snooze(a: ReminderAlert, minutes: Int) {
        ReminderNotifier.cancel(this, a)
        ReminderScheduler.snooze(this, a, minutes)
        Toast.makeText(this, "Volto a lembrar daqui a ${ReminderPlan.snoozeLabel(minutes)}", Toast.LENGTH_SHORT).show()
        finish()
    }

    /** Opens the event inside FortyCalendar (never in another calendar app). */
    private fun open(a: ReminderAlert) {
        ReminderNotifier.cancel(this, a)
        afterUnlock {
            val intent = Intent(Intent.ACTION_VIEW)
                .setClass(this, MainActivity::class.java)
                .setData(android.content.ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, a.eventId))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            finish()
        }
    }

    /** WhatsApp to the person's mobile when there is one, otherwise the phone's "Share" list. */
    private fun sendMessage(person: ReminderPerson?, text: String) {
        afterUnlock {
            val number = person?.phone?.let(ContactFormat::whatsAppNumber)
            val sent = number != null && start(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number?text=" + Uri.encode(text))))
            if (!sent) {
                val share = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                start(Intent.createChooser(share, "Enviar mensagem"))
            }
        }
    }

    private fun start(intent: Intent): Boolean = try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: ActivityNotFoundException) { false }

    /** Asks for the PIN or fingerprint first when the phone is locked. */
    private fun afterUnlock(action: () -> Unit) {
        val km = getSystemService(KeyguardManager::class.java)
        if (!km.isKeyguardLocked) { action(); return }
        km.requestDismissKeyguard(this, object : KeyguardManager.KeyguardDismissCallback() {
            override fun onDismissSucceeded() = action()
        })
    }
}

@Composable
private fun ReminderScreen(
    alert: ReminderAlert,
    color: Color,
    onOpen: () -> Unit,
    onSnooze: (Int) -> Unit,
    onDismiss: () -> Unit,
    onMessage: (ReminderPerson?, String) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var person by remember(alert.eventId) { mutableStateOf<ReminderPerson?>(null) }
    var looked by remember(alert.eventId) { mutableStateOf(false) }
    LaunchedEffect(alert.eventId) {
        person = withContext(Dispatchers.IO) { ReminderPeople.find(context, alert) }
        looked = true
    }
    val zone = ZoneId.systemDefault()
    val begin = Instant.ofEpochMilli(alert.beginMs)
    val time = DateTimeFormatter.ofPattern("HH:mm", PT)
    val timeText = if (alert.allDay) "Todo o dia" else "${time.format(begin.atZone(zone))} – ${time.format(Instant.ofEpochMilli(alert.endMs).atZone(zone))}"

    Column(
        Modifier.fillMaxSize().background(color).statusBarsPadding().navigationBarsPadding().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Top half: the person, never covered by anything.
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Color.White.copy(alpha = 0.16f))) {
            val photo = person?.photo
            if (photo != null) {
                Image(
                    photo.asImageBitmap(), contentDescription = person?.person?.name,
                    contentScale = ContentScale.Crop, filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f)),
                    ),
                )
            } else if (looked) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(132.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.92f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        val initials = person?.person?.name?.let(ContactFormat::initials).orEmpty()
                        if (initials.isNotEmpty()) {
                            Text(initials, fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, color = color)
                        } else {
                            Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = color, modifier = Modifier.size(68.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        dayHeading(begin.atZone(zone).toLocalDate()), color = Color.White,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }
            person?.let { p ->
                Text(
                    p.person.name, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 34.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
                )
            }
            Row(
                Modifier.align(Alignment.TopStart).padding(14.dp).clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.28f)).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Lembrete", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Bottom half: the event and what to do.
        Column(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(32.dp)).background(Bento.Tile).padding(20.dp),
        ) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    ReminderPlan.whenText(alert.beginMs, System.currentTimeMillis(), alert.allDay),
                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 12.dp, vertical = 5.dp),
                )
                Text(alert.title, fontSize = 28.sp, lineHeight = 32.sp, fontWeight = FontWeight.ExtraBold, color = Bento.Ink)
                InfoLine(Icons.Rounded.Schedule, timeText, color)
                alert.location?.let { InfoLine(Icons.Rounded.LocationOn, it, color) }

                Label("ADIAR")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReminderPlan.snoozeMinutes.forEach { m -> Chip(ReminderPlan.snoozeLabel(m), color) { onSnooze(m) } }
                }
                Label(person?.let { "MENSAGEM RÁPIDA PARA ${it.firstName.uppercase()}" } ?: "MENSAGEM RÁPIDA")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReminderPlan.quickMessages.forEach { msg -> Chip(msg, color, Icons.Rounded.Send) { onMessage(person, msg) } }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigButton("Dispensar", Bento.Ground, Bento.Ink, Modifier.weight(1f), onDismiss)
                BigButton("Abrir", color, Color.White, Modifier.weight(1f), onOpen)
            }
        }
    }
}

@Composable
private fun InfoLine(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Bento.Ink)
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Bento.Secondary,
        letterSpacing = 0.8.sp, modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun Chip(text: String, color: Color, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = color, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Bento.Ink)
    }
}

@Composable
private fun BigButton(text: String, background: Color, content: Color, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.height(60.dp).clip(RoundedCornerShape(20.dp)).background(background).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = content, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold) }
}
