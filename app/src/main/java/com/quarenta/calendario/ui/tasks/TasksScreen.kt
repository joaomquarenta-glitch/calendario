package com.quarenta.calendario.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quarenta.calendario.data.Task
import com.quarenta.calendario.ui.agenda.TaskCheck
import com.quarenta.calendario.ui.components.SectionLabel
import com.quarenta.calendario.ui.dayHeading
import com.quarenta.calendario.ui.theme.Bento
import java.time.LocalDate

@Composable
fun TasksScreen(
    tasks: List<Task>,
    onAdd: (String) -> Unit,
    onToggle: (Task) -> Unit,
    onDelete: (Task) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val submit = { onAdd(draft); draft = "" }
    val today = LocalDate.now().toEpochDay()
    val groups = listOf(
        "Atrasadas" to tasks.filter { !it.done && it.dueEpochDay != null && it.dueEpochDay < today },
        "Hoje" to tasks.filter { !it.done && it.dueEpochDay == today },
        "Próximas" to tasks.filter { !it.done && it.dueEpochDay != null && it.dueEpochDay > today },
        "Sem data" to tasks.filter { !it.done && it.dueEpochDay == null },
        "Feitas" to tasks.filter { it.done },
    ).filter { it.second.isNotEmpty() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Tarefas", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 4.dp, top = 20.dp))
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("Nova tarefa para hoje") },
                    singleLine = true,
                    shape = RoundedCornerShape(20.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Bento.Tile, unfocusedContainerColor = Bento.Tile,
                        unfocusedBorderColor = Bento.Tile, focusedBorderColor = Bento.Blue,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.weight(1f),
                )
                FilledIconButton(
                    onClick = submit,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Bento.Blue),
                    modifier = Modifier.padding(top = 6.dp),
                ) { Icon(Icons.Rounded.Add, contentDescription = "Adicionar tarefa") }
            }
        }
        if (groups.isEmpty()) {
            item { Text("Ainda não há tarefas. Escreve a primeira acima.", color = Bento.Secondary, modifier = Modifier.padding(4.dp)) }
        }
        groups.forEach { (label, list) ->
            item(key = "g$label") { SectionLabel(label) }
            items(list, key = { it.id }) { task ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Bento.Tile)
                        .clickable { onToggle(task) }
                        .padding(start = 16.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TaskCheck(task.done)
                    Column(Modifier.weight(1f)) {
                        Text(
                            task.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            textDecoration = if (task.done) TextDecoration.LineThrough else null,
                            color = if (task.done) Bento.Secondary else Bento.Ink,
                        )
                        task.dueEpochDay?.let {
                            Text(dayHeading(LocalDate.ofEpochDay(it)), fontSize = 12.sp, color = Bento.Secondary)
                        }
                    }
                    IconButton(onClick = { onDelete(task) }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Apagar tarefa", tint = Bento.Secondary)
                    }
                }
            }
        }
    }
}
