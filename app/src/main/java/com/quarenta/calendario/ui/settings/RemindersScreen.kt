package com.quarenta.calendario.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.rounded.AccessAlarm
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.quarenta.calendario.reminders.ReminderActivity
import com.quarenta.calendario.reminders.ReminderAlert
import com.quarenta.calendario.reminders.ReminderColors
import com.quarenta.calendario.reminders.ReminderIntents
import com.quarenta.calendario.reminders.ReminderNotifier
import com.quarenta.calendario.reminders.ReminderScheduler
import com.quarenta.calendario.ui.theme.Bento

/** Turns reminders on, checks everything Android needs for them, and picks their colour. */
@Composable
fun RemindersScreen(onClose: () -> Unit, sample: () -> ReminderAlert) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    // Settings can change in Android's own screens; re-read them when we come back.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { refresh++ }
    var enabled by remember { mutableStateOf(ReminderScheduler.isEnabled(context)) }
    var color by remember { mutableIntStateOf(ReminderScheduler.color(context)) }

    val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        refresh++
        if (!it) openAppSettings(context, Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    }

    Column(
        Modifier.fillMaxSize().background(Bento.Ground).verticalScroll(rememberScrollState())
            .statusBarsPadding().navigationBarsPadding().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconButton(onClick = onClose, modifier = Modifier.size(44.dp).clip(CircleShape).background(Bento.Tile)) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = "Voltar", tint = Bento.Ink)
        }
        Text("Lembretes", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Bento.Ink)
        Text(
            "Em ecrã inteiro, com a foto da pessoa, para todos os calendários do telemóvel. " +
                "Usa os avisos que já tens em cada compromisso.",
            fontSize = 15.sp, color = Bento.Secondary,
        )

        Card {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Bubble(Icons.Rounded.NotificationsActive, Color(color))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Lembretes da FortyCalendar", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Bento.Ink)
                    Text(if (enabled) "Ligados" else "Desligados", fontSize = 13.sp, color = Bento.Secondary)
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { on ->
                        enabled = on
                        Thread { ReminderScheduler.setEnabled(context.applicationContext, on) }.start()
                    },
                    colors = SwitchDefaults.colors(checkedTrackColor = Color(color)),
                )
            }
        }

        if (enabled) {
            // Reading `refresh` makes these checks run again after returning from Android's settings.
            refresh.let { }
            Label("PARA FUNCIONAR A 100%")
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Check(
                        Icons.Rounded.Notifications, "Notificações", "Para o lembrete aparecer",
                        ok = ReminderNotifier.canPost(context),
                    ) {
                        if (Build.VERSION.SDK_INT >= 33) notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else openAppSettings(context, Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    }
                    if (Build.VERSION.SDK_INT >= 34) {
                        Check(
                            Icons.Rounded.Fullscreen, "Ecrã inteiro", "Para acender o ecrã com o telemóvel bloqueado",
                            ok = ReminderNotifier.canUseFullScreen(context),
                        ) { openAppSettings(context, Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT) }
                    }
                    Check(
                        Icons.Rounded.AccessAlarm, "Hora exata", "Para tocar ao minuto certo",
                        ok = ReminderScheduler.canUseExactAlarms(context),
                    ) { if (Build.VERSION.SDK_INT >= 31) openAppSettings(context, Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM) }
                    Check(
                        Icons.Rounded.BatteryChargingFull, "Sem limite de bateria",
                        "Para o telemóvel não adormecer a app (sobretudo nos Samsung)",
                        ok = ignoresBattery(context),
                    ) { openBatterySettings(context) }
                }
            }

            Label("COR DO LEMBRETE")
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ReminderColors.all.chunked(6).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { (name, argb) ->
                                val selected = argb == color
                                Box(
                                    Modifier.weight(1f).aspectRatio(1f).clip(CircleShape).background(Color(argb))
                                        .border(if (selected) 3.dp else 0.dp, if (selected) Bento.Ink else Color.Transparent, CircleShape)
                                        .clickable {
                                            color = argb
                                            ReminderScheduler.setColor(context, argb)
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (selected) Icon(Icons.Rounded.Check, contentDescription = name, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                    Text(
                        ReminderColors.all.firstOrNull { it.second == color }?.first.orEmpty(),
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Bento.Secondary,
                    )
                }
            }

            Label("EXPERIMENTAR")
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Usa o teu próximo compromisso (ou um exemplo) para veres como fica.",
                        fontSize = 14.sp, color = Bento.Secondary,
                    )
                    WideButton("Ver o lembrete agora", Color(color)) {
                        val a = sample()
                        context.startActivity(ReminderIntents.put(Intent(context, ReminderActivity::class.java), a).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                    WideButton("Testar com o ecrã bloqueado (daqui a 15 s)", Bento.Ground, Bento.Ink) {
                        ReminderScheduler.test(context, sample(), seconds = 15)
                        android.widget.Toast.makeText(context, "Bloqueia o ecrã: o lembrete aparece daqui a 15 segundos", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
            Text(
                "Se também recebes avisos do Google Calendar ou do Calendário Samsung, podes desligá-los nessas apps " +
                    "para não teres o mesmo lembrete duas vezes.",
                fontSize = 13.sp, color = Bento.Secondary, modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Bento.Tile).padding(16.dp)) { content() }
}

@Composable
private fun Label(text: String) {
    Text(
        text, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Bento.Secondary,
        letterSpacing = 0.8.sp, modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun Bubble(icon: ImageVector, color: Color) {
    Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun Check(icon: ImageVector, title: String, why: String, ok: Boolean, fix: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Bubble(icon, if (ok) Bento.Green else Bento.Orange)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Bento.Ink)
            Text(why, fontSize = 13.sp, color = Bento.Secondary)
        }
        if (ok) {
            Icon(Icons.Rounded.Check, contentDescription = "Ativo", tint = Bento.GreenText)
        } else {
            Text(
                "Ativar", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Bento.Blue).clickable(onClick = fix)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun WideButton(text: String, background: Color, content: Color = Color.White, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(18.dp)).background(background).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = content, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

private fun ignoresBattery(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

private fun openAppSettings(context: Context, action: String) {
    val intent = Intent(action)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .setData(Uri.parse("package:${context.packageName}"))
    val ok = runCatching { context.startActivity(intent) }.isSuccess ||
        runCatching { context.startActivity(Intent(action).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }.isSuccess
    if (!ok) runCatching {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }
}

/** The list of apps with battery limits; the person picks FortyCalendar and chooses "Sem restrições". */
private fun openBatterySettings(context: Context) {
    val ok = runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }.isSuccess
    if (!ok) runCatching {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }
}
