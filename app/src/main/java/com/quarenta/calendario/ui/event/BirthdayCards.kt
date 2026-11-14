package com.quarenta.calendario.ui.event

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quarenta.calendario.data.Birthdays
import com.quarenta.calendario.data.ContactFormat
import com.quarenta.calendario.data.GiftIdea
import com.quarenta.calendario.data.GiftIdeas
import com.quarenta.calendario.data.Person
import com.quarenta.calendario.ui.components.BentoTile
import com.quarenta.calendario.ui.components.IconTile
import com.quarenta.calendario.ui.theme.Bento

/** "Dá os parabéns": how long until the day, and ready-made messages that open WhatsApp. */
@Composable
fun WishesCard(person: Person, turns: Int?, daysLeft: Int, phoneFor: suspend (Person) -> String?) {
    val context = LocalContext.current
    val phone by produceState<String?>(null, person.id) { value = phoneFor(person) }
    val firstName = person.name.trim().split(' ').firstOrNull().orEmpty()
    BentoTile(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "DAR OS PARABÉNS", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Bento.Pink,
                letterSpacing = 0.8.sp, modifier = Modifier.weight(1f),
            )
            Text(Birthdays.countdown(daysLeft), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Bento.Secondary)
        }
        Spacer(Modifier.size(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Birthdays.wishes(firstName, turns).forEach { msg ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Bento.Ground)
                        .clickable { sendWish(context, phone, msg) }.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(msg, fontSize = 15.sp, color = Bento.Ink, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Rounded.Send, contentDescription = "Enviar", tint = Bento.Pink, modifier = Modifier.size(20.dp))
                }
            }
        }
        Text(
            if (phone != null) "Toca numa mensagem para a enviar por WhatsApp." else "Toca numa mensagem para a enviar.",
            fontSize = 13.sp, color = Bento.Secondary, modifier = Modifier.padding(top = 8.dp, start = 4.dp),
        )
    }
}

/** WhatsApp to the person's number when there is one, otherwise the phone's "Share" list. */
private fun sendWish(context: Context, phone: String?, text: String) {
    val number = phone?.let(ContactFormat::whatsAppNumber)
    fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: ActivityNotFoundException) { false }
    val sent = number != null && start(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number?text=" + Uri.encode(text))))
    if (!sent) {
        start(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Enviar parabéns"))
    }
}

/** Gift ideas for this person, kept from one year to the next; tick them once bought. */
@Composable
fun GiftIdeasCard(person: Person) {
    val context = LocalContext.current
    val version by GiftIdeas.version.collectAsState()
    val ideas = remember(person.id, version) { GiftIdeas.get(context, person) }
    var draft by remember(person.id) { mutableStateOf("") }
    fun save(list: List<GiftIdea>) = GiftIdeas.set(context, person, list)
    fun add() {
        if (draft.isNotBlank()) save(ideas + GiftIdea(draft.trim()))
        draft = ""
    }
    BentoTile(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(Icons.Rounded.CardGiftcard, Bento.Purple)
            Column(Modifier.weight(1f)) {
                Text("IDEIAS DE PRENDA", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Bento.Secondary)
                Text(
                    if (ideas.isEmpty()) "Aponta aqui o que te lembrares ao longo do ano"
                    else "${ideas.count { !it.bought }} por comprar",
                    fontSize = 14.sp, color = Bento.Secondary,
                )
            }
        }
        Spacer(Modifier.size(8.dp))
        ideas.forEachIndexed { i, idea ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { save(ideas.toMutableList().also { it[i] = idea.copy(bought = !idea.bought) }) }) {
                    Icon(
                        if (idea.bought) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = if (idea.bought) "Comprada" else "Por comprar",
                        tint = if (idea.bought) Bento.Green else Bento.Secondary,
                    )
                }
                Text(
                    idea.text, fontSize = 16.sp, color = if (idea.bought) Bento.Secondary else Bento.Ink,
                    textDecoration = if (idea.bought) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { save(ideas.filterIndexed { j, _ -> j != i }) }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Tirar", tint = Bento.Secondary, modifier = Modifier.size(18.dp))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = draft, onValueChange = { draft = it },
                placeholder = { Text("Nova ideia (um livro, um jantar...)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Bento.Ground, unfocusedContainerColor = Bento.Ground,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { add() }) {
                Icon(Icons.Rounded.Add, contentDescription = "Juntar", tint = Bento.Purple)
            }
        }
    }
}
