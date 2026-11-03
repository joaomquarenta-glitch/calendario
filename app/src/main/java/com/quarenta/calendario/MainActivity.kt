package com.quarenta.calendario

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.quarenta.calendario.ui.agenda.AgendaScreen
import com.quarenta.calendario.ui.bento.BentoScreen
import com.quarenta.calendario.ui.home.HomeScreen
import com.quarenta.calendario.ui.tasks.TasksScreen
import com.quarenta.calendario.ui.theme.Bento
import com.quarenta.calendario.ui.theme.CalendarioTheme

private enum class Tab(val label: String, val icon: ImageVector) {
    Home("Início", Icons.Rounded.Dashboard),
    Agenda("Agenda", Icons.Rounded.CalendarMonth),
    People("Bento", Icons.Rounded.Groups),
    Tasks("Tarefas", Icons.Rounded.CheckBox),
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CalendarioTheme { App(vm) } }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }
}

@Composable
private fun App(vm: AppViewModel) {
    var tab by rememberSaveable { mutableStateOf(Tab.Home) }
    val events by vm.events.collectAsStateWithLifecycle()
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val hasAccess by vm.hasCalendarAccess.collectAsStateWithLifecycle()

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onPermissionResult()
    }
    val requestAccess = {
        permission.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
    }

    Scaffold(
        containerColor = Bento.Ground,
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
        val modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding())
        androidx.compose.foundation.layout.Box(modifier) {
            when (tab) {
                Tab.Home -> HomeScreen(
                    events = events, tasks = tasks, hasCalendarAccess = hasAccess,
                    onRequestAccess = requestAccess,
                    onOpenAgenda = { tab = Tab.Agenda },
                    onOpenTasks = { tab = Tab.Tasks },
                    onOpenBento = { tab = Tab.People },
                )
                Tab.Agenda -> AgendaScreen(
                    events = events, tasks = tasks, hasCalendarAccess = hasAccess,
                    onRequestAccess = requestAccess,
                    onToggleTask = vm::toggleTask,
                )
                Tab.People -> BentoScreen()
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
