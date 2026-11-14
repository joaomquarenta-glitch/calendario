package com.quarenta.calendario.ui.bento

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quarenta.calendario.data.Dedup
import com.quarenta.calendario.data.Person
import com.quarenta.calendario.data.PhotoMath
import com.quarenta.calendario.ui.theme.Bento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opens when a picture is shared to FortyCalendar from Instagram, WhatsApp or the gallery:
 * the picture on top, and below it the people to choose from. Picking one keeps the photo in HD.
 */
@Composable
fun PhotoTargetScreen(
    uri: Uri,
    people: List<Person>,
    hasContactsAccess: Boolean,
    onPick: (Person) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val preview by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val resolver = context.contentResolver
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                val opts = BitmapFactory.Options().apply { inSampleSize = PhotoMath.sampleSize(bounds.outWidth, bounds.outHeight, 1200) }
                resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }?.asImageBitmap()
            }.getOrNull()
        }
    }
    var query by remember { mutableStateOf("") }
    val shown = remember(query, people) {
        val q = Dedup.normalize(query)
        val sorted = people.sortedWith(compareByDescending<Person> { it.starred }.thenBy { Dedup.normalize(it.name) })
        if (q.isBlank()) sorted else sorted.filter { Dedup.normalize(it.name).contains(q) }
    }

    Column(Modifier.fillMaxSize().background(Bento.Ground).imePadding()) {
        Box(Modifier.fillMaxWidth().height(260.dp).background(Color.Black)) {
            preview?.let {
                Image(
                    it, contentDescription = "Fotografia partilhada",
                    contentScale = ContentScale.Crop, filterQuality = FilterQuality.High, modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier.fillMaxWidth().height(120.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)))),
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.statusBarsPadding().padding(8.dp).size(44.dp).clip(CircleShape),
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.35f), contentColor = Color.White),
            ) { Icon(Icons.Rounded.Close, contentDescription = "Cancelar") }
            Text(
                "De quem é esta fotografia?", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
            )
        }

        if (!hasContactsAccess) {
            Text(
                "Para escolher a pessoa, autoriza primeiro a FortyCalendar a ver os contactos no separador Pessoas.",
                fontSize = 16.sp, color = Bento.Secondary, modifier = Modifier.padding(20.dp),
            )
            return@Column
        }

        TextField(
            value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("Procurar pessoa") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            shape = RoundedCornerShape(18.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Bento.Tile, unfocusedContainerColor = Bento.Tile,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
        LazyColumn(
            Modifier.fillMaxWidth().weight(1f).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(shown, key = { it.id }) { person ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(person) }.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(person, 48.dp)
                    Spacer(Modifier.size(14.dp))
                    Text(
                        person.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Bento.Ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
