package com.quarenta.calendario

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.quarenta.calendario.data.AppDatabase
import com.quarenta.calendario.data.CalendarEvent
import com.quarenta.calendario.data.CalendarRepository
import com.quarenta.calendario.data.Task
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val calendars = CalendarRepository(app)
    private val taskDao = AppDatabase.get(app).tasks()

    private val _hasCalendarAccess = MutableStateFlow(checkAccess())
    val hasCalendarAccess: StateFlow<Boolean> = _hasCalendarAccess.asStateFlow()

    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    val events: StateFlow<List<CalendarEvent>> = _events.asStateFlow()

    val tasks: StateFlow<List<Task>> = taskDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Reload whenever any calendar on the phone changes (new sync, edit in another app). */
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }
    private var observing = false

    init {
        refresh()
    }

    fun onPermissionResult() {
        _hasCalendarAccess.value = checkAccess()
        refresh()
    }

    fun refresh() {
        if (!checkAccess()) return
        if (!observing) {
            getApplication<Application>().contentResolver
                .registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
            observing = true
        }
        viewModelScope.launch {
            val today = LocalDate.now()
            val from = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val loaded = withContext(Dispatchers.IO) {
                runCatching { calendars.eventsBetween(from, today.plusDays(35)) }.getOrDefault(emptyList())
            }
            _events.value = loaded
        }
    }

    fun addTask(title: String, due: LocalDate? = LocalDate.now()) {
        if (title.isBlank()) return
        viewModelScope.launch { taskDao.insert(Task(title = title.trim(), dueEpochDay = due?.toEpochDay())) }
    }

    fun toggleTask(task: Task) {
        viewModelScope.launch { taskDao.update(task.copy(done = !task.done)) }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch { taskDao.delete(task.id) }
    }

    private fun checkAccess(): Boolean =
        ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    override fun onCleared() {
        if (observing) getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }
}
