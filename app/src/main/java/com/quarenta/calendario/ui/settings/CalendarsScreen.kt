package com.quarenta.calendario.ui.settings

import com.quarenta.calendario.ui.components.asHeading
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.launch
import com.quarenta.calendario.data.CalendarChoice
import com.quarenta.calendario.data.CalendarInfo
import com.quarenta.calendario.data.Subscription
import com.quarenta.calendario.ui.theme.Bento

/** Turn each of the phone's calendars on or off, for this app only. */
@Composable
fun CalendarsScreen(
    load: suspend () -> List<CalendarInfo>,
    hidden: Set<Long>,
    onShown: (Long, Boolean) -> Unit,
    onClose: () -> Unit,
    subscriptions: List<Subscription> = emptyList(),
    prefillUrl: String? = null,
    onSubscribe: suspend (url: String, name: String, color: Int) -> String? = { _, _, _ -> null },
    onUnsubscribe: (Subscription) -> Unit = {},
) {
    BackHandler(onBack = onClose)
    // Read again when a link is added or removed, so its calendar shows up here.
    val calendars by produceState<List<CalendarInfo>?>(null, subscriptions.map { it.calendarId }) { value = load() }

    Column(
        Modifier.fillMaxSize().background(Bento.Ground).verticalScroll(rememberScrollState())
            .statusBarsPadding().navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(44.dp).clip(CircleShape).background(Bento.Tile)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar", tint = Bento.Ink)
        }
        Text("Calendários", style = androidx.compose.material3.MaterialTheme.typography.displaySmall, modifier = Modifier.asHeading())
        Text(
            "Escolhe o que aparece na FortyCalendar. Os calendários ficam iguais nas outras apps.",
            fontSize = 15.sp, color = Bento.Secondary,
        )
        SubscribeCard(subscriptions, prefillUrl, onSubscribe, onUnsubscribe)

        val list = calendars
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Bento.Blue)
            }
            list.isEmpty() -> Text("Não há calendários, ou a app ainda não tem autorização para os ver.", fontSize = 15.sp, color = Bento.Secondary)
            else -> CalendarChoice.byAccount(list).forEach { (account, cals) ->
                Text(
                    account.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Bento.Secondary,
                    letterSpacing = 0.6.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp, top = 8.dp),
                )
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Bento.Tile)) {
                    cals.forEachIndexed { i, cal ->
                        if (i > 0) HorizontalDivider(Modifier.padding(start = 56.dp), color = Bento.Secondary.copy(alpha = 0.15f))
                        val shown = cal.id !in hidden
                        Row(
                            Modifier.fillMaxWidth().clickable { onShown(cal.id, !shown) }.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(24.dp).clip(CircleShape).background(Color(cal.color).copy(alpha = if (shown) 1f else 0.35f)))
                            Spacer(Modifier.width(16.dp))
                            Text(
                                cal.name.ifBlank { "Sem nome" }, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                                color = if (shown) Bento.Ink else Bento.Secondary, modifier = Modifier.weight(1f),
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                            Switch(
                                checked = shown, onCheckedChange = { onShown(cal.id, it) },
                                colors = SwitchDefaults.colors(checkedTrackColor = Color(cal.color).copy(alpha = 1f)),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val subscriptionColors = listOf(0xFF0A84FF, 0xFF30D158, 0xFFFF9F0A, 0xFFBF5AF2, 0xFFFF375F, 0xFF64D2FF, 0xFFFFD60A, 0xFFAC8E68)

/** Links to calendars on the internet: school, club, a shared iCloud calendar, holidays. */
@Composable
private fun SubscribeCard(
    subscriptions: List<Subscription>,
    prefillUrl: String?,
    onSubscribe: suspend (String, String, Int) -> String?,
    onUnsubscribe: (Subscription) -> Unit,
) {
    var url by rememberSaveable(prefillUrl) { mutableStateOf(prefillUrl.orEmpty()) }
    var name by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    val now = System.currentTimeMillis()

    Text(
        "LINKS ASSINADOS", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Bento.Secondary,
        letterSpacing = 0.6.sp, modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Bento.Tile).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        subscriptions.forEach { sub ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(24.dp).clip(CircleShape).background(Color(sub.color)))
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(sub.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Bento.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        sub.lastError ?: "${sub.eventCount} compromissos · ${ago(now - sub.lastSync)}",
                        fontSize = 13.sp, color = if (sub.lastError != null) Bento.RedText else Bento.Secondary,
                    )
                }
                if (confirmRemove == sub.calendarId) {
                    TextButton(onClick = { confirmRemove = null; onUnsubscribe(sub) }) {
                        Text("Remover", color = Bento.RedText, fontWeight = FontWeight.Bold)
                    }
                } else {
                    IconButton(onClick = { confirmRemove = sub.calendarId }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Deixar de assinar", tint = Bento.Secondary)
                    }
                }
            }
        }
        if (subscriptions.isEmpty()) {
            Text(
                "Cola o link de um calendário da internet (webcal:// ou .ics): horário da escola, jogos do clube, " +
                    "um calendário partilhado do iCloud. A app atualiza-o sozinha.",
                fontSize = 15.sp, color = Bento.Secondary, lineHeight = 21.sp,
            )
        }
        OutlinedTextField(
            value = url, onValueChange = { url = it; error = null }, singleLine = true,
            label = { Text("Link do calendário") },
            placeholder = { Text("webcal://…") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = name, onValueChange = { name = it }, singleLine = true,
            label = { Text("Nome (opcional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, fontSize = 14.sp, color = Bento.RedText) }
        Button(
            onClick = {
                busy = true
                error = null
                scope.launch {
                    val color = subscriptionColors[subscriptions.size % subscriptionColors.size].toInt()
                    val result = onSubscribe(url, name, color)
                    busy = false
                    if (result == null) { url = ""; name = "" } else error = result
                }
            },
            enabled = !busy && url.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Bento.Blue),
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            else Text("Assinar", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun ago(ms: Long): String {
    val min = ms / 60_000
    return when {
        min < 1 -> "atualizado agora"
        min < 60 -> "atualizado há $min min"
        min < 60 * 24 -> "atualizado há ${min / 60} h"
        else -> "atualizado há ${min / (60 * 24)} dias"
    }
}
