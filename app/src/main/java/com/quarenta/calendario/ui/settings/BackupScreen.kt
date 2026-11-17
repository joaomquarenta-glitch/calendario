package com.quarenta.calendario.ui.settings

import com.quarenta.calendario.ui.components.asHeading
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quarenta.calendario.data.Backup
import com.quarenta.calendario.data.BackupPlan
import com.quarenta.calendario.ui.components.BentoTile
import com.quarenta.calendario.ui.components.IconTile
import com.quarenta.calendario.ui.monthShort
import com.quarenta.calendario.ui.theme.Bento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Save everything kept in this app to one file, or put it back from one. */
@Composable
fun BackupScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var last by remember { mutableStateOf(Backup.lastBackup(context)) }
    var pending by remember { mutableStateOf<android.net.Uri?>(null) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { Backup.export(context, uri) }.isSuccess }
            busy = false
            last = Backup.lastBackup(context)
            Toast.makeText(context, if (ok) "Cópia guardada" else "Não foi possível guardar a cópia", Toast.LENGTH_LONG).show()
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pending = uri }

    Column(
        Modifier.fillMaxSize().background(Bento.Ground).verticalScroll(rememberScrollState())
            .statusBarsPadding().navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(44.dp).clip(CircleShape).background(Bento.Tile)) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "Voltar", tint = Bento.Ink)
        }
        Text("Cópia de segurança", style = MaterialTheme.typography.displaySmall, modifier = Modifier.asHeading())
        Text(
            "Guarda num ficheiro as tarefas, as notas e campos das pessoas, os planos, as ideias de prenda, " +
                "as fotografias HD e as definições. Os compromissos ficam nos teus calendários (Google, Outlook...).",
            fontSize = 15.sp, color = Bento.Secondary,
        )

        BentoTile(Modifier.fillMaxWidth(), onClick = { if (!busy) save.launch(BackupPlan.fileName(LocalDate.now())) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconTile(Icons.Rounded.CloudUpload, Bento.Blue)
                Column(Modifier.weight(1f)) {
                    Text("Guardar uma cópia", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Bento.Ink)
                    Text(
                        last?.let { "Última: ${it.dayOfMonth} ${monthShort(it)} ${it.year}" } ?: "Ainda não fizeste nenhuma",
                        fontSize = 14.sp, color = Bento.Secondary,
                    )
                }
                if (busy) CircularProgressIndicator(color = Bento.Blue, modifier = Modifier.size(24.dp))
            }
        }
        Text(
            "Escolhe onde guardar: no Google Drive fica a salvo mesmo que percas o telemóvel.",
            fontSize = 13.sp, color = Bento.Secondary, modifier = Modifier.padding(horizontal = 4.dp),
        )

        BentoTile(Modifier.fillMaxWidth(), onClick = { if (!busy) open.launch(arrayOf("application/zip", "application/octet-stream")) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconTile(Icons.Rounded.CloudDownload, Bento.Green)
                Column(Modifier.weight(1f)) {
                    Text("Repor de uma cópia", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Bento.Ink)
                    Text("Num telemóvel novo, ou para voltar atrás", fontSize = 14.sp, color = Bento.Secondary)
                }
            }
        }

        // Restoring replaces what is here now, so it asks once, right on the page.
        pending?.let { uri ->
            BentoTile(Modifier.fillMaxWidth()) {
                Text("Repor esta cópia?", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Bento.Ink)
                Text(
                    "O que tens agora na app (tarefas, notas, definições) é substituído pelo que está na cópia. A app reinicia no fim.",
                    fontSize = 14.sp, color = Bento.Secondary,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Choice("Cancelar", primary = false, Modifier.weight(1f)) { pending = null }
                    Choice("Repor", primary = true, Modifier.weight(1f)) {
                        pending = null
                        busy = true
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { runCatching { Backup.restore(context, uri) }.getOrDefault(false) }
                            busy = false
                            if (ok) Backup.restart(context)
                            else Toast.makeText(context, "Este ficheiro não é uma cópia do FortyCalendar", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Choice(label: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(16.dp)).background(if (primary) Bento.Blue else Bento.Ground)
            .clickable(onClick = onClick).padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (primary) Bento.OnBlue else Bento.BlueText)
    }
}
