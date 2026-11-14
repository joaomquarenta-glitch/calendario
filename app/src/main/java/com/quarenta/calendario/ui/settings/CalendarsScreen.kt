package com.quarenta.calendario.ui.settings

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
import com.quarenta.calendario.data.CalendarChoice
import com.quarenta.calendario.data.CalendarInfo
import com.quarenta.calendario.ui.theme.Bento

/** Turn each of the phone's calendars on or off, for this app only. */
@Composable
fun CalendarsScreen(
    load: suspend () -> List<CalendarInfo>,
    hidden: Set<Long>,
    onShown: (Long, Boolean) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val calendars by produceState<List<CalendarInfo>?>(null) { value = load() }

    Column(
        Modifier.fillMaxSize().background(Bento.Ground).verticalScroll(rememberScrollState())
            .statusBarsPadding().navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(44.dp).clip(CircleShape).background(Bento.Tile)) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar", tint = Bento.Ink)
        }
        Text("Calendários", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Bento.Ink)
        Text(
            "Escolhe o que aparece na FortyCalendar. Os calendários ficam iguais nas outras apps.",
            fontSize = 15.sp, color = Bento.Secondary,
        )
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
