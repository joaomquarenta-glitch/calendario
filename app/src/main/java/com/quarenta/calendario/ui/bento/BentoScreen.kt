package com.quarenta.calendario.ui.bento

import androidx.compose.foundation.Image
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

fun initials(name: String): String =
    name.split(' ').filter { it.isNotBlank() }.let { parts ->
        (parts.firstOrNull()?.take(1).orEmpty() + (if (parts.size > 1) parts.last().take(1) else "")).uppercase()
    }

/** Loads a contact photo off the main thread, in full size when [full] is true. */
@Composable
fun rememberContactPhoto(person: Person, full: Boolean): ImageBitmap? {
    val context = LocalContext.current
    val state by produceState<ImageBitmap?>(null, person.id, full) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (full) {
                    com.quarenta.calendario.data.ContactsRepository(context).photo(person)?.asImageBitmap()
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
) {
    var query by rememberSaveable { mutableStateOf("") }
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
            items(favourites, key = { "f${it.id}" }) { person -> PhotoTile(person) { onOpenPerson(person) } }
            item(span = { GridItemSpan(maxLineSpan) }) { Label("TODOS") }
        } else if (q.isBlank() && filtered.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Para pôr alguém nos favoritos, abre a ficha e toca na estrela, em cima à direita.",
                    color = Bento.Secondary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
        items(filtered, key = { it.id }) { person -> PersonTile(person) { onOpenPerson(person) } }
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

/** Favourites: the photo fills the whole tile. */
@Composable
private fun PhotoTile(person: Person, onClick: () -> Unit) {
    val photo = rememberContactPhoto(person, full = true)
    Box(
        Modifier.fillMaxWidth().aspectRatio(0.8f).clip(RoundedCornerShape(22.dp))
            .background(avatarColor(person.name)).clickable(onClick = onClick),
    ) {
        if (photo != null) {
            Image(photo, contentDescription = null, contentScale = ContentScale.Crop, filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize())
        } else {
            Text(initials(person.name), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, modifier = Modifier.align(Alignment.Center))
        }
        Text(
            person.name.substringBefore(' '),
            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.35f)).padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun PersonTile(person: Person, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Bento.Tile).clickable(onClick = onClick).padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Avatar(person, 64.dp)
        Text(person.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp)
    }
}
