package com.quarenta.calendario.ui.bento

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quarenta.calendario.ui.components.BentoTile
import com.quarenta.calendario.ui.components.IconTile
import com.quarenta.calendario.ui.theme.Bento

/** Placeholder until step 3 (Bento libraries and person records). */
@Composable
fun BentoScreen() {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Bento", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(start = 4.dp, top = 20.dp))
        BentoTile(Modifier.fillMaxWidth()) {
            IconTile(Icons.Rounded.Groups, Bento.Orange, 52.dp, 28.dp)
            Spacer(Modifier.height(12.dp))
            Text("Pessoas e bibliotecas", style = MaterialTheme.typography.titleLarge)
            Text(
                "As fichas com foto em ecrã grande, campos livres e notas chegam na próxima versão.",
                fontSize = 15.sp, color = Bento.Secondary,
            )
        }
    }
}
