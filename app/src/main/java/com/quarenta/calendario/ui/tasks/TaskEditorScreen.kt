package com.quarenta.calendario.ui.tasks

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import com.quarenta.calendario.data.Task
import com.quarenta.calendario.data.TaskRules
import com.quarenta.calendario.ui.agenda.TaskCheck
import com.quarenta.calendario.ui.components.BentoTile
import com.quarenta.calendario.ui.dayHeading
import com.quarenta.calendario.ui.theme.Bento
import java.time.LocalDate

/** One task in full: its day and time, a reminder, how it repeats, and its steps. Every change is saved at once. */
@Composable
fun TaskEditorScreen(
    task: Task,
    steps: List<Task>,
    onSave: (Task) -> Unit,
    onToggleStep: (Task) -> Unit,
    onAddStep: (String) -> Unit,
    onDeleteStep: (Task) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var title by remember(task.id) { mutableStateOf(task.title) }
    val latest by rememberUpdatedState(task)
    val latestTitle by rememberUpdatedState(title)
    fun save(t: Task) = onSave(t.copy(title = latestTitle.ifBlank { t.title }))
    DisposableEffect(task.id) {
        onDispose { if (latestTitle.isNotBlank() && latestTitle.trim() != latest.title) onSave(latest.copy(title = latestTitle)) }
    }
    BackHandler(onBack = onClose)

    val today = LocalDate.now()
    val due = task.dueEpochDay?.let(LocalDate::ofEpochDay)

    Column(
        Modifier.fillMaxSize().background(Bento.Ground).verticalScroll(rememberScrollState())
            .statusBarsPadding().imePadding().navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(44.dp).clip(CircleShape).background(Bento.Tile)) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Voltar", tint = Bento.Ink)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDelete, modifier = Modifier.size(44.dp).clip(CircleShape).background(Bento.Tile)) {
                Icon(Icons.Rounded.Delete, contentDescription = "Apagar tarefa", tint = Bento.RedText)
            }
        }

        TextField(
            value = title, onValueChange = { title = it },
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Bento.Ink),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save(task) }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Bento.Tile, unfocusedContainerColor = Bento.Tile,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth(),
        )

        BentoTile(Modifier.fillMaxWidth()) {
            Label("DIA")
            Text(due?.let { dayHeading(it) } ?: "Sem data", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Bento.Ink)
            Spacer(Modifier.height(8.dp))
            Chips(
                listOf<Pair<LocalDate?, String>>(
                    today to "Hoje", today.plusDays(1) to "Amanhã", today.plusWeeks(1) to "Daqui a 1 semana", null to "Sem data",
                ),
                selected = due, extra = "Escolher…",
                onExtra = {
                    val d = due ?: today
                    DatePickerDialog(context, { _, y, m, day -> save(task.copy(dueEpochDay = LocalDate.of(y, m + 1, day).toEpochDay())) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
                },
            ) { save(task.copy(dueEpochDay = it?.toEpochDay())) }
        }

        BentoTile(Modifier.fillMaxWidth()) {
            Label("HORA")
            Text(task.timeMinutes?.let { TaskRules.timeText(it) } ?: "Qualquer hora", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Bento.Ink)
            Spacer(Modifier.height(8.dp))
            Chips(
                listOf<Pair<Int?, String>>(null to "Sem hora", 9 * 60 to "09:00", 13 * 60 to "13:00", 18 * 60 to "18:00", 21 * 60 to "21:00"),
                selected = task.timeMinutes, extra = "Escolher…",
                onExtra = {
                    val m = task.timeMinutes ?: (today.let { java.time.LocalTime.now().hour + 1 } * 60)
                    TimePickerDialog(context, { _, h, min -> save(task.copy(timeMinutes = h * 60 + min, dueEpochDay = task.dueEpochDay ?: today.toEpochDay())) }, (m / 60) % 24, m % 60, true).show()
                },
            ) { save(task.copy(timeMinutes = it, dueEpochDay = if (it != null) task.dueEpochDay ?: today.toEpochDay() else task.dueEpochDay)) }
        }

        BentoTile(Modifier.fillMaxWidth()) {
            Label("LEMBRETE")
            Chips(listOf<Pair<Int?, String>>(null to "Nenhum") + TaskRules.reminders, selected = task.remindBefore) {
                save(task.copy(remindBefore = it, dueEpochDay = if (it != null) task.dueEpochDay ?: today.toEpochDay() else task.dueEpochDay))
            }
            if (task.remindBefore != null && task.timeMinutes == null) {
                Text("Sem hora, o lembrete toca às 09:00.", fontSize = 13.sp, color = Bento.Secondary, modifier = Modifier.padding(top = 6.dp))
            }
        }

        BentoTile(Modifier.fillMaxWidth()) {
            Label("REPETIR")
            Chips(listOf<Pair<String?, String>>(null to "Não") + TaskRules.repeats, selected = task.repeat) {
                save(task.copy(repeat = it, dueEpochDay = if (it != null) task.dueEpochDay ?: today.toEpochDay() else task.dueEpochDay))
            }
            if (task.repeat != null) {
                Text("Quando a marcares como feita, passa para a vez seguinte.", fontSize = 13.sp, color = Bento.Secondary, modifier = Modifier.padding(top = 6.dp))
            }
        }

        StepsCard(steps, onToggleStep, onAddStep, onDeleteStep)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Bento.Secondary, letterSpacing = 0.6.sp)
}

/** A row of choices; the chosen one is filled. [extra] adds a last chip that opens a picker. */
@Composable
private fun <T> Chips(
    options: List<Pair<T, String>>,
    selected: T,
    extra: String? = null,
    onExtra: () -> Unit = {},
    onPick: (T) -> Unit,
) {
    val known = options.any { it.first == selected }
    Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) -> Chip(label, value == selected) { onPick(value) } }
        if (extra != null) Chip(extra, !known, onExtra)
    }
}

@Composable
private fun Chip(label: String, on: Boolean, onClick: () -> Unit) {
    Text(
        label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
        color = if (on) Bento.OnBlue else Bento.Ink,
        modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(if (on) Bento.Blue else Bento.Ground)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun StepsCard(steps: List<Task>, onToggle: (Task) -> Unit, onAdd: (String) -> Unit, onDelete: (Task) -> Unit) {
    var draft by remember { mutableStateOf("") }
    val add = { onAdd(draft); draft = "" }
    BentoTile(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("PASSOS")
            Spacer(Modifier.weight(1f))
            TaskRules.progress(steps)?.let { Text(it, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Bento.Secondary) }
        }
        steps.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onToggle(s) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    TaskCheck(s.done)
                    Spacer(Modifier.size(12.dp))
                    Text(
                        s.title, fontSize = 16.sp, color = if (s.done) Bento.Secondary else Bento.Ink,
                        textDecoration = if (s.done) TextDecoration.LineThrough else null,
                    )
                }
                IconButton(onClick = { onDelete(s) }) {
                    Icon(Icons.Rounded.Close, contentDescription = "Tirar passo", tint = Bento.Secondary, modifier = Modifier.size(18.dp))
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextField(
                value = draft, onValueChange = { draft = it },
                placeholder = { Text("Novo passo") },
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
            IconButton(onClick = add) { Icon(Icons.Rounded.Add, contentDescription = "Juntar passo", tint = Bento.Blue) }
        }
    }
}
