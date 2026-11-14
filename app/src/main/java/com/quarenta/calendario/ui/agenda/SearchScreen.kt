package com.quarenta.calendario.ui.agenda

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quarenta.calendario.data.CalendarEvent
import com.quarenta.calendario.data.EventSearch
import com.quarenta.calendario.ui.components.SectionLabel
import com.quarenta.calendario.ui.composeColor
import com.quarenta.calendario.ui.dayHeading
import com.quarenta.calendario.ui.localDate
import com.quarenta.calendario.ui.theme.Bento
import com.quarenta.calendario.ui.timeRange
import java.time.Instant
import java.time.LocalDate

/** Find any appointment, a year back and two years ahead, by title, place, notes or calendar. */
@Composable
fun SearchScreen(
    pool: suspend () -> List<CalendarEvent>,
    onOpenEvent: (CalendarEvent) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val events by produceState<List<CalendarEvent>?>(null) { value = pool() }
    var query by rememberSaveable { mutableStateOf("") }
    val result = remember(query, events) { EventSearch.search(events.orEmpty(), query, Instant.now()) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val today = LocalDate.now()

    Column(Modifier.fillMaxSize().background(Bento.Ground).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                placeholder = { Text("Procurar compromissos") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                shape = RoundedCornerShape(18.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Bento.Tile, unfocusedContainerColor = Bento.Tile,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = Bento.Blue) }
        }

        when {
            events == null -> Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Bento.Blue)
            }
            query.isBlank() -> Hint("Escreve uma palavra do título, do local ou das notas.\nProcura no último ano e nos próximos dois.")
            result.isEmpty -> Hint("Nada encontrado para “${query.trim()}”.")
            else -> LazyColumn(
                Modifier.fillMaxSize().navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (result.upcoming.isNotEmpty()) {
                    item { SectionLabel("Próximos") }
                    items(result.upcoming, key = { "u${it.id}-${it.start}" }) { ResultRow(it, today, past = false) { onOpenEvent(it) } }
                }
                if (result.past.isNotEmpty()) {
                    item { SectionLabel("Anteriores", Modifier.padding(top = 12.dp)) }
                    items(result.past, key = { "p${it.id}-${it.start}" }) { ResultRow(it, today, past = true) { onOpenEvent(it) } }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, fontSize = 16.sp, color = Bento.Secondary, lineHeight = 22.sp, modifier = Modifier.padding(24.dp))
}

@Composable
private fun ResultRow(event: CalendarEvent, today: LocalDate, past: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Bento.Tile).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(5.dp).height(40.dp).clip(CircleShape).background(event.composeColor().copy(alpha = if (past) 0.45f else 1f)))
        Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                run {
                    val day = event.start.localDate()
                    val year = if (day.year != today.year) " ${day.year}" else ""
                    "${dayHeading(day, today)}$year · ${event.timeRange()}"
                },
                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (past) Bento.Secondary else Bento.BlueText,
            )
            Text(
                event.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                color = if (past) Bento.Secondary else Bento.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            event.location?.let { Text(it, fontSize = 14.sp, color = Bento.Secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}
