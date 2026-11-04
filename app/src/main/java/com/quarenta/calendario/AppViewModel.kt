package com.quarenta.calendario

import com.quarenta.calendario.reminders.ReminderAlert
import com.quarenta.calendario.data.EventPlan

import android.Manifest
import com.quarenta.calendario.ui.theme.ThemeState
import com.quarenta.calendario.ui.theme.Themes
import android.app.Application
import android.content.Intent
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
import com.quarenta.calendario.data.CalendarInfo
import com.quarenta.calendario.data.CalendarRepository
import com.quarenta.calendario.data.ContactsRepository
import com.quarenta.calendario.data.Person
import com.quarenta.calendario.data.PersonField
import com.quarenta.calendario.data.PersonNote
import com.quarenta.calendario.data.Dedup
import com.quarenta.calendario.data.EventDraft
import com.quarenta.calendario.data.IncomingEvent
import com.quarenta.calendario.data.IncomingRequest
import com.quarenta.calendario.data.Task
import com.quarenta.calendario.data.AppUpdate
import com.quarenta.calendario.data.Updater
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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

/** Which full-screen sheet is open on top of the tabs. */
sealed interface Overlay {
    data class Detail(val event: CalendarEvent) : Overlay
    /** [editingId] is null for a new event. */
    data class Editor(val draft: EventDraft, val editingId: Long? = null, val fromOtherApp: Boolean = false) : Overlay
    data class PersonCard(val person: Person) : Overlay
    data object Appearance : Overlay
    data object Reminders : Overlay
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val calendars = CalendarRepository(app)
    private val taskDao = AppDatabase.get(app).tasks()
    private val personDao = AppDatabase.get(app).people()
    val contacts = ContactsRepository(app)

    private val _hasContactsAccess = MutableStateFlow(checkContacts())
    val hasContactsAccess: StateFlow<Boolean> = _hasContactsAccess.asStateFlow()

    private val _people = MutableStateFlow<List<Person>>(emptyList())
    val people: StateFlow<List<Person>> = _people.asStateFlow()

    private val _hasCalendarAccess = MutableStateFlow(checkAccess())
    val hasCalendarAccess: StateFlow<Boolean> = _hasCalendarAccess.asStateFlow()

    private val _events = MutableStateFlow<List<CalendarEvent>>(emptyList())
    /** Events from every calendar on the phone, with duplicates merged. */
    val events: StateFlow<List<CalendarEvent>> = _events.asStateFlow()

    private val _writable = MutableStateFlow<List<CalendarInfo>>(emptyList())
    val writableCalendars: StateFlow<List<CalendarInfo>> = _writable.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _overlay = MutableStateFlow<Overlay?>(null)
    val overlay: StateFlow<Overlay?> = _overlay.asStateFlow()

    /** Open sheets, newest last, so "back" returns to the previous one (person -> event -> back to person). */
    private val stack = mutableListOf<Overlay>()

    private fun push(o: Overlay) { stack += o; _overlay.value = o }

    private fun pop() { stack.removeLastOrNull(); _overlay.value = stack.lastOrNull() }

    private fun replaceAll(o: Overlay) { stack.clear(); push(o) }

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val tasks: StateFlow<List<Task>> = taskDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Reload whenever any calendar on the phone changes (new sync, edit elsewhere). */
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }
    private var observing = false
    private var loadedRange: ClosedRange<LocalDate> = LocalDate.MIN..LocalDate.MIN

    // ---- App updates ------------------------------------------------------------------

    private val _update = MutableStateFlow<AppUpdate?>(null)
    /** A newer version on GitHub, or null when up to date. */
    val update: StateFlow<AppUpdate?> = _update.asStateFlow()

    private val _updateProgress = MutableStateFlow<Float?>(null)
    /** Download progress 0..1 while updating, null otherwise. */
    val updateProgress: StateFlow<Float?> = _updateProgress.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private var lastUpdateCheck = 0L

    fun checkForUpdate(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastUpdateCheck < 30 * 60_000) return
        lastUpdateCheck = now
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { Updater.check(BuildConfig.VERSION_NAME) }
            if (found != null || force) _update.value = found
        }
    }

    /** Downloads the new version and opens Android's installer (it updates in place). */
    fun startUpdate() {
        val update = _update.value ?: return
        if (_updateProgress.value != null) return
        val app = getApplication<Application>()
        if (!Updater.canInstall(app)) {
            _message.value = "Autoriza a FortyCalendar a instalar atualizações e volta a carregar em Atualizar"
            Updater.openInstallPermission(app)
            return
        }
        _updateProgress.value = 0f
        viewModelScope.launch {
            val apk = withContext(Dispatchers.IO) {
                Updater.download(app, update) { p -> _updateProgress.value = p }
            }
            _updateProgress.value = null
            if (apk == null) _message.value = "Não foi possível descarregar. Verifica a internet e tenta outra vez."
            else Updater.install(app, apk)
        }
    }

    /** Pull down: reload calendars and people, and look for a new version. */
    fun pullToRefresh() {
        if (_refreshing.value) return
        _refreshing.value = true
        refresh()
        refreshPeople()
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { Updater.check(BuildConfig.VERSION_NAME) }
            lastUpdateCheck = System.currentTimeMillis()
            _update.value = found
            delay(400)
            _refreshing.value = false
            if (found == null) _message.value = "Tudo atualizado"
        }
    }

    init {
        refresh()
        refreshPeople()
        checkForUpdate()
    }

    fun onPermissionResult() {
        _hasCalendarAccess.value = checkAccess()
        refresh()
        refreshPeople()
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) { runCatching { com.quarenta.calendario.reminders.ReminderScheduler.reschedule(app) } }
    }

    fun refreshPeople() {
        _hasContactsAccess.value = checkContacts()
        if (!checkContacts()) return
        viewModelScope.launch {
            _people.value = withContext(Dispatchers.IO) { runCatching { contacts.all() }.getOrDefault(emptyList()) }
        }
    }

    fun openPerson(person: Person) = push(Overlay.PersonCard(person))

    fun openAppearance() = push(Overlay.Appearance)

    fun openReminders() = push(Overlay.Reminders)

    // Full-size contact photos for the big agenda cards, decoded once and kept while the app is open.
    private val photoCache = android.util.LruCache<Long, android.graphics.Bitmap>(24)

    private val birthYears = mutableMapOf<Long, Int?>()

    /** The person's year of birth from the phone's contacts, for "34º aniversário". */
    suspend fun birthYearFor(person: Person): Int? {
        if (birthYears.containsKey(person.id)) return birthYears[person.id]
        val year = withContext(Dispatchers.IO) { runCatching { contacts.details(person).birthYear }.getOrNull() }
        birthYears[person.id] = year
        return year
    }

    private val planDao = AppDatabase.get(app).plans()

    fun planFor(key: String): Flow<EventPlan?> = planDao.observe(key)

    fun savePlan(plan: EventPlan) {
        viewModelScope.launch { planDao.save(plan.copy(updatedAt = System.currentTimeMillis())) }
    }

    /** The contact an event is about, found by name in its title. */
    fun personIn(event: CalendarEvent): Person? {
        val list = people.value
        val id = com.quarenta.calendario.reminders.PersonMatch.inTitle(event.title, list.map { it.id to it.name }) ?: return null
        return list.firstOrNull { it.id == id }
    }

    /** The largest photo the phone has for [person], or null. Runs off the main thread. */
    suspend fun photoFor(person: Person): android.graphics.Bitmap? {
        photoCache.get(person.id)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) { runCatching { contacts.photo(person) }.getOrNull() } ?: return null
        photoCache.put(person.id, bitmap)
        return bitmap
    }

    /** For "Experimentar": the next event on the agenda, or an example when there is none. */
    fun sampleReminder(): ReminderAlert {
        val now = java.time.Instant.now()
        val e = events.value.firstOrNull { !it.allDay && it.start.isAfter(now) }
        return if (e != null) ReminderAlert(
            e.id, e.title, e.start.toEpochMilli(), e.end.toEpochMilli(), false, e.location, e.color, now.toEpochMilli(),
        ) else ReminderAlert(
            -1, "Exemplo: café às 10h", now.plusSeconds(900).toEpochMilli(), now.plusSeconds(4500).toEpochMilli(),
            false, "Pastelaria do bairro", 0, now.toEpochMilli(),
        )
    }

    private val prefs = app.getSharedPreferences("forty", android.content.Context.MODE_PRIVATE)

    init {
        ThemeState.selectedId = prefs.getString("theme", Themes.SYSTEM) ?: Themes.SYSTEM
    }

    /** Applies a look at once and remembers it for next time. */
    fun setTheme(id: String) {
        ThemeState.selectedId = id
        prefs.edit().putString("theme", id).apply()
    }

    fun say(text: String) { _message.value = text }

    fun canWriteContacts(): Boolean =
        ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.WRITE_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** Star or unstar a person. The open card updates at once; the list reloads from the phone. */
    fun toggleFavourite(person: Person) {
        if (!canWriteContacts()) {
            _message.value = "Sem autorização para alterar os favoritos"
            return
        }
        val wanted = !person.starred
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { contacts.setStarred(person, wanted) }.getOrDefault(false) }
            if (!ok) {
                _message.value = "Não foi possível alterar os favoritos"
                return@launch
            }
            val updated = person.copy(starred = wanted)
            for (i in stack.indices) {
                val o = stack[i]
                if (o is Overlay.PersonCard && o.person.id == person.id) stack[i] = Overlay.PersonCard(updated)
            }
            _overlay.value = stack.lastOrNull()
            _people.value = _people.value.map { if (it.id == person.id) updated else it }
            _message.value = if (wanted) "${person.name} está nos favoritos" else "${person.name} saiu dos favoritos"
            refreshPeople()
        }
    }

    fun noteFor(key: String): Flow<PersonNote?> = personDao.observeNote(key)
    fun fieldsFor(key: String): Flow<List<PersonField>> = personDao.observeFields(key)

    fun saveNote(key: String, text: String) {
        viewModelScope.launch { personDao.saveNote(PersonNote(lookupKey = key, notes = text)) }
    }

    fun addField(key: String, label: String, value: String) {
        if (label.isBlank() && value.isBlank()) return
        viewModelScope.launch { personDao.addField(PersonField(lookupKey = key, label = label.trim(), value = value.trim())) }
    }

    fun deleteField(id: Long) {
        viewModelScope.launch { personDao.deleteField(id) }
    }

    fun selectDate(date: LocalDate) {
        val reload = !loadedRange.contains(date)
        _selectedDate.value = date
        if (reload) refresh()
    }

    fun refresh() {
        _hasCalendarAccess.value = checkAccess()
        if (!checkAccess()) return
        if (!observing) {
            getApplication<Application>().contentResolver
                .registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
            observing = true
        }
        val anchor = minOf(_selectedDate.value, LocalDate.now())
        val from = anchor.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
        val to = maxOf(_selectedDate.value, LocalDate.now()).plusDays(42)
        viewModelScope.launch {
            val (loaded, writable) = withContext(Dispatchers.IO) {
                val ev = runCatching { Dedup.merge(calendars.eventsBetween(from, to)) }.getOrDefault(emptyList())
                val cals = runCatching { calendars.writableCalendars() }.getOrDefault(emptyList())
                ev to cals
            }
            loadedRange = from..to.minusDays(1)
            _events.value = loaded
            _writable.value = writable
        }
    }

    // ---- Overlays: detail and editor ------------------------------------------------

    fun openEvent(event: CalendarEvent) = push(Overlay.Detail(event))

    fun newEvent(date: LocalDate = _selectedDate.value) {
        val zone = java.time.ZoneId.systemDefault()
        val now = java.time.LocalTime.now()
        val hour = if (date == LocalDate.now()) (now.hour + 1).coerceAtMost(23) else 9
        val start = date.atTime(hour, 0).atZone(zone).toInstant()
        push(Overlay.Editor(EventDraft(start = start, end = start.plusSeconds(3600))))
    }

    fun editEvent(event: CalendarEvent) {
        push(Overlay.Editor(
            EventDraft(
                title = event.title, start = event.start, end = event.end, allDay = event.allDay,
                location = event.location.orEmpty(), description = event.description.orEmpty(),
                calendarId = event.calendarId, reminderMinutes = null,
            ),
            editingId = event.id,
        ))
    }

    fun closeOverlay() = pop()

    fun save(draft: EventDraft, editingId: Long?) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    if (editingId == null) calendars.insert(draft) != null else calendars.update(editingId, draft)
                }.getOrDefault(false)
            }
            if (ok) {
                pop() // the editor
                // The event detail underneath now shows old data: close it too.
                if (editingId != null && stack.lastOrNull() is Overlay.Detail) pop()
                _message.value = if (editingId == null) "Compromisso guardado" else "Alterações guardadas"
                _selectedDate.value = draft.start.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                refresh()
            } else {
                _message.value = "Não foi possível guardar. Confirma o acesso ao calendário."
            }
        }
    }

    fun delete(event: CalendarEvent) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { calendars.delete(event.id) }.getOrDefault(false) }
            _message.value = if (ok) "Compromisso apagado" else "Este calendário não permite apagar"
            if (ok) { if (stack.lastOrNull() is Overlay.Detail) pop(); refresh() }
        }
    }

    fun messageShown() { _message.value = null }

    /** Handles "Add to calendar" buttons and .ics files sent by other apps. */
    fun handleIntent(intent: Intent?) {
        val request = IncomingEvent.parse(getApplication(), intent) ?: return
        when (request) {
            is IncomingRequest.Add -> replaceAll(Overlay.Editor(request.draft, fromOtherApp = true))
            is IncomingRequest.Show -> viewModelScope.launch {
                val e = withContext(Dispatchers.IO) { runCatching { calendars.eventById(request.eventId) }.getOrNull() }
                if (e != null) replaceAll(Overlay.Detail(e))
            }
        }
    }

    // ---- Tasks ---------------------------------------------------------------------

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

    private fun checkContacts(): Boolean =
        ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    private fun checkAccess(): Boolean =
        ContextCompat.checkSelfPermission(getApplication(), Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    override fun onCleared() {
        if (observing) getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }
}
