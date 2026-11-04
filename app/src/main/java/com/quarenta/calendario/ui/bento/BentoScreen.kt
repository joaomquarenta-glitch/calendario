package com.quarenta.calendario.ui.bento

import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quarenta.calendario.data.Dedup
import com.quarenta.calendario.data.Person
import com.quarenta.calendario.ui.components.BentoTile
import com.quarenta.calendario.ui.theme.Bento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val avatarColors = listOf(Bento.Orange, Bento.Purple, Bento.Teal, Bento.Blue, Bento.Green, Bento.Pink, Bento.Indigo)

fun avatarColor(name: String): Color = avatarColors[(name.hashCode() and 0x7fffffff) % avatarColors.size]

fun initials(name: String): String = com.quarenta.calendario.data.ContactFormat.initials(name)

private fun scaledDown(b: android.graphics.Bitmap, maxPx: Int): android.graphics.Bitmap {
    val longest = maxOf(b.width, b.height)
    if (longest <= maxPx) return b
    val f = maxPx.toFloat() / longest
    return android.graphics.Bitmap.createScaledBitmap(b, (b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1), true)
}

/** Loads a contact photo off the main thread, in full size when [full] is true. */
@Composable
fun rememberContactPhoto(person: Person, full: Boolean, maxPx: Int? = null): ImageBitmap? {
    val context = LocalContext.current
    val state by produceState<ImageBitmap?>(null, person.id, full) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (full) {
                    com.quarenta.calendario.data.ContactsRepository(context).photo(person)?.let { b -> if (maxPx != null) scaledDown(b, maxPx) else b }?.asImageBitmap()
                } else {
                    person.thumbUri?.let { uri ->
                        context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use {
                            android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap()
                        }
                    }
                }
            }.getOrNull()
        }
    }
    return state
}

@Composable
fun Avatar(person: Person, size: Dp, full: Boolean = false) {
    val photo = rememberContactPhoto(person, full)
    Box(
        Modifier.size(size).clip(CircleShape).background(avatarColor(person.name)),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            Image(photo, contentDescription = null, contentScale = ContentScale.Crop, filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize())
        } else {
            Text(initials(person.name), color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.36f).sp)
        }
    }
}

@Composable
fun BentoScreen(
    people: List<Person>,
    hasContactsAccess: Boolean,
    onRequestAccess: () -> Unit,
    onOpenPerson: (Person) -> Unit,
    onDeletePerson: (Person) -> Unit = {},
) {
    var query by rememberSaveable { mutableStateOf("") }
    // Press and hold a person to delete them; Android asks first.
    var toDelete by remember { mutableStateOf<Person?>(null) }
    toDelete?.let { p ->
        DeletePersonDialog(p, onConfirm = { toDelete = null; onDeletePerson(p) }, onDismiss = { toDelete = null })
    }
    val q = Dedup.normalize(query)
    val filtered = if (q.isBlank()) people else people.filter { Dedup.normalize(it.name).contains(q) }
    val favourites = filtered.filter { it.starred }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text("Pessoas", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 4.dp, top = 4.dp))
        }
        if (!hasContactsAccess) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                BentoTile(Modifier.fillMaxWidth(), color = Bento.Orange) {
                    Text("Juntar os teus contactos", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(
                        "Dá acesso aos contactos para criar uma ficha de cada pessoa, com a foto que já tens no telemóvel.",
                        fontSize = 14.sp, color = Color.White,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = onRequestAccess,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF8A4B00)),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) { Text("Dar acesso", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                }
            }
            return@LazyVerticalGrid
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Procurar pessoa") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Bento.Tile, unfocusedContainerColor = Bento.Tile,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (favourites.isNotEmpty() && q.isBlank()) {
            item(span = { GridItemSpan(maxLineSpan) }) { Label("FAVORITOS") }
            items(favourites, key = { "f${it.id}" }) { person -> PhotoTile(person, person.name.substringBefore(' '), onLongClick = { toDelete = person }) { onOpenPerson(person) } }
            item(span = { GridItemSpan(maxLineSpan) }) { Label("TODOS") }
        } else if (q.isBlank() && filtered.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Para pôr alguém nos favoritos, abre a ficha e toca na estrela, em cima à direita.",
                    color = Bento.Secondary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
        // Everyone the same way as the favourites: the photo fills the tile, the name along the bottom.
        items(filtered, key = { it.id }) { person -> PhotoTile(person, person.name, onLongClick = { toDelete = person }) { onOpenPerson(person) } }
        if (filtered.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Nenhuma pessoa encontrada.", color = Bento.Secondary, modifier = Modifier.padding(8.dp))
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Bento.Secondary, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

/** A person as a photo that fills the whole tile, with [label] along the bottom. */
@Composable
private fun PhotoTile(person: Person, label: String, onLongClick: () -> Unit, onClick: () -> Unit) {
    // Sharp for a tile a third of the screen wide, without holding every full-size photo in memory.
    val photo = rememberContactPhoto(person, full = true, maxPx = 640)
    Box(
        Modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(22.dp))
            .background(avatarColor(person.name)).holdable(onClick, onLongClick),
    ) {
        if (photo != null) {
            Image(photo, contentDescription = null, contentScale = ContentScale.Crop, filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize())
        } else {
            Text(initials(person.name), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, modifier = Modifier.align(Alignment.Center))
        }
        Text(
            label,
            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

/** Tap opens; press and hold offers to delete, with a little vibration like on the iPhone. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Modifier.holdable(onClick: () -> Unit, onLongClick: () -> Unit): Modifier {
    val haptics = LocalHapticFeedback.current
    return combinedClickable(
        onClick = onClick,
        onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onLongClick() },
    )
}

@Composable
private fun DeletePersonDialog(person: Person, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Avatar(person, 72.dp) },
        title = { Text("Apagar ${person.name}?", textAlign = TextAlign.Center) },
        text = {
            Text(
                "O contacto é apagado do telemóvel e das contas onde está guardado (Google, Samsung...). Não dá para desfazer.",
                textAlign = TextAlign.Center,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Apagar", color = Bento.RedText, fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar", fontWeight = FontWeight.SemiBold) }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = Bento.Tile,
        titleContentColor = Bento.Ink,
        textContentColor = Bento.Secondary,
    )
}
