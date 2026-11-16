package com.dayflow.app.ui

import android.app.Application
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dayflow.app.*
import com.dayflow.app.data.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.Calendar

class Vm(app: Application) : AndroidViewModel(app) {
    /** Compose State backed by a DB flow: reading `.value` in a composable subscribes it to updates. */
    private fun <T> Flow<T>.asState(initial: T): State<T> {
        val s = mutableStateOf(initial)
        viewModelScope.launch { collect { s.value = it } }
        return s
    }
    val prefs = Repo.prefs.flow.asState(Prefs())
    val habits = Repo.habits.asState(emptyList())
    val logs = Repo.logs.asState(emptyList())
    val tasks = Repo.tasks.asState(emptyList())
    val items = Repo.items.asState(emptyList())
    val moments = Repo.moments.asState(emptyList())
    val mitems = Repo.mitems.asState(emptyList())
    val runs = Repo.runs.asState(emptyList())
    val sleeps = Repo.sleeps.asState(emptyList())
    val meds = Repo.meds.asState(emptyList())
    val medLogs = Repo.medLogs.asState(emptyList())
    val reviews = Repo.reviews.asState(emptyList())
    val views = Repo.views.asState(emptyList())
    val active = Repo.active.asState(null)
    /** (date, habitId) -> value; streak() calls logVal hundreds of times per row, so avoid a linear scan each time. */
    private val logIndex by derivedStateOf { logs.value.associate { (it.date to it.habitId) to it.value } }

    var tab by mutableStateOf("today")
    var cmd by mutableStateOf("")
    var sugs by mutableStateOf<List<Moment>>(emptyList())
    var listening by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)
    var runner by mutableStateOf(false)
    var sheet by mutableStateOf<String?>(null)
    var editId by mutableStateOf<String?>(null)
    var draft by mutableStateOf<Any?>(null)
    var confirmFn by mutableStateOf<(() -> Unit)?>(null)
    var unknownCmd by mutableStateOf("")
    var createName by mutableStateOf("")
    var createItems by mutableStateOf<List<String>>(emptyList())
    var runConfirm by mutableStateOf(false)
    var planner by mutableStateOf(listOf("", "", ""))
    var review by mutableStateOf<Review?>(null)
    var tq by mutableStateOf(""); var tf by mutableStateOf("all")
    var tp by mutableStateOf("all"); var ts by mutableStateOf("manual")
    var selectMode by mutableStateOf(false); var selected by mutableStateOf(setOf<String>())
    var mq by mutableStateOf(""); var weekOffset by mutableStateOf(0)

    fun run(block: suspend () -> Unit) = viewModelScope.launch { block() }
    fun say(m: String) { toast = m }
    fun logVal(d: String, h: String) = logIndex[d to h] ?: 0
    fun doneHabit(h: Habit, d: String): Boolean { val v = logVal(d, h.id); return if (h.type == "count") v >= h.goal else v > 0 }
    fun streak(h: Habit): Int {
        var s = 0; var c = Calendar.getInstance()
        if (Repo.scheduled(h, c) && !doneHabit(h, today())) c = addDays(-1)
        var g = 0
        while (g++ < 370) { if (Repo.scheduled(h, c)) { if (doneHabit(h, dstr(c))) s++ else break }; c.add(Calendar.DAY_OF_YEAR, -1) }
        return s
    }
    fun itemsOf(id: String) = items.value.filter { it.taskId == id }.sortedBy { it.sortOrder }
    fun mitemsOf(id: String) = mitems.value.filter { it.momentId == id }.sortedBy { it.sortOrder }
    fun moment(id: String?) = moments.value.find { it.id == id }
    fun filteredTasks(): List<Task> {
        var l = tasks.value; val t = today()
        if (tq.isNotBlank()) { val q = tq.lowercase()
            l = l.filter { it.title.lowercase().contains(q) || itemsOf(it.id).any { i -> i.text.lowercase().contains(q) } } }
        l = when (tf) {
            "all" -> l.filter { !it.done }
            "today" -> l.filter { !it.done && (it.date == t || it.date == null) }
            "timed" -> l.filter { it.time != null && !it.done }
            "untimed" -> l.filter { it.time == null && !it.done }
            "done" -> l.filter { it.done }; else -> l }
        if (tp != "all") l = l.filter { it.placeId == tp }
        return when (ts) {
            "manual" -> l.sortedBy { it.sortOrder }; "time" -> l.sortedBy { tmin(it.time) ?: 9999 }
            "title" -> l.sortedBy { it.title.lowercase() }; "new" -> l.sortedByDescending { it.createdAt }; else -> l }
    }
    fun openSheet(kind: String, id: String? = null) {
        editId = id; sheet = kind
        draft = when (kind) {
            "task" -> id?.let { i -> tasks.value.find { it.id == i } }
                ?: Task(uid(), "", "note", null, null, null, "none", true, false, null, nowMs(), tasks.value.size)
            "habit" -> id?.let { i -> habits.value.find { it.id == i } }
                ?: Habit(uid(), "", "✨", PALETTE[habits.value.size % 8], "tick", 1, "", "0|1|2|3|4|5|6", null, true, habits.value.size)
            "moment" -> id?.let { i -> moments.value.find { it.id == i } }
                ?: Moment(uid(), null, "place", createName, "📍", PALETTE[moments.value.size % 8], "", 0, null, moments.value.size)
            "med" -> id?.let { i -> meds.value.find { it.id == i } }
                ?: Medicine(uid(), "", "", "09:00", 10, true, true, nowMs())
            "view" -> ""
            else -> null
        }
        if (kind == "moment" && id == null && createItems.isNotEmpty()) {
            val mid = (draft as Moment).id; val list = createItems; createItems = emptyList()
            run { list.forEachIndexed { i, s -> Repo.dao.putMItem(MomentItem(uid(), mid, s, i)) }; Repo.touch() }
        }
    }
    /** discard=true (cancel/dismiss) removes checklist items already written for a never-saved new task/moment. */
    fun closeSheet(discard: Boolean = true) {
        val d = draft
        if (discard && editId == null) when (d) {
            is Task -> run { Repo.dao.delItemsOf(d.id); Repo.touch() }
            is Moment -> run { Repo.dao.delMItemsOf(d.id); Repo.touch() }
        }
        sheet = null; draft = null; editId = null; confirmFn = null
    }
    fun saveDraft() = run {
        when (sheet) {
            "task" -> { val d = draft as Task
                if (d.title.isBlank()) { say("Title required"); return@run }
                Repo.dao.putTask(d); say("📝 Task saved") }
            "habit" -> { val d = draft as Habit
                if (d.name.isBlank()) { say("Name required"); return@run }
                Repo.dao.putHabit(if (d.type == "tick") d.copy(goal = 1) else d.copy(goal = d.goal.coerceAtLeast(1))); say("Habit saved") }
            "moment" -> { val d = draft as Moment
                if (d.name.isBlank()) { say("Name required"); return@run }
                Repo.dao.putMoment(d); say("Moment saved") }
            "med" -> { val d = draft as Medicine
                if (d.name.isBlank()) { say("Name required"); return@run }
                val raw = uncsv(d.times); val times = raw.mapNotNull { normTime(it) }.distinct().sorted()
                if (times.isEmpty() || times.size != raw.size) { say("Times must look like 09:00|21:00"); return@run }
                Repo.dao.putMed(d.copy(times = csv(times))); say("💊 Medicine saved") }
            "view" -> { val n = (draft as? String).orEmpty().ifBlank { "My view" }
                Repo.dao.putView(SavedView(uid(), n, tf, tp, tq, ts)); say("👁 View saved") }
        }
        Repo.touch(); closeSheet(discard = false)
    }
    fun deleteDraft() = run {
        when (sheet) {
            "task" -> { val d = draft as Task; Repo.dao.delTask(d.id); Repo.dao.delItemsOf(d.id); say("Task deleted") }
            "habit" -> { Repo.dao.delHabit((draft as Habit).id); say("Habit deleted") }
            "moment" -> { val d = draft as Moment; Repo.dao.delMoment(d.id); Repo.dao.delMItemsOf(d.id); say("Moment deleted") }
            "med" -> { Repo.dao.delMed((draft as Medicine).id); say("Medicine deleted") }
        }
        Repo.touch(); closeSheet(discard = false)
    }
    fun execCmd() = run {
        val raw = cmd; if (raw.isBlank()) return@run
        when (val r = Commands.parse(raw)) {
            is Cmd.Water -> { Repo.addWater(1); say("💧 Water logged") }
            is Cmd.Start -> { Repo.startMoment(r.id); runner = true }
            is Cmd.Create -> { createName = r.name; createItems = r.items; openSheet("moment") }
            is Cmd.Added -> say("📝 ${r.n} items → ${r.name}")
            is Cmd.Unknown -> { unknownCmd = r.raw; sheet = "unknown" }
        }
        cmd = ""; sugs = emptyList()
    }
    fun refreshSugs() = run { sugs = Commands.suggestions(cmd) }
    fun startMoment(id: String) = run { Repo.startMoment(id); runner = true; runConfirm = false; planner = listOf("", "", "") }
    fun finish() = run { Repo.finishMoment(); runner = false; runConfirm = false; say("Moment complete ✨") }
    fun bulkDone() = run {
        selected.forEach { id -> tasks.value.find { it.id == id }?.let { Repo.dao.putTask(it.copy(done = true, doneAt = nowMs())) } }
        selected = emptySet(); selectMode = false; Repo.touch(); say("Tasks completed")
    }
    fun bulkDel() = run {
        selected.forEach { Repo.dao.delTask(it); Repo.dao.delItemsOf(it) }
        selected = emptySet(); selectMode = false; Repo.touch(); say("Tasks deleted")
    }
    /** Moves past the adjacent *visible* task, renumbering the full list so hidden tasks keep their relative order. */
    fun moveTask(id: String, dir: Int) = run {
        val vis = filteredTasks(); val i = vis.indexOfFirst { it.id == id }; val j = i + dir
        if (i < 0 || j !in vis.indices) return@run
        val all = tasks.value.sortedWith(compareBy({ it.sortOrder }, { it.createdAt })).toMutableList()
        val moving = all.first { it.id == id }; all.remove(moving)
        val at = all.indexOfFirst { it.id == vis[j].id }
        all.add(if (dir > 0) at + 1 else at, moving)
        all.forEachIndexed { k, t -> if (t.sortOrder != k) Repo.dao.putTask(t.copy(sortOrder = k)) }; Repo.touch()
    }
}
