package com.dayflow.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.dayflow.app.ui.icons.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dayflow.app.*
import com.dayflow.app.data.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable fun SkyHero(vm: Vm) {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val phase = when { h in 5..7 -> "dawn"; h in 8..16 -> "day"; h in 17..20 -> "dusk"; else -> "night" }
    val colors = when (phase) {
        "dawn" -> listOf(col("#2B2A55"), col("#7A4D8F"), col("#FF8A5C"), col("#FFB347"))
        "day" -> listOf(col("#1B3F7A"), col("#2F6FB8"), col("#39D0D8"))
        "dusk" -> listOf(col("#241B4D"), col("#6B3D8F"), col("#FF5D73"), col("#FFB347"))
        else -> listOf(col("#070A1A"), col("#141A3F"), col("#2B2A6B"))
    }
    var stats by remember { mutableStateOf(Triple(0, 0, 0)) }
    var next by remember { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(vm.logs.value, vm.tasks.value, vm.habits.value) { stats = Repo.todayStats(); next = Repo.nextReminder() }
    val greet = when { h < 5 -> "Night owl mode"; h < 12 -> "Good morning"; h < 17 -> "Good afternoon"; h < 21 -> "Good evening"; else -> "Good night" }
    Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(24.dp))
        .background(Brush.linearGradient(colors)).border(1.dp, C.line2, RoundedCornerShape(24.dp))) {
        Canvas(Modifier.fillMaxSize()) {
            if (phase == "night") repeat(16) { i ->
                drawCircle(Color.White.copy(alpha = .5f), 1.6.dp.toPx(),
                    Offset(((i*37+13) % 96)/100f * size.width, ((i*23+7) % 55)/100f * size.height)) }
            drawCircle(if (phase == "night") col("#CDD3F0") else col("#FFB347"), 26.dp.toPx(), Offset(size.width*.78f, size.height*.26f))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, col("#0A0C1A").copy(alpha = .55f))))
        }
        Row(Modifier.fillMaxSize().padding(18.dp)) {
            Column(Modifier.weight(1f)) {
                Text("$greet${if (vm.prefs.value.name.isNotBlank()) ", " + vm.prefs.value.name else ""} ${if (phase=="night") "🌙" else "☀️"}",
                    color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text(SimpleDateFormat("EEEE, MMMM d", Locale.US).format(java.util.Date()),
                    color = Color.White.copy(alpha = .9f), fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                HeroChip(next?.let { "Next: ${it.first} · ${it.second}" } ?: "All clear ✨")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { HeroChip("${stats.second} tasks"); HeroChip("${stats.third} habits left") }
            }
            Ring(stats.first)
        }
    }
}
@Composable private fun HeroChip(t: String) {
    Box(Modifier.clip(CircleShape).background(col("#0A0C1A").copy(alpha = .5f))
        .border(1.dp, Color.White.copy(alpha = .22f), CircleShape).padding(horizontal = 10.dp, vertical = 5.dp)) {
        Text(t, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable fun TodayScreen(vm: Vm, onMic: () -> Unit) {
    val p = vm.prefs.value; val t = today(); val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SkyHero(vm) }
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(vm.cmd, { vm.cmd = it; vm.refreshSugs() },
                        placeholder = { Text("checklist office: ID, laptop, keys", color = C.dim, fontSize = 12.sp) },
                        modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(14.dp), colors = tfColors())
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (vm.listening) LocalAcc.current.first else C.surf)
                        .border(1.dp, C.line2, RoundedCornerShape(14.dp)).clickable(onClick = onMic), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Mic, null, tint = if (vm.listening) col("#1A1206") else C.mut) }
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                        .background(Brush.horizontalGradient(listOf(LocalAcc.current.first, LocalAcc.current.second)))
                        .clickable { vm.execCmd() }, contentAlignment = Alignment.Center) { Icon(Icons.Default.Send, null, tint = col("#1A1206")) }
                }
                if (vm.sugs.isNotEmpty()) Column(Modifier.padding(top = 6.dp).clip(RoundedCornerShape(14.dp)).background(C.surf2).border(1.dp, C.line2, RoundedCornerShape(14.dp))) {
                    vm.sugs.forEach { m -> Row(Modifier.fillMaxWidth().clickable { vm.startMoment(m.id); vm.cmd = ""; vm.sugs = emptyList() }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(m.icon, fontSize = 18.sp); Spacer(Modifier.width(10.dp)); Text(m.name, color = C.ink, fontSize = 13.sp) } }
                }
            }
        }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.moments.value.filter { it.type == "routine" }) { m ->
                Row(Modifier.clip(RoundedCornerShape(14.dp)).background(C.surf).border(1.dp, colA(m.color, .5f), RoundedCornerShape(14.dp))
                    .clickable { vm.startMoment(m.id) }.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(m.icon, fontSize = 17.sp); Spacer(Modifier.width(7.dp)); Text(m.name, color = C.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold) } } } }
        val a = vm.active.value
        val am = vm.moment(a?.momentId)
        if (a != null && am != null) item {
            val ck = uncsv(a.checked).size; val total = vm.mitemsOf(am.id).size
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (p.focusMode) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colA("#8B7BFF", .18f)).border(1.dp, colA("#8B7BFF", .5f), RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🎯 Focus: ${am.name} · $ck/$total", color = C.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Btn("Resume", true, true) { vm.runner = true } }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colA("#FFB347", .14f)).border(1.dp, colA("#FFB347", .4f), RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${am.icon} ${am.name} · $ck/$total", color = C.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Btn("Resume", true, true) { vm.runner = true } }
            }
        }
        item { WaterCard(vm) }
        item { SleepCard(vm) }
        item { MedCard(vm) }
        item { Sec("Today's habits", null, "+ New habit") { vm.openSheet("habit") } }
        val todayHabits = vm.habits.value.filter { Repo.scheduled(it, Calendar.getInstance()) && it.id != "water" }
        if (todayHabits.isEmpty()) item { Empty("🌱", "No habits for today", "Tap “+ New habit” to start one.") }
        items(todayHabits) { h -> HabitRow(vm, h) }
        item { Sec("Today's tasks", null, "View all →") { vm.tab = "tasks" } }
        val list = vm.tasks.value.filter { !it.done && (it.date == t || it.date == null) }.sortedBy { tmin(it.time) ?: 9999 }
        if (list.isEmpty()) item { Empty("🎉", "Nothing pending today", "Add a note or checklist in Tasks.") }
        items(list.take(6)) { x -> TaskRow(vm, x, false) }
        item { Spacer(Modifier.height(90.dp)) }
    }
}
@Composable fun WaterCard(vm: Vm) {
    val h = vm.habits.value.find { it.id == "water" } ?: return
    val c = vm.logVal(today(), "water"); val scope = rememberCoroutineScope()
    var next by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(c, vm.prefs.value) { next = Repo.nextWater() }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
        .background(Brush.linearGradient(listOf(col("#12324A"), col("#1B2C5E"))))
        .border(1.dp, colA("#39D0D8", .35f), RoundedCornerShape(20.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💧", fontSize = 26.sp); Spacer(Modifier.width(10.dp))
            Text("Water", color = C.ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.weight(1f))
            Text("$c", color = C.aqua, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text(" / ${h.goal}", color = C.mut, fontSize = 13.sp) }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(h.goal) { i -> Box(Modifier.width(28.dp).height(36.dp).clip(RoundedCornerShape(6.dp))
                .background(if (i < c) Brush.verticalGradient(listOf(col("#6EE7EF"), col("#2AB5C9"))) else SolidColor(colA("#39D0D8", .06f)))
                .border(2.dp, if (i < c) C.aqua else colA("#39D0D8", .45f), RoundedCornerShape(6.dp))
                .clickable { scope.launch { Repo.setWater(if (i + 1 == c) i else i + 1) } }) } }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Btn("+ Glass", true, true) { scope.launch { Repo.addWater(1) } }
            Spacer(Modifier.width(8.dp)); Btn("−", false, true) { scope.launch { Repo.addWater(-1) } }
            Spacer(Modifier.weight(1f))
            Text(if (c >= h.goal) "🏆 Goal reached!" else next?.let { "next ${fmtMin(it)}" } ?: "done today",
                color = if (c >= h.goal) C.grn else C.mut, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
    }
}
@Composable fun SleepCard(vm: Vm) {
    val p = vm.prefs.value; val last = vm.sleeps.value.firstOrNull()
    val recent = vm.sleeps.value.take(7)
    val avg = if (recent.isEmpty()) 0 else recent.sumOf { it.minutes } / recent.size
    DCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("😴", fontSize = 24.sp); Spacer(Modifier.width(10.dp))
            Text("Sleep", color = C.ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.weight(1f))
            Text(if (p.sleeping) "ON" else last?.let { fmtDur(it.minutes) } ?: "—", color = C.vio, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold) }
        Text(if (p.sleeping) "Quiet hours active. Alerts resume when you say “I woke up”."
            else "Last sleep ${last?.let { fmtDur(it.minutes) } ?: "not tracked"} · 7-day avg ${fmtDur(avg)}",
            color = C.mut, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
    }
}
@Composable fun MedCard(vm: Vm) {
    if (vm.meds.value.none { it.enabled }) return
    var due by remember { mutableStateOf<List<Pair<Medicine, String>>>(emptyList()) }
    val tick by Repo.changes.collectAsState()       // snoozes / taken doses
    var minute by remember { mutableStateOf(nowMin()) }  // doses become due as time passes
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(30_000); minute = nowMin() } }
    LaunchedEffect(vm.meds.value, vm.medLogs.value, tick, minute) { due = Repo.medDue() }
    DCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("💊", fontSize = 24.sp); Spacer(Modifier.width(10.dp))
            Text("Medicine", color = C.ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.weight(1f))
            Text("${due.size}", color = C.red, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold) }
        if (due.isEmpty()) Text("No medicine due right now.", color = C.mut, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        due.forEach { (m, tm) -> Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(m.name, color = C.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("${m.dose} · ${fmtT(tm)}", color = C.mut, fontSize = 11.sp) }
            Btn("Snooze", false, true) { vm.run { Repo.snooze(Repo.medNotifKey(m.id, today(), tm), m.snoozeMin) }; vm.say("💤 Snoozed") }
            Spacer(Modifier.width(6.dp))
            Btn("Taken", true, true) { vm.run { Repo.markTaken(m.id, tm) }; vm.say("💊 Taken") } } }
    }
}
@Composable fun HabitRow(vm: Vm, h: Habit) {
    val done = vm.doneHabit(h, today()); val v = vm.logVal(today(), h.id); val s = vm.streak(h); val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Brush.verticalGradient(listOf(C.surf, C.bg2)))
        .border(1.dp, C.line, RoundedCornerShape(16.dp)).clickable { vm.openSheet("habit", h.id) }.padding(13.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(colA(h.color, .14f))
            .border(1.dp, colA(h.color, .45f), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) { Text(h.icon, fontSize = 21.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(h.name, color = C.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (h.time != null) Tag("⏰ ${fmtT(h.time)}", C.blu); if (s > 0) Tag("🔥 $s", LocalAcc.current.first) } }
        if (h.type == "tick") Ck(done) { scope.launch { Repo.tickHabit(h.id) } }
        else Stepper("$v/${h.goal}", col(h.color), { scope.launch { Repo.bumpHabit(h.id, -1) } }, { scope.launch { Repo.bumpHabit(h.id, 1) } })
    }
}
@Composable fun TaskRow(vm: Vm, x: Task, full: Boolean) {
    val t = today(); val over = x.date != null && x.date < t && !x.done; val p = vm.moment(x.placeId)
    val its = vm.itemsOf(x.id); val sel = vm.selected.contains(x.id); val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
        .background(Brush.verticalGradient(listOf(if (sel) colA("#FFB347", .08f) else C.surf, C.bg2)))
        .border(1.dp, if (sel) LocalAcc.current.first else C.line, RoundedCornerShape(16.dp)).padding(13.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (full && vm.selectMode) Ck(sel) { vm.selected = if (sel) vm.selected - x.id else vm.selected + x.id }
        else Ck(x.done) { scope.launch { Repo.toggleTask(x.id) } }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f).clickable { vm.openSheet("task", x.id) }) {
            Text(x.title, color = if (x.done) C.dim else C.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textDecoration = if (x.done) TextDecoration.LineThrough else null)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.padding(top = 4.dp)) {
                if (x.kind == "checklist") Tag("📋 ${its.count { it.done }}/${its.size}") else Tag("📝 note")
                if (x.time != null) Tag("⏰ ${fmtT(x.time)}", C.blu)
                if (p != null) Tag("${p.icon} ${p.name}", C.mag)
                if (x.repeat != "none") Tag("🔁 ${x.repeat}", C.vio)
                if (over) Tag("⚠ overdue", C.red) } }
        if (full && vm.ts == "manual" && !vm.selectMode) Column {
            Icon(Icons.Default.KeyboardArrowUp, null, tint = C.dim, modifier = Modifier.size(20.dp).clickable { vm.moveTask(x.id, -1) })
            Icon(Icons.Default.KeyboardArrowDown, null, tint = C.dim, modifier = Modifier.size(20.dp).clickable { vm.moveTask(x.id, 1) }) }
        if (full && !vm.selectMode) IconButton(onClick = { vm.run { Repo.dao.delTask(x.id); Repo.dao.delItemsOf(x.id); Repo.touch() }; vm.say("Task deleted") }) {
            Icon(Icons.Default.Delete, null, tint = C.red) }
    }
}
@Composable fun TasksScreen(vm: Vm) {
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Sec("Notes & Checklists", "${vm.filteredTasks().size}", if (vm.selectMode) "Cancel" else "Select") {
            vm.selectMode = !vm.selectMode; vm.selected = emptySet() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(vm.tq, { vm.tq = it }, placeholder = { Text("Search tasks & items…", color = C.dim) },
                modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(13.dp), colors = tfColors(),
                leadingIcon = { Icon(Icons.Default.Search, null, tint = C.dim) })
            Spacer(Modifier.width(8.dp))
            var open by remember { mutableStateOf(false) }
            Box { Btn(vm.ts, false, true) { open = true }
                DropdownMenu(open, { open = false }, containerColor = C.surf2) {
                    listOf("manual","time","title","new").forEach { DropdownMenuItem({ Text(it, color = C.ink) }, { vm.ts = it; open = false }) } } }
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(listOf("all" to "Open","today" to "Today","timed" to "Timed","untimed" to "Anytime","done" to "Done")) { (k, l) ->
                Box(Modifier.clip(CircleShape)
                    .background(if (vm.tf == k) Brush.horizontalGradient(listOf(LocalAcc.current.first, LocalAcc.current.second)) else SolidColor(C.surf))
                    .border(1.dp, if (vm.tf == k) Color.Transparent else C.line, CircleShape)
                    .clickable { vm.tf = k }.padding(horizontal = 14.dp, vertical = 7.dp)) {
                    Text(l, color = if (vm.tf == k) col("#1A1206") else C.mut, fontSize = 12.sp, fontWeight = FontWeight.Bold) } }
            item { var open by remember { mutableStateOf(false) }
                Box { Btn(if (vm.tp == "all") "📍 All" else vm.moment(vm.tp)?.name ?: "📍", false, true) { open = true }
                    DropdownMenu(open, { open = false }, containerColor = C.surf2) {
                        DropdownMenuItem({ Text("All places", color = C.ink) }, { vm.tp = "all"; open = false })
                        vm.moments.value.forEach { m -> DropdownMenuItem({ Text("${m.icon} ${m.name}", color = C.ink) }, { vm.tp = m.id; open = false }) } } } }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(vm.views.value) { v -> Row(Modifier.clip(CircleShape).border(1.dp, C.line2, CircleShape)
                .clickable { vm.tf = v.filter; vm.tp = v.place; vm.tq = v.q; vm.ts = v.sort }
                .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(v.name, color = C.vio, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Icon(Icons.Default.Close, null, tint = C.dim, modifier = Modifier.size(16.dp).clickable { vm.run { Repo.dao.delView(v.id); Repo.touch() } }) } }
            item { Btn("+ Save view", false, true) { vm.openSheet("view") } }
        }
        if (vm.selectMode) Row(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(RoundedCornerShape(14.dp))
            .background(C.surf2).border(1.dp, LocalAcc.current.first, RoundedCornerShape(14.dp)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("${vm.selected.size} selected", color = C.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Btn("Done", false, true) { vm.bulkDone() }; Spacer(Modifier.width(6.dp)); Btn("Delete", false, true) { vm.bulkDel() } }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            val l = vm.filteredTasks()
            if (l.isEmpty()) item { Empty("📝", "Nothing here", "Try a different filter or add a task.") }
            items(l) { x -> TaskRow(vm, x, true) }
            item { Spacer(Modifier.height(90.dp)) } }
    }
}
@Composable fun MomentsScreen(vm: Vm) {
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        Sec("Moments & Places")
        DCard { Text("💡 Tap ▶ or type/speak \"going to market\", \"I woke up\", \"taken water\". Voice fills the box for review before sending.", color = C.dim, fontSize = 12.sp) }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(vm.mq, { vm.mq = it }, placeholder = { Text("Search moments…", color = C.dim) },
            modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(13.dp), colors = tfColors(),
            leadingIcon = { Icon(Icons.Default.Search, null, tint = C.dim) })
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val f = vm.moments.value.filter { vm.mq.isBlank() || it.name.contains(vm.mq, true) || uncsv(it.aliases).any { a -> a.contains(vm.mq, true) } }
            item { Sec("Daily routines") }
            items(f.filter { it.type == "routine" }) { m -> MomentCard(vm, m) }
            item { Sec("Places") }
            val places = f.filter { it.type == "place" }
            if (places.isEmpty()) item { Empty("📍", if (vm.mq.isBlank()) "No places yet" else "No matching places",
                if (vm.mq.isBlank()) "Tap + to add a place, or type “going to <place>” on Today." else "Try a different search.") }
            items(places) { m -> MomentCard(vm, m) }
            item { Spacer(Modifier.height(90.dp)) } }
    }
}
@Composable fun MomentCard(vm: Vm, m: Moment) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brush.verticalGradient(listOf(C.surf, C.bg2)))
        .border(1.dp, C.line, RoundedCornerShape(18.dp)).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(50.dp).clip(RoundedCornerShape(15.dp)).background(colA(m.color, .14f))
            .border(1.dp, colA(m.color, .45f), RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) { Text(m.icon, fontSize = 25.sp) }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) { Text(m.name, color = C.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Text("${vm.mitemsOf(m.id).size} items · ${m.runs} runs", color = C.mut, fontSize = 11.sp) }
        IconButton(onClick = { vm.openSheet("moment", m.id) }) { Icon(Icons.Default.Edit, null, tint = C.mut) }
        Box(Modifier.size(42.dp).clip(CircleShape).background(col(m.color)).clickable { vm.startMoment(m.id) },
            contentAlignment = Alignment.Center) { Icon(Icons.Default.PlayArrow, null, tint = col("#101223")) }
    }
}
@Composable fun StatsScreen(vm: Vm) {
    val days = weekDays(vm.weekOffset); val ds = days.map { dstr(it) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Sec("Your progress", null, "Weekly review") { vm.run { vm.review = Repo.generateReview(); vm.sheet = "review" } } }
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.weekOffset-- }) { Icon(Icons.Default.ChevronLeft, null, tint = C.mut) }
            Text("${SimpleDateFormat("MMM d", Locale.US).format(days.first().time)} – ${SimpleDateFormat("MMM d", Locale.US).format(days.last().time)}",
                color = C.ink, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            IconButton(onClick = { if (vm.weekOffset < 0) vm.weekOffset++ }, enabled = vm.weekOffset < 0) { Icon(Icons.Default.ChevronRight, null, tint = C.mut) } } }
        vm.reviews.value.firstOrNull()?.let { r -> item { DCard {
            Row(verticalAlignment = Alignment.CenterVertically) { Text("📊", fontSize = 22.sp); Spacer(Modifier.width(10.dp))
                Text("Weekly review", color = C.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.weight(1f))
                Btn("Open", false, true) { vm.review = r; vm.sheet = "review" } }
            Text(r.headline, color = C.mut, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) } } }
        item {
            val best = vm.habits.value.maxOfOrNull { vm.streak(it) } ?: 0
            val wavg = ds.sumOf { vm.logVal(it, "water") } / 7.0
            val slp = vm.sleeps.value.filter { ds.contains(it.date) }
            val sa = if (slp.isEmpty()) 0 else slp.sumOf { it.minutes } / slp.size
            val td = vm.tasks.value.count { it.doneAt != null && ds.contains(dstr(calOfMs(it.doneAt))) }
            Column { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("🗒️", "$td", "tasks done", C.grn, Modifier.weight(1f))
                    StatTile("🔥", "${best}d", "best streak", LocalAcc.current.first, Modifier.weight(1f)) }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("💧", String.format(Locale.US, "%.1f", wavg), "glasses/day", C.aqua, Modifier.weight(1f))
                    StatTile("😴", fmtDur(sa), "avg sleep", C.vio, Modifier.weight(1f)) } }
        }
        item { DCard { Text("Daily completion", color = C.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(10.dp))
            // same rule as the Today ring: count habits earn partial credit (3 of 8 glasses = 37.5%)
            val vals = days.map { c -> val d = dstr(c); val hs = vm.habits.value.filter { Repo.scheduled(it, c) }
                val goal = hs.sumOf { if (it.type == "count") it.goal else 1 }
                val done = hs.sumOf { minOf(if (it.type == "count") it.goal else 1, vm.logVal(d, it.id)) }
                if (goal == 0) 0f else done * 100f / goal }
            BarChart(vals, 100f, PALETTE.map { col(it) })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                days.forEach { Text(DOWFULL[dow(it)], color = C.dim, fontSize = 10.sp) } } } }
        item { DCard { Text("Habits · last 7 days", color = C.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            vm.habits.value.forEach { h -> Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${h.icon} ${h.name}", color = C.ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Tag("🔥 ${vm.streak(h)}", LocalAcc.current.first); Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { days.forEach { c ->
                    val d = dstr(c); val v = vm.logVal(d, h.id); val sch = Repo.scheduled(h, c); val dn = vm.doneHabit(h, d)
                    Box(Modifier.size(15.dp).clip(RoundedCornerShape(5.dp))
                        .background(if (!sch) colA(h.color, .12f) else if (dn) col(h.color) else if (v > 0) colA(h.color, .5f) else C.surf2)
                        .border(1.dp, if (sch) col(h.color) else C.line, RoundedCornerShape(5.dp))) } } } } } }
        item { DCard { Text("💧 Water", color = C.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(10.dp))
            val goal = (vm.habits.value.find { it.id == "water" }?.goal ?: 8).toFloat()
            BarChart(ds.map { vm.logVal(it, "water").toFloat() }, goal, listOf(C.aqua), goal) } }
        item { DCard { Text("😴 Sleep", color = C.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(10.dp))
            BarChart(ds.map { d -> vm.sleeps.value.filter { it.date == d }.maxOfOrNull { it.minutes }?.toFloat() ?: 0f }, 600f, listOf(C.vio)) } }
        item { DCard { Text("Moments used", color = C.ink, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            vm.moments.value.forEach { m -> Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${m.icon} ${m.name}", color = C.ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Tag("${vm.runs.value.count { it.momentId == m.id && ds.contains(it.date) }}× this week") } } } }
        item { Spacer(Modifier.height(90.dp)) }
    }
}
@Composable fun StatTile(icon: String, v: String, l: String, c: Color, mod: Modifier = Modifier) {
    Column(mod.clip(RoundedCornerShape(16.dp)).background(Brush.verticalGradient(listOf(C.surf, C.bg2)))
        .border(1.dp, C.line, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Text("$icon $v", color = c, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Text(l, color = C.mut, fontSize = 11.sp) }
}
@Composable fun MoreScreen(vm: Vm) {
    val p = vm.prefs.value; val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Sec("Settings") }
        item { DCard {
            SetRow("Your name", "Used in greeting") { val (name, setName) = rememberSynced("name", p.name) { Repo.prefs.name(it) }
                OutlinedTextField(name, setName,
                modifier = Modifier.width(140.dp), singleLine = true, colors = tfColors()) }
            SetRow("Wake-up time") { TimeBtn(p.wake) { scope.launch { Repo.prefs.wake(it) } } }
            SetRow("Sleep time") { TimeBtn(p.sleep) { scope.launch { Repo.prefs.sleepTime(it) } } } } }
        item { Sec("🔔 Notifications") }
        item { DCard {
            SetRow("Real notifications", "Battery-friendly inexact; Android may batch.") { Toggle(p.notifications) { scope.launch { Repo.prefs.notifications(!p.notifications) } } }
            SetRow("Auto quiet hours", "Mutes while sleeping; resumes on “I woke up”.") { Tag("ON", C.grn) }
            SetRow("Manual quiet window") { Toggle(p.quietEnabled) { scope.launch { Repo.prefs.quietEnabled(!p.quietEnabled) } } }
            if (p.quietEnabled) SetRow("Quiet from / to") { Row {
                TimeBtn(p.quietStart) { scope.launch { Repo.prefs.quietStart(it) } }; Spacer(Modifier.width(6.dp))
                TimeBtn(p.quietEnd) { scope.launch { Repo.prefs.quietEnd(it) } } } }
            Btn("Send test", true, true, Modifier.padding(top = 12.dp)) {
                com.dayflow.app.notify.Notifier.post(vm.getApplication(), "test", "🔔 Dayflow test", "Notifications are working!", "water", "water") } } }
        item { Sec("🎙️ Voice & focus") }
        item { DCard {
            SetRow("Voice input", "Inline recognizer + system dialog fallback.") { Tag("ready", C.grn) }
            SetRow("Focus mode", "Highlights active moment; navigation stays visible.") { Toggle(p.focusMode) { scope.launch { Repo.prefs.focus(!p.focusMode) } } } } }
        item { Sec("💧 Water") }
        item { DCard {
            SetRow("Daily goal") { Stepper("${p.waterGoal}", C.aqua,
                { scope.launch { val g = (p.waterGoal-1).coerceAtLeast(1); Repo.prefs.waterGoal(g); Repo.water()?.let { Repo.dao.putHabit(it.copy(goal = g)) }; Repo.touch() } },
                { scope.launch { val g = (p.waterGoal+1).coerceAtMost(16); Repo.prefs.waterGoal(g); Repo.water()?.let { Repo.dao.putHabit(it.copy(goal = g)) }; Repo.touch() } }) }
            SetRow("Remind every") { var open by remember { mutableStateOf(false) }
                Box { Btn("${p.waterInterval} min", false, true) { open = true }
                    DropdownMenu(open, { open = false }, containerColor = C.surf2) {
                        listOf(60,90,120,180).forEach { m -> DropdownMenuItem({ Text("$m min", color = C.ink) }, { scope.launch { Repo.prefs.waterInterval(m) }; open = false }) } } } }
            SetRow("Active hours") { Row { TimeBtn(p.waterStart) { scope.launch { Repo.prefs.waterStart(it) } }
                Spacer(Modifier.width(6.dp)); TimeBtn(p.waterEnd) { scope.launch { Repo.prefs.waterEnd(it) } } } } } }
        item { Sec("📊 Sunday review") }
        item { DCard {
            SetRow("Automatic Sunday review") { Toggle(p.reviewEnabled) { scope.launch { Repo.prefs.reviewEnabled(!p.reviewEnabled) } } }
            SetRow("Review time") { TimeBtn(p.reviewTime) { scope.launch { Repo.prefs.reviewTime(it) } } }
            Btn("Open weekly review", false, true, Modifier.fillMaxWidth().padding(top = 10.dp)) {
                vm.run { vm.review = Repo.generateReview(); vm.sheet = "review" } } } }
        item { Sec("💊 Medicine", "${vm.meds.value.size}", "+ Add") { vm.openSheet("med") } }
        item { DCard { if (vm.meds.value.isEmpty()) Text("No medicines yet. Add one for anyone who needs it.", color = C.dim, fontSize = 12.sp)
            vm.meds.value.forEach { m -> SetRow(m.name, "${m.dose} · ${uncsv(m.times).joinToString(", ") { fmtT(it) }} · snooze ${m.snoozeMin}m") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Toggle(m.enabled) { scope.launch { Repo.dao.putMed(m.copy(enabled = !m.enabled)); Repo.touch() } }
                    IconButton(onClick = { vm.openSheet("med", m.id) }) { Icon(Icons.Default.Edit, null, tint = C.mut) } } } } } }
        item { Sec("🎨 Sound & look") }
        item { DCard {
            SetRow("Sounds & haptics") { Toggle(p.sound) { scope.launch { Repo.prefs.sound(!p.sound) } } }
            SetRow("Accent") { Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                listOf("dawn" to "#FFB347","aqua" to "#39D0D8","violet" to "#8B7BFF","green" to "#4ADE80").forEach { (k, hex) ->
                    Box(Modifier.size(34.dp).clip(CircleShape).background(col(hex))
                        .border(if (p.accent == k) 2.dp else 0.dp, C.ink, CircleShape)
                        .clickable { scope.launch { Repo.prefs.accent(k) } }) } } } } }
        item { Sec("🩺 Health check") }
        item { DCard { Btn("Run checks", true, true) {
                vm.say("✓ ${vm.habits.value.size} habits · ${vm.tasks.value.size} tasks · ${vm.moments.value.size} moments · 4 widgets") }
            Spacer(Modifier.height(8.dp))
            Btn("Repair data", false, true) { vm.run { Repo.touch() }; vm.say("Data repaired") } } }
        item { Sec("🗄️ Data & privacy") }
        item { DCard { SetRow("Stored only on this device", "Room + DataStore. No account, no cloud.") { Tag("🔒 private", C.grn) }
            Btn("Reset app", false, true, Modifier.padding(top = 12.dp)) {
                vm.confirmFn = { vm.run { Repo.resetAll() }; vm.say("App reset") }; vm.sheet = "confirm" } } }
        item { Text("Dayflow v1.0.3 · native · private · offline", color = C.dim, fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth().padding(16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        item { Spacer(Modifier.height(90.dp)) }
    }
}
