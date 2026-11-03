package com.quarenta.calendario.ui.event

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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

/** The event, shown inside our own app: never handed to another calendar. */
@Composable
fun EventDetailScreen(
    event: CalendarEvent,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    val color = event.composeColor()

    Column(
        Modifier
            .fillMaxSize()
            .background(Bento.Ground)
            .verticalScroll(rememberScrollState()),
    ) {
        // Coloured header in the event's own colour.
        Column(
            Modifier
                .fillMaxWidth()
                .background(color)
                .statusBarsPadding()
                .padding(start = 8.dp, end = 8.dp, bottom = 28.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(Icons.Rounded.ArrowBack, "Voltar", onClose)
                Spacer(Modifier.weight(1f))
                RoundIcon(Icons.Rounded.Share, "Partilhar") { shareEvent(context, event) }
                if (event.canEdit) RoundIcon(Icons.Rounded.Edit, "Editar", onEdit)
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(dayHeading(event.start.localDate()), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(event.title, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 36.sp)
                Text(event.timeRange(), color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }

        Column(Modifier.padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            event.location?.let { place ->
                BentoTile(Modifier.fillMaxWidth(), onClick = { openMap(context, place) }) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconTile(Icons.Rounded.LocationOn, Bento.Green)
                        Column(Modifier.weight(1f)) {
                            Text("LOCAL", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Bento.Secondary)
                            Text(place, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("Toca para ver o caminho", fontSize = 12.sp, color = Bento.BlueText)
                        }
                    }
                }
            }
            event.description?.let { notes ->
                BentoTile(Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IconTile(Icons.Rounded.Notes, Bento.Orange)
                        Column(Modifier.weight(1f)) {
                            Text("NOTAS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Bento.Secondary)
                            Text(notes, fontSize = 15.sp, lineHeight = 22.sp)
                        }
                    }
                }
            }
            BentoTile(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconTile(Icons.Rounded.CalendarMonth, color)
                    Column(Modifier.weight(1f)) {
                        Text("CALENDÁRIO", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Bento.Secondary)
                        Text(event.sources.joinToString(" · "), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        if (event.sources.size > 1) {
                            Text("Estava repetido em ${event.sources.size} calendários; mostramos só uma vez.", fontSize = 12.sp, color = Bento.Secondary)
                        }
                    }
                }
            }
            if (event.canEdit) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Bento.Tile)
                        .clickable { confirmDelete = true },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = Bento.RedText)
                    Spacer(Modifier.size(8.dp))
                    Text("Apagar compromisso", color = Bento.RedText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            } else {
                Text(
                    "Este calendário é só de leitura (por exemplo, aniversários dos contactos).",
                    fontSize = 13.sp, color = Bento.Secondary, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Apagar \"${event.title}\"?") },
            text = { Text("O compromisso é apagado deste calendário em todos os teus dispositivos.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Apagar", color = Bento.RedText) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.padding(4.dp).size(44.dp).clip(CircleShape),
        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.18f), contentColor = Color.White),
    ) { Icon(icon, contentDescription = label) }
}

private fun openMap(context: Context, place: String) {
    val uri = Uri.parse("geo:0,0?q=" + Uri.encode(place))
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
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
