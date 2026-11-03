package com.quarenta.calendario.ui.bento

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.StickyNote2
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quarenta.calendario.data.ContactField
import com.quarenta.calendario.data.ContactFormat
import com.quarenta.calendario.data.ContactGroup
import com.quarenta.calendario.data.FieldKind
import com.quarenta.calendario.ui.theme.Bento
import com.quarenta.calendario.ui.theme.ThemeState
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp

/** Soft background + strong accent for each kind of information. */
data class Pastel(val background: Color, val accent: Color, val icon: ImageVector)

object Pastels {
    /** Soft colour + accent; in the dark look the soft colour becomes a deep tint of the accent. */
    private fun p(soft: Long, accent: Long): Pair<Color, Color> {
        val a = Color(accent)
        return if (ThemeState.isDark) a.copy(alpha = 0.26f).compositeOver(Bento.Tile) to lerp(a, Color.White, 0.3f)
        else Color(soft) to a
    }
    val Mint get() = p(0xFFE3F5E9, 0xFF2E9E57)
    val Sky get() = p(0xFFE4EEFD, 0xFF2F6FDB)
    val Peach get() = p(0xFFFFECDF, 0xFFD5641E)
    val Lavender get() = p(0xFFEFE8FB, 0xFF7B52C4)
    val Rose get() = p(0xFFFCE6EE, 0xFFCB3F73)
    val Aqua get() = p(0xFFDFF4F5, 0xFF138C96)
    val Butter get() = p(0xFFFFF4D4, 0xFF9A7300)
    val Lilac get() = p(0xFFEAEAFA, 0xFF5552CF)
    val Stone get() = p(0xFFEFEFF2, 0xFF5E5E66)
    val WhatsApp get() = p(0xFFDCF8E6, 0xFF1DA851)
}

private fun pastelOf(colors: Pair<Color, Color>, icon: ImageVector) = Pastel(colors.first, colors.second, icon)

fun FieldKind.pastel(type: Int = 0): Pastel = when (this) {
    FieldKind.Phone -> pastelOf(Pastels.Mint, Icons.Rounded.Call)
    FieldKind.Email -> pastelOf(Pastels.Sky, Icons.Rounded.Email)
    FieldKind.Address -> pastelOf(Pastels.Peach, Icons.Rounded.Home)
    FieldKind.Organization -> pastelOf(Pastels.Lavender, Icons.Rounded.Business)
    FieldKind.Event -> pastelOf(Pastels.Rose, if (type == android.provider.ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY) Icons.Rounded.Cake else Icons.Rounded.Event)
    FieldKind.Website -> pastelOf(Pastels.Aqua, Icons.Rounded.Language)
    FieldKind.Im -> pastelOf(Pastels.Aqua, Icons.Rounded.Chat)
    FieldKind.Sip -> pastelOf(Pastels.Aqua, Icons.Rounded.PhoneInTalk)
    FieldKind.Nickname -> pastelOf(Pastels.Butter, Icons.Rounded.Face)
    FieldKind.Relation -> pastelOf(Pastels.Butter, Icons.Rounded.FamilyRestroom)
    FieldKind.Note -> pastelOf(Pastels.Butter, Icons.Rounded.StickyNote2)
    FieldKind.Group -> pastelOf(Pastels.Lilac, Icons.Rounded.Groups)
}

/** Round pastel button with a coloured icon and a label underneath, like the iPhone contact card. */
@Composable
fun QuickAction(icon: ImageVector, label: String, colors: Pair<Color, Color>, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(if (enabled) colors.first else Bento.Hairline),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = if (enabled) colors.second else Bento.Secondary, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) Bento.Ink else Bento.Secondary, maxLines = 1)
    }
}

/** A card holding every value of one kind (all phones, all emails…). */
@Composable
fun FieldCard(
    title: String,
    style: Pastel,
    rows: List<ContactField>,
    display: (ContactField) -> String = { it.value },
    onTap: (ContactField) -> Unit,
    onEdit: (ContactField) -> Unit,
) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(style.background).padding(top = 14.dp, bottom = 4.dp)) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(style.accent), contentAlignment = Alignment.Center) {
                Icon(style.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.size(10.dp))
            Text(title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = style.accent, letterSpacing = 0.6.sp)
        }
        rows.forEachIndexed { i, f ->
            if (i > 0) HorizontalDivider(color = Bento.Tile.copy(alpha = 0.7f), modifier = Modifier.padding(start = 16.dp))
            Row(
                Modifier.fillMaxWidth().clickable { onTap(f) }.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(f.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = style.accent.copy(alpha = 0.85f))
                    Text(display(f), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Bento.Ink)
                    f.secondary?.let { Text(it, fontSize = 14.sp, color = Bento.Secondary) }
                }
                IconButton(onClick = { onEdit(f) }) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Alterar ${f.label}", tint = style.accent.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** What can be added to a card: the phone's own fields plus a free field kept only in this app. */
sealed interface AddChoice {
    val title: String
    val style: Pastel
    data class Field(val kind: FieldKind, val type: Int?, override val title: String) : AddChoice {
        override val style get() = kind.pastel(type ?: 0)
    }
    data object Ringtone : AddChoice {
        override val title = "Som de toque"
        override val style get() = pastelOf(Pastels.Stone, Icons.Rounded.MusicNote)
    }
    data object Custom : AddChoice {
        override val title = "Campo livre"
        override val style get() = pastelOf(Pastels.Stone, Icons.Rounded.TextFields)
    }
}

val addChoices: List<AddChoice> = listOf(
    AddChoice.Field(FieldKind.Phone, null, "Telefone"),
    AddChoice.Field(FieldKind.Email, null, "Email"),
    AddChoice.Field(FieldKind.Address, null, "Morada"),
    AddChoice.Field(FieldKind.Organization, null, "Organização"),
    AddChoice.Field(FieldKind.Event, android.provider.ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY, "Data de nascimento"),
    AddChoice.Field(FieldKind.Event, android.provider.ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY, "Outra data"),
    AddChoice.Field(FieldKind.Relation, null, "Relação"),
    AddChoice.Field(FieldKind.Nickname, null, "Alcunha"),
    AddChoice.Field(FieldKind.Website, null, "Página de Internet"),
    AddChoice.Field(FieldKind.Sip, null, "Chamada via Internet"),
    AddChoice.Field(FieldKind.Im, null, "Mensagens instantâneas"),
    AddChoice.Field(FieldKind.Note, null, "Nota"),
    AddChoice.Field(FieldKind.Group, null, "Grupo"),
    AddChoice.Ringtone,
    AddChoice.Custom,
)

/** Grid of everything that can be added, shown in the bottom half so the photo stays visible. */
@Composable
fun AddPicker(onPick: (AddChoice) -> Unit, onClose: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        PanelHeader("Adicionar à ficha", onClose)
        addChoices.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { choice ->
                    Column(
                        Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(22.dp)).background(choice.style.background)
                            .clickable { onPick(choice) }.padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(choice.style.accent), contentAlignment = Alignment.Center) {
                            Icon(choice.style.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            choice.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Bento.Ink,
                            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun PanelHeader(title: String, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        IconButton(onClick = onClose, modifier = Modifier.size(36.dp).clip(CircleShape).background(Bento.Hairline)) {
            Icon(Icons.Rounded.Close, contentDescription = "Fechar", tint = Bento.Secondary, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * Inline editor for one field: type chips, the value, and Guardar / Apagar.
 * [existing] is null when adding. [onSave] gets (type, value, secondary) already checked.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FieldEditor(
    kind: FieldKind,
    presetType: Int?,
    existing: ContactField?,
    groups: List<ContactGroup>,
    onSave: (type: Int, value: String, secondary: String?) -> Unit,
    onDelete: (() -> Unit)?,
    onClose: () -> Unit,
) {
    val style = kind.pastel(existing?.type ?: presetType ?: 0)
    var type by remember { mutableIntStateOf(existing?.type ?: presetType ?: kind.types.firstOrNull()?.type ?: 0) }
    var value by remember {
        mutableStateOf(
            when {
                existing == null -> ""
                kind == FieldKind.Event -> ContactFormat.dateForEditing(existing.value)
                kind == FieldKind.Group -> groups.firstOrNull { it.title == existing.value }?.id?.toString() ?: ""
                else -> existing.value
            },
        )
    }
    var secondary by remember { mutableStateOf(existing?.secondary ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelHeader(if (existing == null) kind.title else "Alterar ${kind.title.lowercase()}", onClose)

        if (kind.types.size > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                kind.types.forEach { t -> Chip(t.label, selected = t.type == type, style = style) { type = t.type } }
            }
        }

        when (kind) {
            FieldKind.Group -> {
                if (groups.isEmpty()) {
                    Text("Não há grupos nesta conta. Cria um na app Contactos.", color = Bento.Secondary)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        groups.forEach { g -> Chip(g.title, selected = value == g.id.toString(), style = style) { value = g.id.toString() } }
                    }
                }
            }
            FieldKind.Organization -> {
                PastelField(value, { value = it }, "Empresa", style)
                PastelField(secondary, { secondary = it }, "Cargo", style)
            }
            FieldKind.Event -> PastelField(value, { value = it; error = null }, "Data (ex.: 12/03/1980)", style, keyboard = KeyboardType.Number)
            FieldKind.Phone -> PastelField(value, { value = it }, "Número", style, keyboard = KeyboardType.Phone)
            FieldKind.Email -> PastelField(value, { value = it }, "Email", style, keyboard = KeyboardType.Email)
            FieldKind.Website -> PastelField(value, { value = it }, "Endereço (ex.: www.exemplo.pt)", style, keyboard = KeyboardType.Uri)
            FieldKind.Sip -> PastelField(value, { value = it }, "Endereço SIP", style, keyboard = KeyboardType.Email)
            FieldKind.Address, FieldKind.Note -> PastelField(value, { value = it }, kind.title, style, minLines = 3)
            FieldKind.Relation -> PastelField(value, { value = it }, "Nome da pessoa", style)
            else -> PastelField(value, { value = it }, kind.title, style)
        }
        error?.let { Text(it, color = Bento.RedText, fontSize = 14.sp) }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onDelete != null) {
                Text(
                    "Apagar", color = Bento.RedText, fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onDelete).padding(horizontal = 14.dp, vertical = 14.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            val ready = value.isNotBlank() || (kind == FieldKind.Organization && secondary.isNotBlank())
            Box(
                Modifier.clip(RoundedCornerShape(18.dp)).background(if (ready) style.accent else Bento.Hairline)
                    .clickable(enabled = ready) {
                        val clean = value.trim()
                        if (kind == FieldKind.Event) {
                            val date = ContactFormat.dateForContacts(clean)
                            if (date == null) error = "Escreve a data como dia/mês/ano, por exemplo 12/03/1980." else onSave(type, date, null)
                        } else {
                            onSave(type, clean, secondary.trim().takeIf { it.isNotEmpty() })
                        }
                    }
                    .padding(horizontal = 28.dp, vertical = 14.dp),
            ) { Text("Guardar", color = if (ready) Color.White else Bento.Secondary, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
        Text(
            "Fica gravado nos contactos do telemóvel.",
            fontSize = 12.sp, color = Bento.Secondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End,
        )
    }
}

/** Free field kept only in this app ("Filhos", "Prefere", "Como nos conhecemos"…). */
@Composable
fun CustomFieldEditor(onSave: (String, String) -> Unit, onClose: () -> Unit) {
    val style = AddChoice.Custom.style
    var label by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PanelHeader("Campo livre", onClose)
        PastelField(label, { label = it }, "Nome (ex.: Filhos, Prefere)", style)
        PastelField(value, { value = it }, "Valor", style, minLines = 2)
        Row {
            Spacer(Modifier.weight(1f))
            val ready = label.isNotBlank() || value.isNotBlank()
            Box(
                Modifier.clip(RoundedCornerShape(18.dp)).background(if (ready) Bento.Ink else Bento.Hairline)
                    .clickable(enabled = ready) { onSave(label, value) }.padding(horizontal = 28.dp, vertical = 14.dp),
            ) { Text("Guardar", color = if (ready) Color.White else Bento.Secondary, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        }
        Text("Fica só nesta app.", fontSize = 12.sp, color = Bento.Secondary, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
    }
}

@Composable
private fun Chip(text: String, selected: Boolean, style: Pastel, onClick: () -> Unit) {
    Text(
        text,
        color = if (selected) Color.White else style.accent,
        fontWeight = FontWeight.Bold, fontSize = 14.sp,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (selected) style.accent else style.background)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun PastelField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    style: Pastel,
    keyboard: KeyboardType = KeyboardType.Text,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        label = { Text(label) },
        singleLine = minLines == 1, minLines = minLines,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = style.background, unfocusedContainerColor = style.background,
            focusedBorderColor = style.accent, unfocusedBorderColor = Color.Transparent,
            focusedLabelColor = style.accent, cursorColor = style.accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Small "+ Adicionar" pill used at the end of the card. */
@Composable
fun AddButton(onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(20.dp)).background(Bento.Tile).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Add, contentDescription = null, tint = Bento.Blue)
        Spacer(Modifier.size(6.dp))
        Text("Adicionar campo", color = Bento.BlueText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}
