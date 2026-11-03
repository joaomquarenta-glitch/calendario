package com.quarenta.calendario

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quarenta.calendario.data.AppUpdate
import com.quarenta.calendario.ui.components.IconTile
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quarenta.calendario.ui.agenda.AgendaScreen
import com.quarenta.calendario.ui.bento.BentoScreen
import com.quarenta.calendario.ui.bento.PersonScreen
import com.quarenta.calendario.ui.event.EventDetailScreen
import com.quarenta.calendario.ui.event.EventEditorScreen
import com.quarenta.calendario.ui.home.HomeScreen
import com.quarenta.calendario.ui.tasks.TasksScreen
import com.quarenta.calendario.ui.theme.Bento
import com.quarenta.calendario.ui.theme.CalendarioTheme

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Início", Icons.Rounded.Dashboard),
    Agenda("Agenda", Icons.Rounded.CalendarMonth),
    People("Pessoas", Icons.Rounded.Groups),
    Tasks("Tarefas", Icons.Rounded.CheckBox),
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only handle the launching intent once, not again after a rotation.
        if (savedInstanceState == null) vm.handleIntent(intent)
        setContent { CalendarioTheme { App(vm) } }
    }

    /** "Add to calendar" from another app while this one is already open. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        vm.handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
        vm.refreshPeople()
        vm.checkForUpdate()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun App(vm: AppViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    val events by vm.events.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val hasAccess by vm.hasCalendarAccess.collectAsStateWithLifecycle()
    val hasContacts by vm.hasContactsAccess.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val selectedDate by vm.selectedDate.collectAsStateWithLifecycle()
    val overlay by vm.overlay.collectAsStateWithLifecycle()
    val writable by vm.writableCalendars.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val updateProgress by vm.updateProgress.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val pullState = rememberPullToRefreshState()

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onPermissionResult()
    }
    val requestCalendar = {
        permission.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
    }
    val requestContacts = { permission.launch(arrayOf(Manifest.permission.READ_CONTACTS)) }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        vm.messageShown()
        snackbar.showSnackbar(text)
    }

    Box(Modifier.fillMaxSize().background(Bento.Ground)) {
        Scaffold(
            containerColor = Bento.Ground,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Bento.Blue,
                                selectedTextColor = Bento.Blue,
                                indicatorColor = Bento.Blue.copy(alpha = 0.12f),
                                unselectedIconColor = Bento.Secondary,
                                unselectedTextColor = Bento.Secondary,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())) {
              update?.let { UpdateBanner(it, updateProgress, onUpdate = vm::startUpdate) }
              PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = vm::pullToRefresh,
                state = pullState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullState,
                        isRefreshing = refreshing,
                        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
                    )
                },
              ) {
                when (tab) {
                    Tab.Home -> HomeScreen(
                        events = events, tasks = tasks, hasCalendarAccess = hasAccess,
                        onRequestAccess = requestCalendar,
                        onOpenAgenda = { tab = Tab.Agenda },
                        onOpenTasks = { tab = Tab.Tasks },
                        onOpenBento = { tab = Tab.People },
                        onOpenEvent = vm::openEvent,
                    )
                    Tab.Agenda -> AgendaScreen(
                        events = events, tasks = tasks, hasCalendarAccess = hasAccess,
                        onRequestAccess = requestCalendar,
                        onToggleTask = vm::toggleTask,
                        selectedDate = selectedDate,
                        onSelectDate = vm::selectDate,
                        onOpenEvent = vm::openEvent,
                        onNewEvent = { vm.newEvent() },
                    )
                    Tab.People -> BentoScreen(
                        people = people,
                        hasContactsAccess = hasContacts,
                        onRequestAccess = requestContacts,
                        onOpenPerson = vm::openPerson,
                    )
                    Tab.Tasks -> TasksScreen(
                        tasks = tasks,
                        onAdd = { vm.addTask(it) },
                        onToggle = vm::toggleTask,
                        onDelete = vm::deleteTask,
                    )
                }
              }
            }
        }

        // Full-screen sheets drawn over the tabs: everything stays inside this app.
        val o = overlay
        if (o != null) Box(
            Modifier
                .fillMaxSize()
                .background(Bento.Ground)
                // Registers as hit so taps never fall through to the tab underneath.
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        ) { when (o) {
            is Overlay.Detail -> EventDetailScreen(
                event = o.event,
                onClose = vm::closeOverlay,
                onEdit = { vm.editEvent(o.event) },
                onDelete = { vm.delete(o.event) },
            )
            is Overlay.Editor -> EventEditorScreen(
                initial = o.draft,
                isNew = o.editingId == null,
                fromOtherApp = o.fromOtherApp,
                calendars = writable,
                onCancel = vm::closeOverlay,
                onSave = { draft -> vm.save(draft, o.editingId) },
            )
            is Overlay.PersonCard -> {
                val key = o.person.lookupKey
                val noteFlow = remember(key) { vm.noteFor(key) }
                val fieldsFlow = remember(key) { vm.fieldsFor(key) }
                PersonScreen(
                    person = o.person,
                    events = events,
                    noteFlow = noteFlow,
                    fieldsFlow = fieldsFlow,
                    onSaveNote = { vm.saveNote(key, it) },
                    onAddField = { label, value -> vm.addField(key, label, value) },
                    onDeleteField = vm::deleteField,
                    onOpenEvent = vm::openEvent,
                    onClose = vm::closeOverlay,
                )
            }
        } }
    }
}

/** Shown above the tab bar when GitHub has a newer version. */
@Composable
private fun UpdateBanner(update: AppUpdate, progress: Float?, onUpdate: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.SystemUpdate, Bento.Blue, 40.dp, 22.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Nova versão disponível", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Bento.Ink)
                Text(
                    if (progress == null) "Versão ${update.version}. Não perdes nada." else "A descarregar… ${(progress * 100).toInt()}%",
                    fontSize = 13.sp, color = Bento.Secondary,
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onUpdate,
                enabled = progress == null,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Bento.Blue),
            ) { Text("Atualizar", fontWeight = FontWeight.Bold) }
        }
        if (progress != null) {
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                color = Bento.Blue,
                trackColor = Bento.Blue.copy(alpha = 0.15f),
            )
        }
    }
}
