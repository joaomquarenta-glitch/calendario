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
import kotlinx.coroutines.flow.map
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
    data class Editor(
        val draft: EventDraft,
        val editingId: Long? = null,
        val fromOtherApp: Boolean = false,
        /** The occurrence being edited, so a repeating one can change alone. */
        val event: CalendarEvent? = null,
        /** Words from the quick add on Início, understood in the editor. */
        val quick: String = "",
    ) : Overlay
    data class PersonCard(val person: Person) : Overlay
    data object Appearance : Overlay
    data object Reminders : Overlay
    /** A picture shared from another app (Instagram, WhatsApp, Galeria...): whose photo is it? */
    data class PhotoTarget(val uri: android.net.Uri) : Overlay
    data object Search : Overlay
    data object Birthdays : Overlay
    data object Backup : Overlay
    data class TaskEditor(val taskId: Long) : Overlay
    data class Calendars(val prefillUrl: String? = null) : Overlay
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
    private val _birthdays = MutableStateFlow<List<com.quarenta.calendario.data.Birthdays.Entry>>(emptyList())
    /** Everyone's next birthday from the phone's contacts, soonest first. */
    val birthdays: StateFlow<List<com.quarenta.calendario.data.Birthdays.Entry>> = _birthdays.asStateFlow()


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

    /** Every task, steps included. */
    val allTasks: StateFlow<List<Task>> = taskDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The tasks shown in lists: steps stay inside their task. */
    val tasks: StateFlow<List<Task>> = allTasks
        .map { all -> all.filter { it.parentId == null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        // Keep one alarm per task reminder, whatever changed the tasks.
        viewModelScope.launch(Dispatchers.IO) {
            taskDao.observeAll().collect { runCatching { com.quarenta.calendario.reminders.TaskReminders.sync(app, it) } }
        }
    }

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
        syncSubscriptions(force = true)
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
        // Events, people and the update check are loaded by MainActivity.onResume, which always
        // follows; loading them here too made every launch read the calendars and contacts twice.
        syncSubscriptions(force = false)
    }

    fun onPermissionResult() {
        _hasCalendarAccess.value = checkAccess()
        refresh()
        refreshPeople()
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) { runCatching { com.quarenta.calendario.reminders.ReminderScheduler.reschedule(app) } }
        viewModelScope.launch(Dispatchers.IO) { runCatching { com.quarenta.calendario.reminders.MorningBrief.schedule(app) } }
    }

    fun refreshPeople() {
        _hasContactsAccess.value = checkContacts()
        if (!checkContacts()) return
        viewModelScope.launch {
            _people.value = withContext(Dispatchers.IO) { runCatching { contacts.all() }.getOrDefault(emptyList()) }
            refreshBirthdays()
        }
    }

    private suspend fun refreshBirthdays() {
        val list = people.value
        _birthdays.value = withContext(Dispatchers.IO) {
            runCatching {
                val dates = contacts.birthdays()
                com.quarenta.calendario.data.Birthdays.upcoming(
                    list.mapNotNull { p -> dates[p.id]?.let { Triple(p.id, p.name, it) } },
                    java.time.LocalDate.now(),
                )
            }.getOrDefault(emptyList())
        }
    }

    fun openBirthdays() = push(Overlay.Birthdays)
    fun openBackup() = push(Overlay.Backup)

    /** The person's mobile number (for WhatsApp), or any number they have. */
    suspend fun phoneFor(person: Person): String? = withContext(Dispatchers.IO) {
        runCatching {
            val phones = contacts.details(person).fields.filter { it.kind == com.quarenta.calendario.data.FieldKind.Phone }
            (phones.firstOrNull { it.type == android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE } ?: phones.firstOrNull())?.value
        }.getOrNull()
    }

    fun openPerson(person: Person) = push(Overlay.PersonCard(person))

    fun openAppearance() = push(Overlay.Appearance)

    fun openReminders() = push(Overlay.Reminders)

    // Full-size contact photos for the big agenda cards, decoded once and kept while the app is open.
    // Sized by memory, because chosen photos are kept in high resolution.
    private val photoCache = object : android.util.LruCache<Long, android.graphics.Bitmap>(
        (Runtime.getRuntime().maxMemory() / 8).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
    ) {
        override fun sizeOf(key: Long, value: android.graphics.Bitmap) = value.allocationByteCount
    }
    private val chosenPhotos = com.quarenta.calendario.data.PersonPhotos(app)

    /** When the high-resolution photo chosen for [person] was taken, or null if they use the contact's photo. */
    fun chosenPhotoDate(person: Person): java.time.LocalDate? = chosenPhotos.takenAt(person)

    /** Keeps [uri] as [person]'s photo, in high resolution, everywhere in the app and on the widget. */
    fun setPhoto(person: Person, uri: android.net.Uri, openCard: Boolean = false) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { chosenPhotos.save(person, uri) }
            if (!ok) {
                _message.value = "Não foi possível usar esta fotografia"
                return@launch
            }
            photoCache.remove(person.id)
            com.quarenta.calendario.widget.NextWidget.refresh(getApplication())
            if (openCard) replaceAll(Overlay.PersonCard(person))
            _message.value = "Nova fotografia de ${person.name.substringBefore(' ')}"
        }
    }

    /** Goes back to the photo stored in the phone's contact. */
    fun resetPhoto(person: Person) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { chosenPhotos.remove(person) }
            photoCache.remove(person.id)
            com.quarenta.calendario.widget.NextWidget.refresh(getApplication())
            _message.value = "Voltou a fotografia dos contactos"
        }
    }

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
        val bitmap = withContext(Dispatchers.IO) { runCatching { contacts.photo(person, maxPx = 2048) }.getOrNull() } ?: return null
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
        ThemeState.selectedId = Themes.load(prefs)
        // Any change to the tasks, here or from the widget, redraws the tasks widget.
        viewModelScope.launch { taskDao.observeAll().collect { com.quarenta.calendario.widget.TasksWidget.refresh(app) } }
    }

    // The forecast for the next days where the phone roughly is, beside each day of the agenda.
    private val _weather = MutableStateFlow<Map<LocalDate, com.quarenta.calendario.data.DayWeather>>(emptyMap())
    val weather: StateFlow<Map<LocalDate, com.quarenta.calendario.data.DayWeather>> = _weather.asStateFlow()

    /** True while the agenda should offer to show the weather: no permission yet, and not turned down. */
    private val _weatherAsk = MutableStateFlow(false)
    val weatherAsk: StateFlow<Boolean> = _weatherAsk.asStateFlow()

    fun refreshWeather() {
        val app = getApplication<Application>()
        _weatherAsk.value = !com.quarenta.calendario.data.WeatherSource.hasPermission(app) && !prefs.getBoolean("weather_declined", false)
        viewModelScope.launch {
            val days = withContext(Dispatchers.IO) { com.quarenta.calendario.data.WeatherSource.home(app) }
            if (days.isNotEmpty()) _weather.value = days
        }
    }

    fun declineWeather() {
        prefs.edit().putBoolean("weather_declined", true).apply()
        _weatherAsk.value = false
    }

    /** The forecast for an event's day, at its place when the place can be found. Null beyond the forecast. */
    suspend fun weatherFor(event: CalendarEvent): com.quarenta.calendario.data.DayWeather? {
        val app = getApplication<Application>()
        val day = event.start.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        if (day.isBefore(LocalDate.now()) || day.isAfter(LocalDate.now().plusDays(com.quarenta.calendario.data.Weather.DAYS - 1L))) return null
        return withContext(Dispatchers.IO) { com.quarenta.calendario.data.WeatherSource.at(app, event.location)[day] }
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

    /** Deletes a person from the phone's contacts and closes their card if it is open. */
    fun deletePerson(person: Person) {
        if (!canWriteContacts()) {
            _message.value = "Sem autorização para alterar os contactos"
            return
        }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { contacts.delete(person) }
            if (!ok) {
                _message.value = "Não foi possível apagar ${person.name}"
                return@launch
            }
            stack.removeAll { it is Overlay.PersonCard && it.person.id == person.id }
            _overlay.value = stack.lastOrNull()
            photoCache.remove(person.id)
            _people.value = _people.value.filter { it.id != person.id }
            _message.value = "${person.name} foi apagado dos contactos"
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
        searchPool = null
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
                val hidden = com.quarenta.calendario.data.CalendarChoice.hidden(getApplication())
                val all = runCatching { calendars.writableCalendars() }.getOrDefault(emptyList())
                // New events go to calendars that are shown, unless every one is hidden.
                val cals = all.filter { it.id !in hidden }.ifEmpty { all }
                ev to cals
            }
            loadedRange = from..to.minusDays(1)
            _events.value = loaded
            _writable.value = writable
        }
    }

    // ---- Overlays: detail and editor ------------------------------------------------

    fun openEvent(event: CalendarEvent) = push(Overlay.Detail(event))

    // ---- Search: a year back and two years ahead, read once and kept until a calendar changes.

    fun openSearch() = push(Overlay.Search)

    // ---- Which calendars this app shows -------------------------------------------------

    fun openCalendars() = push(Overlay.Calendars())

    // ---- Subscribed links (webcal / .ics / iCloud) ---------------------------------------

    val subscriptions = com.quarenta.calendario.data.Subscriptions.observe(app)

    /** Returns an error to show, or null when the link was added. */
    suspend fun addSubscription(url: String, name: String, color: Int): String? {
        val app = getApplication<Application>()
        val error = withContext(Dispatchers.IO) { com.quarenta.calendario.data.Subscriptions.add(app, url, name, color) }
        if (error == null) afterSubscriptionsChanged()
        return error
    }

    fun removeSubscription(sub: com.quarenta.calendario.data.Subscription) {
        val app = getApplication<Application>()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { com.quarenta.calendario.data.Subscriptions.remove(app, sub) }
            afterSubscriptionsChanged()
        }
    }

    private fun syncSubscriptions(force: Boolean) {
        val app = getApplication<Application>()
        if (!checkAccess() || com.quarenta.calendario.data.Subscriptions.all(app).isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { com.quarenta.calendario.data.Subscriptions.syncDue(app, if (force) 0L else 6 * 3600_000L) }
            }
            afterSubscriptionsChanged()
        }
    }

    private fun afterSubscriptionsChanged() {
        val app = getApplication<Application>()
        runCatching { com.quarenta.calendario.data.SubscriptionJob.schedule(app) }
        loadedRange = LocalDate.MIN..LocalDate.MIN
        refresh()
        com.quarenta.calendario.widget.NextWidget.refresh(app)
    }

    suspend fun allCalendars(): List<com.quarenta.calendario.data.CalendarInfo> = withContext(Dispatchers.IO) {
        if (!checkAccess()) emptyList() else runCatching { calendars.allCalendars() }.getOrDefault(emptyList())
    }

    val hiddenCalendars = com.quarenta.calendario.data.CalendarChoice.observe(app)

    fun setCalendarShown(id: Long, shown: Boolean) {
        val app = getApplication<Application>()
        com.quarenta.calendario.data.CalendarChoice.setShown(app, id, shown)
        loadedRange = LocalDate.MIN..LocalDate.MIN
        refresh()
        com.quarenta.calendario.widget.NextWidget.refresh(app)
    }

    @Volatile private var searchPool: List<CalendarEvent>? = null

    suspend fun searchPool(): List<CalendarEvent> = searchPool ?: withContext(Dispatchers.IO) {
        if (!checkAccess()) return@withContext emptyList()
        val today = LocalDate.now()
        runCatching { Dedup.merge(calendars.eventsBetween(today.minusYears(1), today.plusYears(2))) }
            .getOrDefault(emptyList())
            .also { searchPool = it }
    }

    /** From Início: what was typed or said ("Jantar com a Ana sexta 20h") opens a new appointment, filled in. */
    fun quickAdd(text: String) {
        if (text.isBlank()) return
        val zone = java.time.ZoneId.systemDefault()
        val start = LocalDate.now().atTime((java.time.LocalTime.now().hour + 1).coerceAtMost(23), 0).atZone(zone).toInstant()
        push(Overlay.Editor(EventDraft(start = start, end = start.plusSeconds(3600)), quick = text.trim()))
    }

    fun newEvent(date: LocalDate = _selectedDate.value, title: String = "") {
        val zone = java.time.ZoneId.systemDefault()
        val now = java.time.LocalTime.now()
        val hour = if (date == LocalDate.now()) (now.hour + 1).coerceAtMost(23) else 9
        val start = date.atTime(hour, 0).atZone(zone).toInstant()
        push(Overlay.Editor(EventDraft(title = title, start = start, end = start.plusSeconds(3600))))
    }

    /** Dragged on the hour-by-hour day: the appointment moves by [minutes], same length. */
    fun moveEvent(event: CalendarEvent, minutes: Int) {
        if (minutes == 0) return
        val shift = minutes * 60_000L
        val moved = event.copy(start = event.start.plusMillis(shift), end = event.end.plusMillis(shift))
        _events.value = _events.value.map { if (it.id == event.id && it.instanceBegin == event.instanceBegin) moved else it }
        val app = getApplication<Application>()
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { calendars.move(event, shift) }.getOrDefault(false) }
            val time = moved.start.atZone(java.time.ZoneId.systemDefault()).toLocalTime()
            _message.value = if (ok) "\"${event.title}\" passou para as %02d:%02d".format(time.hour, time.minute)
                else "Não foi possível mudar a hora"
            refresh()
            if (ok) withContext(Dispatchers.IO) { runCatching { com.quarenta.calendario.reminders.ReminderScheduler.reschedule(app) } }
        }
    }

    /** A new appointment at a chosen time, from tapping an empty hour. */
    fun newEventAt(date: LocalDate, time: java.time.LocalTime) {
        val start = date.atTime(time).atZone(java.time.ZoneId.systemDefault()).toInstant()
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
            event = event,
        ))
    }

    fun closeOverlay() = pop()

    fun save(draft: EventDraft, editingId: Long?) {
        viewModelScope.launch {
            // Where the occurrence was before editing, so a repeating event moves by the same amount.
            val editor = stack.lastOrNull { it is Overlay.Editor } as? Overlay.Editor
            val originalStart = editor?.draft?.start
            val instanceBegin = editor?.event?.instanceBegin
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (editingId == null) {
                        if (calendars.insert(draft) != null) CalendarRepository.UpdateResult.Saved else CalendarRepository.UpdateResult.Failed
                    } else {
                        calendars.update(editingId, draft, originalStart, instanceBegin = instanceBegin)
                    }
                }.getOrDefault(CalendarRepository.UpdateResult.Failed)
            }
            if (result == CalendarRepository.UpdateResult.ReadOnly) {
                _message.value = "Este compromisso vem de um calendário que só deixa ler. Não pode ser alterado aqui."
                return@launch
            }
            if (result == CalendarRepository.UpdateResult.Saved) {
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

    fun delete(event: CalendarEvent, onlyThis: Boolean = false) {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { calendars.delete(event.id, if (onlyThis && event.repeats) event.instanceBegin else null) }.getOrDefault(false)
            }
            _message.value = if (ok) "Compromisso apagado" else "Este calendário não permite apagar"
            if (ok) { if (stack.lastOrNull() is Overlay.Detail) pop(); refresh() }
        }
    }

    fun messageShown() { _message.value = null }

    /** Handles "Add to calendar" buttons and .ics files sent by other apps. */
    private val _tabRequest = MutableStateFlow<String?>(null)
    val tabRequest: StateFlow<String?> = _tabRequest.asStateFlow()
    fun tabShown() { _tabRequest.value = null }

    fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(MainActivity.EXTRA_TAB)?.let { tab ->
            intent.removeExtra(MainActivity.EXTRA_TAB)
            stack.clear()
            _overlay.value = null
            _tabRequest.value = tab
            return
        }
        val scheme = intent?.data?.scheme?.lowercase()
        if (intent?.action == Intent.ACTION_VIEW && (scheme == "webcal" || scheme == "webcals")) {
            replaceAll(Overlay.Calendars(prefillUrl = intent.data.toString()))
            return
        }
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            @Suppress("DEPRECATION")
            val uri = intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM)
            if (uri != null) replaceAll(Overlay.PhotoTarget(uri))
            return
        }
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

    /** Ticks a task; a repeating one moves to its next day with its steps undone. */
    fun toggleTask(task: Task) {
        viewModelScope.launch {
            val updated = com.quarenta.calendario.data.TaskRules.complete(task, LocalDate.now())
            taskDao.update(updated)
            if (updated.dueEpochDay != task.dueEpochDay) taskDao.reopenSteps(task.id)
            if (updated.done) com.quarenta.calendario.reminders.TaskReminders.dismiss(getApplication(), task.id)
        }
    }

    fun saveTask(task: Task) {
        if (task.title.isBlank()) return
        viewModelScope.launch { taskDao.update(task.copy(title = task.title.trim())) }
    }

    fun addStep(parent: Task, title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { taskDao.insert(Task(title = title.trim(), parentId = parent.id)) }
    }

    fun openTask(task: Task) = push(Overlay.TaskEditor(task.id))

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
