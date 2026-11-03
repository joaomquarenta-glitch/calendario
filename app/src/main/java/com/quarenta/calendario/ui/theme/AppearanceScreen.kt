package com.quarenta.calendario.ui.theme

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Choose the app's look: follow the phone, light, black, or one of the pastel colours. */
@Composable
fun AppearanceScreen(selectedId: String, onSelect: (String) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    Column(
        Modifier.fillMaxSize().background(Bento.Ground).verticalScroll(rememberScrollState())
            .statusBarsPadding().navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(44.dp).clip(CircleShape).background(Bento.Tile)) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Voltar", tint = Bento.Ink)
            }
        }
        Text("Aparência", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Bento.Ink)
        Text("Escolhe as cores da app. Muda logo, e fica guardado.", fontSize = 15.sp, color = Bento.Secondary)

        Label("CLARO E ESCURO")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Swatch(
                name = "Sistema", selected = selectedId == Themes.SYSTEM, modifier = Modifier.weight(1f),
                ground = Brush.linearGradient(listOf(Themes.Light.ground, Themes.Light.ground, Themes.Black.ground, Themes.Black.ground)),
                tile = Brush.linearGradient(listOf(Themes.Light.tile, Themes.Light.tile, Themes.Black.tile, Themes.Black.tile)),
                caption = "Como o telemóvel",
            ) { onSelect(Themes.SYSTEM) }
            listOf(Themes.Light, Themes.Black).forEach { t ->
                Swatch(
                    name = t.name, selected = selectedId == t.id, modifier = Modifier.weight(1f),
                    ground = Brush.linearGradient(listOf(t.ground, t.ground)), tile = Brush.linearGradient(listOf(t.tile, t.tile)),
                ) { onSelect(t.id) }
            }
        }

        Label("CORES PASTEL")
        Themes.pastels.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    Swatch(
                        name = t.name, selected = selectedId == t.id, modifier = Modifier.weight(1f),
                        ground = Brush.linearGradient(listOf(t.ground, t.ground)), tile = Brush.linearGradient(listOf(t.tile, t.tile)),
                    ) { onSelect(t.id) }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Bento.Secondary, letterSpacing = 0.8.sp, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
}

/** A small preview of the look: its page colour with two tiles on it. */
@Composable
private fun Swatch(
    name: String,
    selected: Boolean,
    modifier: Modifier,
    ground: Brush,
    tile: Brush,
    caption: String? = null,
    onClick: () -> Unit,
) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(22.dp)).background(ground)
                .border(if (selected) 3.dp else 1.dp, if (selected) Bento.Blue else Bento.Hairline, RoundedCornerShape(22.dp))
                .padding(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(10.dp)).background(tile))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(10.dp)).background(tile))
                    Box(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(10.dp)).background(tile))
                }
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.TopEnd).size(24.dp).clip(CircleShape).background(Bento.Blue),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Check, contentDescription = "Escolhido", tint = Color.White, modifier = Modifier.size(16.dp)) }
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Bento.Ink, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
        caption?.let { Text(it, fontSize = 12.sp, color = Bento.Secondary, modifier = Modifier.padding(start = 6.dp)) }
    }
}
