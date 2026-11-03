package com.quarenta.calendario.ui.event

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quarenta.calendario.data.CalendarInfo
import com.quarenta.calendario.data.EventDraft
import com.quarenta.calendario.ui.dayHeading
import com.quarenta.calendario.ui.theme.Bento
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private enum class Picking { StartDate, StartTime, EndDate, EndTime }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditorScreen(
    initial: EventDraft,
    isNew: Boolean,
    fromOtherApp: Boolean,
    calendars: List<CalendarInfo>,
    onCancel: () -> Unit,
    onSave: (EventDraft) -> Unit,
) {
    BackHandler(onBack = onCancel)
    val zone = ZoneId.systemDefault()
    var draft by remember(initial) {
        mutableStateOf(initial.copy(calendarId = initial.calendarId ?: calendars.firstOrNull()?.id))
    }
    var picking by remember { mutableStateOf<Picking?>(null) }

    val startDate = draft.start.atZone(zone).toLocalDate()
    val startTime = draft.start.atZone(zone).toLocalTime()
    val endDate = draft.end.atZone(zone).toLocalDate()
    val endTime = draft.end.atZone(zone).toLocalTime()

    fun setStart(d: LocalDate, t: LocalTime) {
        val newStart = d.atTime(t).atZone(zone).toInstant()
        val length = Duration.between(draft.start, draft.end).coerceAtLeast(Duration.ofMinutes(15))
        draft = draft.copy(start = newStart, end = newStart.plus(length)) // keep the same length
    }
    fun setEnd(d: LocalDate, t: LocalTime) {
        val newEnd = d.atTime(t).atZone(zone).toInstant()
        draft = draft.copy(end = if (newEnd.isAfter(draft.start)) newEnd else draft.start.plusSeconds(900))
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Bento.Ground)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onCancel) { Text("Cancelar", fontSize = 17.sp) }
            Spacer(Modifier.weight(1f))
            Text(if (isNew) "Novo compromisso" else "Editar", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { onSave(draft) }, enabled = draft.title.isNotBlank()) {
                Text("Guardar", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (fromOtherApp) {
                Text(
                    "Recebido de outra app. Confirma os dados e carrega em Guardar.",
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Bento.Blue.copy(alpha = 0.12f)).padding(14.dp),
                    color = Bento.BlueText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                )
            }

            Card {
                Field("Título", draft.title) { draft = draft.copy(title = it) }
                HorizontalDivider(color = Bento.Hairline)
                Field("Local", draft.location) { draft = draft.copy(location = it) }
            }

            Card {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Todo o dia", fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = draft.allDay,
                        onCheckedChange = { draft = draft.copy(allDay = it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Bento.Green),
                    )
                }
                HorizontalDivider(color = Bento.Hairline)
                DateTimeRow("Início", startDate, startTime, draft.allDay, { picking = Picking.StartDate }, { picking = Picking.StartTime })
                HorizontalDivider(color = Bento.Hairline)
                DateTimeRow("Fim", endDate, endTime, draft.allDay, { picking = Picking.EndDate }, { picking = Picking.EndTime })
            }

            Label("AVISO")
            Chips(
                options = listOf(null to "Nenhum", 10 to "10 min", 30 to "30 min", 60 to "1 hora", 1440 to "1 dia"),
                selected = draft.reminderMinutes,
            ) { draft = draft.copy(reminderMinutes = it) }

            if (calendars.size > 1) {
                Label("CALENDÁRIO")
                Chips(
                    options = calendars.map { it.id to it.name.ifBlank { it.account } },
                    selected = draft.calendarId,
                ) { draft = draft.copy(calendarId = it) }
            }

            Card {
                TextField(
                    value = draft.description,
                    onValueChange = { draft = draft.copy(description = it) },
                    placeholder = { Text("Notas") },
                    minLines = 4,
                    colors = transparentFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Button(
                onClick = { onSave(draft) },
                enabled = draft.title.isNotBlank(),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Bento.Blue),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Guardar", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(24.dp))
        }
    }

    when (picking) {
        Picking.StartDate, Picking.EndDate -> {
            val isStart = picking == Picking.StartDate
            val current = if (isStart) startDate else endDate
            val state = rememberDatePickerState(
                initialSelectedDateMillis = current.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            )
            DatePickerDialog(
                onDismissRequest = { picking = null },
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { ms ->
                            val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                            if (isStart) setStart(d, startTime) else setEnd(d, endTime)
                        }
                        picking = null
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancelar") } },
            ) { DatePicker(state = state) }
        }
        Picking.StartTime, Picking.EndTime -> {
            val isStart = picking == Picking.StartTime
            val current = if (isStart) startTime else endTime
            val state = rememberTimePickerState(initialHour = current.hour, initialMinute = current.minute, is24Hour = true)
            AlertDialog(
                onDismissRequest = { picking = null },
                confirmButton = {
                    TextButton(onClick = {
                        val t = LocalTime.of(state.hour, state.minute)
                        if (isStart) setStart(startDate, t) else setEnd(endDate, t)
                        picking = null
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { picking = null }) { Text("Cancelar") } },
                text = { TimePicker(state = state) },
            )
        }
        null -> Unit
    }
}

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun DateTimeRow(label: String, date: LocalDate, time: LocalTime, allDay: Boolean, onDate: () -> Unit, onTime: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Pill(dayHeading(date).substringAfter("· "), onDate)
        if (!allDay) {
            Spacer(Modifier.size(8.dp))
            Pill(timeFmt.format(time), onTime)
        }
    }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Bento.Ground)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) { Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Bento.BlueText) }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Bento.Tile)) { content() }
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Bento.Secondary, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
}

@Composable
private fun Field(placeholder: String, value: String, onChange: (String) -> Unit) {
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        singleLine = true,
        colors = transparentFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun transparentFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)

@Composable
private fun <T> Chips(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Row(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) Bento.Blue else Bento.Tile)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (on) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Color.White))
                    Spacer(Modifier.size(6.dp))
                }
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (on) Color.White else Bento.Ink)
            }
        }
    }
}
