package com.dayflow.app.data

import android.content.Context
import com.dayflow.app.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Calendar

object Repo {
    lateinit var db: Db; lateinit var dao: Dao; lateinit var prefs: PrefsStore
    private lateinit var app: Context
    @Volatile private var initialized = false
    private val initLock = Mutex()
    private val _changes = MutableStateFlow(0L)
    /** Bumped on every write via touch(); widgets and polled UI re-query when it changes. */
    val changes: StateFlow<Long> = _changes

    /** Creates the DB/prefs handles. Cheap (Room opens lazily) and safe to call from the main thread. */
    @Synchronized fun setup(ctx: Context) {
        if (::db.isInitialized) return
        app = ctx.applicationContext
        db = Db.build(app); dao = db.dao(); prefs = PrefsStore(app)
    }

    /** setup() plus first-run seeding and the daily roll; runs once per process even under concurrent callers. */
    suspend fun init(ctx: Context) {
        setup(ctx)
        if (initialized) return
        initLock.withLock {
            if (initialized) return
            if (!prefs.sampleRemoved()) {
                if (dao.count() == 0) seedEssentials() else removeSampleData()   // fresh install vs. early test install that still has demo data
                prefs.markSampleRemoved()
            }
            rollDay(); initialized = true
        }
    }

    val habits get() = dao.habits()
    val logs get() = dao.logs()
    val tasks get() = dao.tasks()
    val items get() = dao.items()
    val moments get() = dao.moments()
    val mitems get() = dao.mitems()
    val runs get() = dao.runs()
    val sleeps get() = dao.sleeps()
    val meds get() = dao.meds()
    val medLogs get() = dao.medLogs()
    val reviews get() = dao.reviews()
    val views get() = dao.views()
    val active get() = dao.active()

    suspend fun p(): Prefs = prefs.flow.first()
    suspend fun logVal(d: String, h: String) = dao.log(d, h)?.value ?: 0
    suspend fun setLog(d: String, h: String, v: Int) { dao.putLog(HabitLog(d, h, v)); touch() }
    suspend fun water() = dao.habit("water")
    suspend fun addWater(delta: Int) {
        val h = water() ?: return
        setLog(today(), "water", (logVal(today(), "water") + delta).coerceIn(0, h.goal))
    }
    suspend fun setWater(n: Int) { val h = water() ?: return; setLog(today(), "water", n.coerceIn(0, h.goal)) }
    suspend fun tickHabit(id: String) {
        val h = dao.habit(id) ?: return; val t = today(); val cur = logVal(t, id)
        setLog(t, id, if (cur > 0) 0 else if (h.type == "count") h.goal else 1)
    }
    suspend fun bumpHabit(id: String, d: Int) {
        val h = dao.habit(id) ?: return
        setLog(today(), id, (logVal(today(), id) + d).coerceIn(0, h.goal))
    }
    /** Idempotent completion, used by notification actions (which may be stale). */
    suspend fun completeHabit(id: String) {
        val h = dao.habit(id) ?: return
        val goal = if (h.type == "count") h.goal else 1
        if (logVal(today(), id) < goal) setLog(today(), id, goal)
    }
    suspend fun completeTask(id: String) {
        val t = dao.task(id) ?: return
        if (!t.done) { dao.putTask(t.copy(done = true, doneAt = nowMs())); touch() }
    }
    suspend fun toggleTask(id: String) {
        val t = dao.task(id) ?: return
        dao.putTask(t.copy(done = !t.done, doneAt = if (!t.done) nowMs() else null)); touch()
    }
    suspend fun toggleItem(id: String) {
        val i = dao.itemsOnce().find { it.id == id } ?: return
        dao.putItem(i.copy(done = !i.done)); touch()
    }
    fun scheduled(h: Habit, c: Calendar) = uncsv(h.days).contains(dow(c).toString())
    suspend fun doneHabit(h: Habit, d: String): Boolean {
        val v = logVal(d, h.id); return if (h.type == "count") v >= h.goal else v > 0
    }
    suspend fun streak(h: Habit): Int {
        val vals = dao.logsFor(h.id).associate { it.date to it.value }
        fun done(d: String) = (vals[d] ?: 0).let { if (h.type == "count") it >= h.goal else it > 0 }
        var s = 0; var c = Calendar.getInstance()
        if (scheduled(h, c) && !done(today())) c = addDays(-1)
        var g = 0
        while (g++ < 370) {
            if (scheduled(h, c)) { if (done(dstr(c))) s++ else break }
            c.add(Calendar.DAY_OF_YEAR, -1)
        }
        return s
    }
    suspend fun todayStats(): Triple<Int, Int, Int> {
        val t = today(); val c = Calendar.getInstance(); var done = 0; var total = 0
        val hs = dao.habitsOnce()
        hs.forEach { h -> if (scheduled(h, c)) { val g = if (h.type == "count") h.goal else 1; total += g; done += minOf(g, logVal(t, h.id)) } }
        val ts = dao.tasksOnce().filter { (it.date == t || it.date == null) && (!it.done || (it.doneAt != null && dstr(calOfMs(it.doneAt)) == t)) }
        ts.forEach { total++; if (it.done) done++ }
        val pct = if (total > 0) done * 100 / total else 0
        return Triple(pct, ts.count { !it.done }, hs.count { scheduled(it, c) && !doneHabit(it, t) })
    }
    suspend fun nextWater(): Int? {
        val h = water() ?: return null
        if (logVal(today(), "water") >= h.goal) return null
        val pp = p(); val st = tmin(pp.waterStart) ?: return null; val en = tmin(pp.waterEnd) ?: return null
        if (pp.waterInterval <= 0) return null
        val nm = nowMin()
        if (nm < st) return st
        val slot = st + ((nm - st) / pp.waterInterval + 1) * pp.waterInterval
        return if (slot <= en) slot else null
    }
    suspend fun nextReminder(): Pair<String, String>? {
        val nm = nowMin(); val t = today(); var best: Pair<Int, String>? = null
        nextWater()?.let { if (it > nm) best = it to "💧 Water" }
        dao.habitsOnce().forEach { h ->
            if (h.id == "water" || h.time == null || !scheduled(h, Calendar.getInstance()) || doneHabit(h, t)) return@forEach
            val m = tmin(h.time) ?: return@forEach; if (m > nm && (best == null || m < best!!.first)) best = m to "${h.icon} ${h.name}"
        }
        dao.tasksOnce().forEach { x ->
            if (x.done || x.time == null || (x.date != null && x.date != t)) return@forEach
            val m = tmin(x.time) ?: return@forEach; if (m > nm && (best == null || m < best!!.first)) best = m to "⏰ ${x.title}"
        }
        return best?.let { it.second to fmtMin(it.first) }
    }
    suspend fun startMoment(id: String) {
        val m = dao.moment(id) ?: return; val now = nowMs(); val pp = p()
        if (m.mkey == "sleep" && !pp.sleeping) prefs.sleeping(true, now)
        if (m.mkey == "wake" && pp.sleeping) {
            val mins = ((now - pp.sleepStart) / 60000L).toInt().coerceAtLeast(1)
            dao.putSleep(SleepEntry(uid(), today(), pp.sleepStart, now, mins)); prefs.sleeping(false, 0L)
        }
        dao.putActive(Active(momentId = id, startedAt = now, checked = "", note = "")); touch()
    }
    suspend fun setChecked(itemId: String, on: Boolean) {
        val a = dao.activeOnce() ?: return
        val s = uncsv(a.checked).toMutableList()
        if (on) { if (!s.contains(itemId)) s.add(itemId) } else s.remove(itemId)
        dao.putActive(a.copy(checked = csv(s))); touch()
    }
    suspend fun setNote(n: String) { val a = dao.activeOnce() ?: return; dao.putActive(a.copy(note = n)) }
    suspend fun finishMoment() {
        val a = dao.activeOnce() ?: return; val m = dao.moment(a.momentId) ?: return
        val its = dao.mitemsOnce().filter { it.momentId == m.id }; val ck = uncsv(a.checked)
        dao.putRun(MomentRun(uid(), m.id, today(), nowMs(), ck.size, its.size,
            csv(its.filter { !ck.contains(it.id) }.map { it.text }), a.note))
        dao.putMoment(m.copy(runs = m.runs + 1, lastRun = nowMs()))
        dao.clearActive(); touch()
    }
    suspend fun forgotten(momentId: String): List<Pair<String, Int>> {
        val counts = mutableMapOf<String, Int>()
        dao.runsOnce().filter { it.momentId == momentId }.forEach { r -> uncsv(r.unchecked).forEach { counts[it] = (counts[it] ?: 0) + 1 } }
        return counts.entries.sortedByDescending { it.value }.take(3).map { it.key to it.value }
    }
    /** MedLog rows are per dose: medId is "<medicineId>@<HH:mm>" so taking 09:00 doesn't hide 21:00. */
    fun doseKey(medId: String, time: String) = "$medId@$time"
    fun medNotifKey(medId: String, date: String, time: String) = "med:$medId:$date:$time"
    suspend fun doseTaken(medId: String, time: String, date: String = today()) =
        dao.medLog(date, doseKey(medId, time))?.takenAt != null
    suspend fun markTaken(medId: String, time: String) {
        val t = today(); val k = doseKey(medId, time)
        dao.putMedLog(MedLog(t, k, nowMs(), null)); dao.delSnooze(medNotifKey(medId, t, time)); touch()
    }
    suspend fun snooze(key: String, minutes: Int) {
        dao.delNotified(key); dao.putSnooze(Snooze(key, nowMs() + minutes * 60000L)); touch()
        com.dayflow.app.notify.runAfter(app, key, minutes)  // periodic worker is 15 min; wake precisely for short snoozes
    }
    suspend fun medDue(): List<Pair<Medicine, String>> {
        val t = today(); val nm = nowMin(); val out = mutableListOf<Pair<Medicine, String>>()
        val now = nowMs(); val snoozed = dao.snoozes().filter { it.until > now }.map { it.key }.toSet()
        dao.medsOnce().filter { it.enabled }.forEach { m ->
            uncsv(m.times).forEach { tm ->
                val x = tmin(tm) ?: return@forEach
                if (x <= nm && !doseTaken(m.id, tm) && medNotifKey(m.id, t, tm) !in snoozed) out.add(m to tm)
            }
        }
        return out
    }
    suspend fun generateReview(): Review {
        val ds = last7(); var done = 0; var total = 0; var best = 0; var wsum = 0; var runs = 0
        val hs = dao.habitsOnce(); val rs = dao.runsOnce()
        hs.forEach { h -> best = maxOf(best, streak(h))
            ds.forEach { d -> if (scheduled(h, calOf(d))) { total++; if (doneHabit(h, d)) done++ } } }
        ds.forEach { d -> wsum += logVal(d, "water"); runs += rs.count { it.date == d } }
        val td = dao.tasksOnce().count { it.doneAt != null && ds.contains(dstr(calOfMs(it.doneAt))) }
        val sl = dao.sleepsOnce().filter { ds.contains(it.date) }
        val sa = if (sl.isEmpty()) 0 else sl.sumOf { it.minutes } / sl.size
        val pct = if (total > 0) done * 100 / total else 0
        val wk = weekKey(Calendar.getInstance())
        val r = Review("review-$wk", wk, today(),
            "$pct% habits · $td tasks done · ${fmtDur(sa)} avg sleep", pct, td,
            String.format(java.util.Locale.US, "%.1f", wsum / 7.0), sa, runs, best, nowMs())
        dao.putReview(r); touch(); return r
    }
    suspend fun saveTomorrowPlan(lines: List<String>) {
        val tm = dstr(addDays(1)); var order = (dao.tasksOnce().maxOfOrNull { it.sortOrder } ?: 0) + 1
        lines.map { it.trim() }.filter { it.isNotEmpty() }.forEach {
            dao.putTask(Task(uid(), it, "note", tm, null, null, "none", false, false, null, nowMs(), order++))
        }
        touch()
    }
    /** Re-opens repeating tasks completed on an earlier day. Runs on every start and reminder tick, so it must be idempotent. */
    suspend fun rollDay() {
        val t = today(); val dw = dow(Calendar.getInstance())
        dao.tasksOnce().forEach { x ->
            val repeatsToday = x.repeat == "daily" || (x.repeat == "weekdays" && dw in 1..5)
            if (!repeatsToday) return@forEach
            val doneEarlier = x.done && (x.doneAt == null || dstr(calOfMs(x.doneAt)) != t)
            val missed = !x.done && x.date != null && x.date < t   // open from a past day: bring it forward
            if (doneEarlier || missed) dao.putTask(x.copy(done = false, doneAt = null, date = t))
            if (doneEarlier) dao.itemsOnce().filter { it.taskId == x.id }.forEach { dao.putItem(it.copy(done = false)) }
        }
        // reminder bookkeeping keys are date-scoped; drop anything older than two days so the tables don't grow forever
        val cut = nowMs() - 2 * 86_400_000L
        dao.pruneNotified(cut); dao.pruneSnoozes(cut)
    }
    suspend fun touch() {
        _changes.value = _changes.value + 1
        com.dayflow.app.widget.refreshAll(app)
        com.dayflow.app.notify.schedule(app)
    }
    suspend fun resetAll() {
        withContext(Dispatchers.IO) { db.clearAllTables() }  // blocking call; Room forbids it on the main thread
        prefs.clear(); seedEssentials(); prefs.markSampleRemoved(); touch()
    }

    /**
     * Built-in setup a fresh install needs, with no activity data: the Water habit (the water card, widget and
     * reminders look it up by id "water") and the four daily routines (sleep tracking, the morning agenda and the
     * quick-start chips depend on their mkeys). Everything else is created by the user.
     */
    private suspend fun seedEssentials() {
        dao.putHabit(Habit("water","Drink water","💧","#39D0D8","count",p().waterGoal,"glasses","0|1|2|3|4|5|6",null,true,0))
        listOf(
            Moment("m-wake","wake","routine","I woke up","🌅","#FFB347","wake|wake up|woke up|walk up|good morning|morning",0,null,0),
            Moment("m-office","office","routine","Before office","🏢","#5EA8FF","office|work|leaving for office|going to office",0,null,1),
            Moment("m-home","home","routine","Back home","🏡","#4ADE80","home|back home|reached home|returned",0,null,2),
            Moment("m-sleep","sleep","routine","Going to sleep","🌙","#8B7BFF","sleep|night|bed|going to bed|good night",0,null,3)
        ).forEach { dao.putMoment(it) }
        mapOf(
            "m-wake" to listOf("Drink a glass of water","Brush & freshen up","5-minute stretch","Make the bed","Check today's plan"),
            "m-office" to listOf("ID card","Laptop + charger","Keys & wallet","Water bottle","Check today's meetings"),
            "m-home" to listOf("Wash up","Log water","Gym if scheduled","Family time","Prep tomorrow's bag"),
            "m-sleep" to listOf("Phone on charge","Set alarm","Review tomorrow","Lights off","Gratitude note")
        ).forEach { (k, l) -> l.forEachIndexed { i, s -> dao.putMItem(MomentItem(uid(), k, s, i)) } }
    }

    /**
     * One-time cleanup for early test installs of v1.0 whose first launch wrote demo data (six days of fake
     * history, sample tasks/habits/places/medicine/views). Removes only what still matches that demo data;
     * anything the user created, used or edited is kept. Internal for tests.
     */
    internal suspend fun removeSampleData() {
        // 1) Install day. The demo sleep entries have these exact durations with start/end exactly that far apart
        //    (a real tracked sleep is measured in ms, so an exact match is practically impossible).
        val demoSleeps = dao.sleepsOnce().filter { it.minutes in LEGACY_SLEEP_MINUTES && it.end - it.start == it.minutes * 60_000L }
        val demoRuns = dao.runsOnce().filter { r -> LEGACY_RUNS.any { it == listOf(r.momentId, r.done, r.total, r.unchecked) } && r.note.isEmpty() }
        val lastFakeDay = (demoSleeps.map { it.date } + demoRuns.map { it.date }).maxOrNull()
        if (lastFakeDay != null && demoSleeps.size + demoRuns.size >= 3) {
            val installDay = dstr(calOf(lastFakeDay).apply { add(Calendar.DAY_OF_YEAR, 1) })
            // The app only ever records *today*, so nothing real can be dated before the install day.
            dao.delLogsBefore(installDay); dao.delSleepsBefore(installDay); dao.delRunsBefore(installDay)
            // Install day: the demo pre-filled 3 glasses and ticked walk + meditate; keep only what the user added.
            dao.log(installDay, "water")?.let { if (it.value <= 3) dao.delLog(installDay, "water") else dao.putLog(it.copy(value = it.value - 3)) }
            dao.delLog(installDay, "walk"); dao.delLog(installDay, "meditate")
        }
        // 2) Demo tasks, matched on every seeded field the user could have edited (done/date may have changed).
        dao.tasksOnce().filter { t -> LEGACY_TASKS.any { it == listOf(t.title, t.kind, t.time, t.placeId, t.repeat) } }
            .forEach { dao.delTask(it.id); dao.delItemsOf(it.id) }
        // 3) Demo habits, unless renamed or logged by the user.
        val logged = dao.logsOnce().filter { it.value > 0 }.map { it.habitId }.toSet()
        LEGACY_HABITS.forEach { (id, name) ->
            val h = dao.habit(id) ?: return@forEach
            if (h.name == name && id !in logged) { dao.delHabit(id); dao.delLogsOf(id) }
        }
        // 4) Demo places, unless renamed, run by the user, or used by a task.
        val usedPlaces = dao.tasksOnce().mapNotNull { it.placeId }.toSet()
        LEGACY_PLACES.forEach { (id, name) ->
            val m = dao.moment(id) ?: return@forEach
            if (m.name == name && m.runs == 0 && id !in usedPlaces && dao.runsOnce().none { it.momentId == id }) {
                dao.delMoment(id); dao.delMItemsOf(id)
            }
        }
        // 5) Example medicine (seeded disabled), saved views, and reviews computed from the fake history.
        dao.medsOnce().find { it.id == "med-example" && it.name == "Example medicine" && !it.enabled }
            ?.let { dao.delMed(it.id); dao.delMedLogsOf(it.id) }
        dao.viewsOnce().filter { v -> LEGACY_VIEWS.any { it == listOf(v.name, v.filter, v.place, v.q, v.sort) } }.forEach { dao.delView(it.id) }
        dao.delReviews()
    }

    private val LEGACY_SLEEP_MINUTES = (1..6).map { 390 + (it * 17) % 120 }.toSet()
    private val LEGACY_RUNS = listOf(
        listOf("m-wake", 5, 5, ""), listOf("m-home", 4, 5, "Log water"),
        listOf("m-office", 3, 5, "ID card|Laptop + charger"), listOf("m-market", 2, 4, "Cloth bag|Wallet / UPI"))
    private val LEGACY_TASKS = listOf(
        listOf("Prepare presentation", "checklist", "10:30", null, "none"),
        listOf("Pay electricity bill", "note", "17:00", null, "none"),
        listOf("Buy groceries", "checklist", null, "m-market", "none"),
        listOf("Call mom", "note", "20:00", null, "daily"),
        listOf("Pick up vitamins", "note", null, "m-pharmacy", "none"))
    private val LEGACY_HABITS = listOf("walk" to "Morning walk", "meditate" to "Meditate 10 min", "gym" to "Gym workout",
        "read" to "Read", "sleepby" to "Lights out by 11")
    private val LEGACY_PLACES = listOf("m-market" to "Market", "m-gym" to "Gym", "m-pharmacy" to "Pharmacy")
    private val LEGACY_VIEWS = listOf(listOf("🛒 Market run", "all", "m-market", "", "manual"), listOf("⏰ Timed today", "timed", "all", "", "time"))
}

sealed class Cmd {
    object Water : Cmd()
    data class Start(val id: String) : Cmd()
    data class Create(val name: String, val items: List<String>) : Cmd()
    data class Added(val n: Int, val name: String) : Cmd()
    data class Unknown(val raw: String) : Cmd()
}

object Commands {
    suspend fun match(q: String): Moment? {
        val n = norm(q); var best: Moment? = null; var bs = 0
        Repo.dao.momentsOnce().forEach { m ->
            (listOf(m.name) + uncsv(m.aliases)).forEach { a ->
                val na = norm(a); if (na.isEmpty()) return@forEach
                val sc = when { n == na -> 100 + na.length; n.contains(na) -> 50 + na.length
                    na.contains(n) && n.length > 2 -> 20 + n.length; else -> 0 }
                if (sc > bs) { bs = sc; best = m }
            }
        }
        return if (bs >= 30) best else null
    }
    suspend fun suggestions(q: String): List<Moment> {
        val n = norm(q); if (n.isEmpty()) return emptyList()
        return Repo.dao.momentsOnce().filter { m -> norm(m.name).contains(n) || uncsv(m.aliases).any { norm(it).contains(n) } }.take(4)
    }
    suspend fun parse(raw: String): Cmd {
        val q = raw.trim(); val n = norm(q); if (n.isEmpty()) return Cmd.Unknown(raw)
        val cl = Regex("^\\s*(?:add\\s+)?checklist\\s+([^:]+):\\s*(.+)$", RegexOption.IGNORE_CASE).find(q)
        if (cl != null) {
            val target = cl.groupValues[1].trim()
            val items = cl.groupValues[2].split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val mo = match(target)
            if (mo != null) {
                val ex = Repo.dao.mitemsOnce().filter { it.momentId == mo.id }
                var order = (ex.maxOfOrNull { it.sortOrder } ?: -1) + 1; var added = 0
                items.forEach { txt -> if (ex.none { norm(it.text) == norm(txt) }) { Repo.dao.putMItem(MomentItem(uid(), mo.id, txt, order++)); added++ } }
                Repo.touch(); return Cmd.Added(added, mo.name)
            }
            return Cmd.Create(titleCase(target), items)
        }
        if (Regex("\\b(water|glass|glasses|hydrate)\\b").containsMatchIn(n)
            && !Regex("going|market|office|sleep|wake|walk up").containsMatchIn(n)) return Cmd.Water
        match(n)?.let { return Cmd.Start(it.id) }
        val pm = Regex("\\b(?:going to|off to|heading to|head to|visiting|to the|to|at)\\s+(.+)").find(n)
        if (pm != null) {
            val name = pm.groupValues[1].trim()
            val pl = Repo.dao.momentsOnce().filter { it.type == "place" }.find {
                norm(it.name).contains(name) || name.contains(norm(it.name)) || uncsv(it.aliases).any { a -> norm(a).contains(name) } }
            if (pl != null) return Cmd.Start(pl.id)
            return Cmd.Create(titleCase(name), emptyList())
        }
        return Cmd.Unknown(raw)
    }
}
