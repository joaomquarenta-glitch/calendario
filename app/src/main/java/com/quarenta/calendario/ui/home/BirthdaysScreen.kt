package com.quarenta.calendario.ui.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quarenta.calendario.data.Birthdays
import com.quarenta.calendario.data.Person
import com.quarenta.calendario.ui.bento.Avatar
import com.quarenta.calendario.ui.components.BentoTile
import com.quarenta.calendario.ui.components.IconTile
import com.quarenta.calendario.ui.monthShort
import com.quarenta.calendario.ui.theme.Bento
import com.quarenta.calendario.ui.weekdayShort

/** Everyone's birthday, soonest first: who, how old they turn, and how long until the day. */
@Composable
fun BirthdaysScreen(
    entries: List<Birthdays.Entry>,
    people: List<Person>,
    onOpenPerson: (Person) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val byId = people.associateBy { it.id }
    val soon = entries.filter { it.daysLeft <= 30 }
    val later = entries.filter { it.daysLeft > 30 }
    LazyColumn(
        Modifier.fillMaxSize().background(Bento.Ground).statusBarsPadding().navigationBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            IconButton(onClick = onClose, modifier = Modifier.size(44.dp).clip(CircleShape).background(Bento.Tile)) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Voltar", tint = Bento.Ink)
            }
        }
        item { Text("Aniversários", style = androidx.compose.material3.MaterialTheme.typography.displaySmall) }
        if (entries.isEmpty()) {
            item {
                BentoTile(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconTile(Icons.Rounded.Cake, Bento.Pink)
                        Text(
                            "Ainda não há datas de aniversário nos teus contactos. Junta-as na ficha de cada pessoa.",
                            fontSize = 15.sp, color = Bento.Secondary,
                        )
                    }
                }
            }
        }
        if (soon.isNotEmpty()) {
            item { Section("PRÓXIMOS 30 DIAS") }
            items(soon, key = { "s" + it.personId }) { e -> byId[e.personId]?.let { BirthdayRow(e, it, onOpenPerson) } }
        }
        if (later.isNotEmpty()) {
            item { Section("MAIS TARDE") }
            items(later, key = { "l" + it.personId }) { e -> byId[e.personId]?.let { BirthdayRow(e, it, onOpenPerson) } }
        }
    }
}

@Composable
private fun Section(text: String) {
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Bento.Secondary,
        letterSpacing = 0.8.sp, modifier = Modifier.padding(start = 4.dp, top = 10.dp),
    )
}

@Composable
private fun BirthdayRow(e: Birthdays.Entry, person: Person, onOpen: (Person) -> Unit) {
    val today = e.daysLeft == 0
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(if (today) Bento.Pink else Bento.Tile)
            .clickable { onOpen(person) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(person, 52.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                person.name, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                color = if (today) Color.White else Bento.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    "${weekdayShort(e.date)}, ${e.date.dayOfMonth} ${monthShort(e.date)}",
                    e.turns?.let { "faz $it" },
                ).joinToString(" · "),
                fontSize = 14.sp, color = if (today) Color.White.copy(alpha = 0.9f) else Bento.Secondary,
            )
        }
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(if (today) Color.White else Bento.Ground)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Text(
                Birthdays.countdown(e.daysLeft), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = if (today) Bento.Pink else Bento.Ink,
            )
        }
    }
}

/** On Início: the next three birthdays, with faces and how long until each. */
@Composable
fun BirthdaysTile(entries: List<Birthdays.Entry>, people: List<Person>, onOpen: () -> Unit) {
    val byId = people.associateBy { it.id }
    val next = entries.mapNotNull { e -> byId[e.personId]?.let { e to it } }.take(3)
    if (next.isEmpty()) return
    BentoTile(Modifier.fillMaxWidth(), onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "ANIVERSÁRIOS", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Bento.Pink,
                letterSpacing = 0.8.sp, modifier = Modifier.weight(1f),
            )
            Icon(Icons.Rounded.Cake, contentDescription = null, tint = Bento.Pink, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            next.forEach { (e, p) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(p, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Bento.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        e.turns?.let { Text("Faz $it anos", fontSize = 13.sp, color = Bento.Secondary) }
                    }
                    Text(
                        Birthdays.countdown(e.daysLeft), fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = if (e.daysLeft == 0) Bento.Pink else Bento.Secondary,
                    )
                }
            }
        }
    }
}
