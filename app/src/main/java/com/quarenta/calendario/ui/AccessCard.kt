package com.quarenta.calendario.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quarenta.calendario.ui.components.BentoTile
import com.quarenta.calendario.ui.theme.Bento

/** Asks for calendar access, needed to show the phone's existing calendars. */
@Composable
fun AccessCard(onRequestAccess: () -> Unit) {
    BentoTile(Modifier.fillMaxWidth(), color = Bento.Blue) {
        Text("Juntar os teus calendários", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Text(
            "Dá acesso aos calendários do telemóvel (Google, Outlook, Samsung e outros) para os veres todos aqui.",
            fontSize = 14.sp, color = Color.White,
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onRequestAccess,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Bento.BlueText),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) { Text("Dar acesso", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    }
}
