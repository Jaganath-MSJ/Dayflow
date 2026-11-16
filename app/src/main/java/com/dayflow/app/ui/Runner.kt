package com.dayflow.app.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dayflow.app.*
import com.dayflow.app.data.*
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable fun Runner(vm: Vm) {
    val a = vm.active.value ?: return; val m = vm.moment(a.momentId) ?: return
    val its = vm.mitemsOf(m.id); val ck = uncsv(a.checked); val done = ck.size
    val pct = if (its.isEmpty()) 100 else done * 100 / its.size
    var forgot by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    LaunchedEffect(m.id) { forgot = Repo.forgotten(m.id) }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(C.bg)) {
        LazyColumn(Modifier.fillMaxSize()) {
            item { Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(col(m.color), col("#10132A")))).padding(20.dp)) {
                Column { Text(m.icon, fontSize = 40.sp)
                    Text(m.name, color = C.ink, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text(if (m.type == "place") "place checklist" else "daily routine", color = C.ink.copy(alpha = .85f), fontSize = 12.sp) }
                IconButton(onClick = { vm.runner = false }, modifier = Modifier.align(Alignment.TopEnd)) { Icon(Icons.Default.Close, null, tint = C.ink) } } }
            if (forgot.isNotEmpty() && (m.mkey == "office" || m.type == "place")) item {
                Column(Modifier.padding(16.dp).clip(RoundedCornerShape(15.dp)).background(colA("#FFB347", .07f))
                    .border(1.dp, colA("#FFB347", .5f), RoundedCornerShape(15.dp)).padding(14.dp)) {
                    Text("❗ Did I forget?", color = LocalAcc.current.first, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                    forgot.forEach { (txt, n) -> Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text("• $txt", color = C.ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text("$n× missed", color = C.blu, fontSize = 11.sp) } } } }
            if (m.mkey == "wake") item { Agenda(vm) }
            if (m.mkey == "sleep") { item { Tomorrow(vm) }; item { Planner(vm) } }
            val pts = vm.tasks.value.filter { !it.done && it.placeId == m.id }
            if (pts.isNotEmpty()) item { Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)
                .clip(RoundedCornerShape(15.dp)).background(C.surf).border(1.dp, C.line2, RoundedCornerShape(15.dp)).padding(14.dp)) {
                Text("📍 Saved for ${m.name}", color = LocalAcc.current.first, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                pts.forEach { Text("• ${it.title}", color = C.ink, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) } } }
            item { Column(Modifier.padding(horizontal = 16.dp)) {
                Box(Modifier.fillMaxWidth().height(9.dp).clip(CircleShape).background(C.surf2)) {
                    Box(Modifier.fillMaxWidth(pct / 100f).height(9.dp).clip(CircleShape).background(col(m.color))) }
                Text("$done of ${its.size} done", color = C.mut, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)) } }
            items(its) { i -> val on = ck.contains(i.id)
                Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp).fillMaxWidth().clip(RoundedCornerShape(15.dp))
                    .background(if (on) colA(m.color, .08f) else C.surf)
                    .border(1.dp, if (on) colA(m.color, .55f) else C.line, RoundedCornerShape(15.dp))
                    .clickable { scope.launch { Repo.setChecked(i.id, !on) } }.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Ck(on) { scope.launch { Repo.setChecked(i.id, !on) } }
                    Spacer(Modifier.width(13.dp))
                    Text(i.text, color = if (on) C.mut else C.ink, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        textDecoration = if (on) TextDecoration.LineThrough else null) } }
            item { val (note, setNote) = rememberSynced(a.startedAt, a.note) { Repo.setNote(it) }
                OutlinedTextField(note, setNote,
                placeholder = { Text("Quick note (optional)…", color = C.dim) },
                modifier = Modifier.fillMaxWidth().padding(16.dp), minLines = 2, shape = RoundedCornerShape(13.dp), colors = tfColors()) }
            if (vm.runConfirm) item { Column(Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(13.dp))
                .background(colA("#FFB347", .1f)).border(1.dp, colA("#FFB347", .45f), RoundedCornerShape(13.dp)).padding(14.dp)) {
                Text("${its.size - done} items still unchecked. Complete anyway?", color = C.ink, fontSize = 13.sp)
                Row(Modifier.padding(top = 10.dp)) { Btn("Keep going", false, true) { vm.runConfirm = false }
                    Spacer(Modifier.width(8.dp)); Btn("Complete anyway", true, true) { vm.finish() } } } }
            item { Row(Modifier.padding(16.dp)) {
                Btn("Later", false, false, Modifier.weight(1f)) { vm.runner = false }
                Spacer(Modifier.width(10.dp))
                Btn("Complete ✓", true, false, Modifier.weight(1f)) { if (its.any { !ck.contains(it.id) }) vm.runConfirm = true else vm.finish() } }
                Spacer(Modifier.height(30.dp)) }
        }
    }
}
@Composable private fun Agenda(vm: Vm) {
    val t = today()
    val timed = vm.tasks.value.filter { !it.done && (it.date == t || it.date == null) && it.time != null }.sortedBy { tmin(it.time) }
    val hs = vm.habits.value.filter { Repo.scheduled(it, Calendar.getInstance()) && !vm.doneHabit(it, t) }
    Column(Modifier.padding(16.dp).clip(RoundedCornerShape(15.dp)).background(C.surf)
        .border(1.dp, C.line2, RoundedCornerShape(15.dp)).padding(14.dp)) {
        Text("☀️ Today's plan", color = LocalAcc.current.first, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        timed.forEach { Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text("⏰ ${it.title}", color = C.ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(fmtT(it.time), color = C.blu, fontSize = 11.sp) } }
        hs.forEach { Text("${it.icon} ${it.name}", color = C.ink, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) } }
}
@Composable private fun Tomorrow(vm: Vm) {
    val tm = dstr(addDays(1)); val dw = dow(addDays(1))
    val ts = vm.tasks.value.filter { !it.done && (it.date == tm || it.repeat == "daily" || (it.repeat == "weekdays" && dw in 1..5)) }
    val hs = vm.habits.value.filter { uncsv(it.days).contains(dw.toString()) }
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp).clip(RoundedCornerShape(15.dp))
        .background(C.surf).border(1.dp, C.line2, RoundedCornerShape(15.dp)).padding(14.dp)) {
        Text("🌙 Tomorrow preview", color = LocalAcc.current.first, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        ts.forEach { Text("🗒️ ${it.title}", color = C.ink, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
        hs.forEach { Text("${it.icon} ${it.name}", color = C.ink, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) } }
}
@Composable private fun Planner(vm: Vm) {
    Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp).clip(RoundedCornerShape(15.dp))
        .background(C.surf).border(1.dp, C.line2, RoundedCornerShape(15.dp)).padding(14.dp)) {
        Text("🗓️ Optional: plan tomorrow", color = LocalAcc.current.first, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
        repeat(3) { i -> OutlinedTextField(vm.planner[i], { v -> vm.planner = vm.planner.toMutableList().also { it[i] = v } },
            placeholder = { Text("Top priority ${i+1}", color = C.dim) },
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp), singleLine = true, shape = RoundedCornerShape(11.dp), colors = tfColors()) }
        Btn("Save optional plan", false, true, Modifier.fillMaxWidth().padding(top = 10.dp)) {
            vm.run { Repo.saveTomorrowPlan(vm.planner); vm.planner = listOf("", "", "") }; vm.say("🗓️ Tomorrow planned") } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Sheets(vm: Vm) {
    val kind = vm.sheet ?: return
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = { vm.closeSheet() }, sheetState = state,
        containerColor = C.surf2, contentColor = C.ink,
        dragHandle = { Box(Modifier.padding(10.dp).width(44.dp).height(5.dp).clip(CircleShape).background(C.line2)) }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 30.dp).verticalScroll(rememberScrollState())) {
            when (kind) {
                "task" -> TaskSheet(vm)
                "habit" -> HabitSheet(vm)
                "moment" -> MomentSheet(vm)
                "med" -> MedSheet(vm)
                "view" -> { Text("Save this view", color = C.ink, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    Field("Name", vm.draft as? String ?: "", { vm.draft = it }, "e.g. Evening errands")
                    Btn("Save", true, false, Modifier.fillMaxWidth()) { vm.saveDraft() } }
                "confirm" -> { Text("Are you sure?", color = C.ink, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    Text("This cannot be undone.", color = C.mut, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
                    Row { Btn("Cancel", false, false, Modifier.weight(1f)) { vm.closeSheet() }
                        Spacer(Modifier.width(10.dp))
                        Btn("Confirm", true, false, Modifier.weight(1f)) { vm.confirmFn?.invoke(); vm.closeSheet(discard = false) } } }
                "unknown" -> { Text("No moment found", color = C.ink, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Nothing matches “${vm.unknownCmd}”. Create it as a place?", color = C.mut, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp))
                    Row { Btn("Cancel", false, false, Modifier.weight(1f)) { vm.closeSheet() }
                        Spacer(Modifier.width(10.dp))
                        Btn("Create place", true, false, Modifier.weight(1f)) { vm.createName = titleCase(norm(vm.unknownCmd)); vm.openSheet("moment") } } }
                "review" -> vm.review?.let { r ->
                    Text("📊 Weekly review", color = C.ink, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(12.dp))
                    Row { StatTile("🎯", "${r.pct}%", "habits", LocalAcc.current.first, Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp)); StatTile("🗒️", "${r.tasksDone}", "tasks", C.grn, Modifier.weight(1f)) }
                    Spacer(Modifier.height(8.dp))
                    Row { StatTile("💧", r.waterAvg, "glasses/day", C.aqua, Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp)); StatTile("😴", fmtDur(r.sleepAvg), "avg sleep", C.vio, Modifier.weight(1f)) }
                    Text(r.headline, color = C.mut, fontSize = 13.sp, modifier = Modifier.padding(vertical = 14.dp))
                    Btn("Regenerate", true, false, Modifier.fillMaxWidth()) { vm.run { vm.review = Repo.generateReview() } } }
            }
        }
    }
}
@Composable private fun Head(title: String, vm: Vm) {
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = C.ink, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
        IconButton(onClick = { vm.closeSheet() }) { Icon(Icons.Default.Close, null, tint = C.mut) } }
}
@Composable private fun EmojiPick(sel: String, onPick: (String) -> Unit) {
    Text("ICON", color = C.mut, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(6.dp))
    Column { EMOJIS.chunked(8).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
            row.forEach { e -> Box(Modifier.size(38.dp).clip(RoundedCornerShape(10.dp))
                .background(if (sel == e) colA("#FFB347", .12f) else C.input)
                .border(1.dp, if (sel == e) LocalAcc.current.first else C.line, RoundedCornerShape(10.dp))
                .clickable { onPick(e) }, contentAlignment = Alignment.Center) { Text(e, fontSize = 18.sp) } } } } }
}
@Composable private fun ColorPick(sel: String, onPick: (String) -> Unit) {
    Text("COLOR", color = C.mut, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { PALETTE.forEach { c ->
        Box(Modifier.size(30.dp).clip(CircleShape).background(col(c))
            .border(if (sel == c) 2.dp else 0.dp, C.ink, CircleShape).clickable { onPick(c) }) } }
    Spacer(Modifier.height(12.dp))
}
@Composable private fun TaskSheet(vm: Vm) {
    val d = vm.draft as Task; val scope = rememberCoroutineScope()
    Head(if (vm.editId == null) "New task" else "Edit task", vm)
    Field("Title", d.title, { vm.draft = d.copy(title = it) }, "e.g. Buy groceries")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("note","checklist").forEach { k ->
        Btn(k, d.kind == k, true, Modifier.weight(1f)) { vm.draft = d.copy(kind = k)
            if (k == "checklist" && vm.itemsOf(d.id).isEmpty()) scope.launch { Repo.dao.putItem(TaskItem(uid(), d.id, "", false, 0)); Repo.touch() } } } }
    if (d.kind == "checklist") { Spacer(Modifier.height(12.dp))
        Text("ITEMS", color = C.mut, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
        vm.itemsOf(d.id).forEach { i -> Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Ck(i.done) { scope.launch { Repo.toggleItem(i.id) } }; Spacer(Modifier.width(8.dp))
            val (txt, setTxt) = rememberSynced(i.id, i.text) { v -> Repo.dao.putItem(i.copy(text = v)) }
            OutlinedTextField(txt, setTxt,
                modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(11.dp), colors = tfColors())
            IconButton(onClick = { scope.launch { Repo.dao.delItem(i.id); Repo.touch() } }) { Icon(Icons.Default.Close, null, tint = C.red) } } }
        Btn("+ Add item", false, true, Modifier.fillMaxWidth().padding(top = 8.dp)) {
            scope.launch { Repo.dao.putItem(TaskItem(uid(), d.id, "", false, vm.itemsOf(d.id).size)); Repo.touch() } } }
    Spacer(Modifier.height(12.dp))
    Row { Box(Modifier.weight(1f)) { Field("Date (optional)", d.date ?: "", { vm.draft = d.copy(date = it.ifBlank { null }) }, "YYYY-MM-DD") }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) { Text("TIME", color = C.mut, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { TimeBtn(d.time ?: "") { vm.draft = d.copy(time = it) }
                if (d.time != null) IconButton(onClick = { vm.draft = d.copy(time = null) }) { Icon(Icons.Default.Close, null, tint = C.dim) } } } }
    Spacer(Modifier.height(12.dp))
    Text("MOMENT / PLACE", color = C.mut, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(6.dp))
    var open by remember { mutableStateOf(false) }
    Box { Btn(vm.moment(d.placeId)?.name ?: "No place", false, true) { open = true }
        DropdownMenu(open, { open = false }, containerColor = C.surf2) {
            DropdownMenuItem({ Text("No place", color = C.ink) }, { vm.draft = d.copy(placeId = null); open = false })
            vm.moments.value.forEach { m -> DropdownMenuItem({ Text("${m.icon} ${m.name}", color = C.ink) }, { vm.draft = d.copy(placeId = m.id); open = false }) } } }
    Spacer(Modifier.height(12.dp))
    Text("REPEAT", color = C.mut, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("none","daily","weekdays").forEach { r ->
        Btn(r, d.repeat == r, true, Modifier.weight(1f)) { vm.draft = d.copy(repeat = r) } } }
    SetRow("Remind at time") { Toggle(d.notify) { vm.draft = d.copy(notify = !d.notify) } }
    Row(Modifier.padding(top = 16.dp)) {
        if (vm.editId != null) { Btn("Delete", false, false, Modifier.weight(1f)) { vm.deleteDraft() }; Spacer(Modifier.width(10.dp)) }
        Btn("Save", true, false, Modifier.weight(2f)) { vm.saveDraft() } }
}
@Composable private fun HabitSheet(vm: Vm) {
    val d = vm.draft as Habit; val water = d.id == "water"
    Head(if (vm.editId == null) "New habit" else "Edit habit", vm)
    Field("Name", d.name, { vm.draft = d.copy(name = it) }, "e.g. Evening stretch")
    EmojiPick(d.icon) { vm.draft = d.copy(icon = it) }
    ColorPick(d.color) { vm.draft = d.copy(color = it) }
    if (water) Text("Water goal & interval live in More → Water.", color = C.dim, fontSize = 12.sp) else {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("tick" to "✓ Done","count" to "🔢 Count").forEach { (k, l) ->
            Btn(l, d.type == k, true, Modifier.weight(1f)) { vm.draft = d.copy(type = k) } } }
        if (d.type == "count") { Spacer(Modifier.height(12.dp))
            // keep the typed text: mapping "" straight back to 1 made clearing the field impossible ("5" became "15").
            // Save still enforces goal >= 1 (Vm.saveDraft).
            var goalText by remember(d.id) { mutableStateOf("${d.goal}") }
            Row { Box(Modifier.weight(1f)) { Field("Goal", goalText, { t -> val digits = t.filter { it.isDigit() }.take(4)
                    goalText = digits; vm.draft = d.copy(goal = digits.toIntOrNull() ?: 0) }) }
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) { Field("Unit", d.unit, { vm.draft = d.copy(unit = it) }, "pages, km…") } } } }
    Spacer(Modifier.height(12.dp))
    Text("DAYS", color = C.mut, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { DOW.forEachIndexed { i, w ->
        val on = uncsv(d.days).contains("$i")
        Btn(w, on, true, Modifier.weight(1f)) { val l = uncsv(d.days).toMutableList()
            if (on) l.remove("$i") else l.add("$i"); vm.draft = d.copy(days = csv(l.sortedBy { it.toInt() })) } } }
    Spacer(Modifier.height(12.dp))
    SetRow("Time") { Row(verticalAlignment = Alignment.CenterVertically) { TimeBtn(d.time ?: "") { vm.draft = d.copy(time = it) }
        if (d.time != null) IconButton(onClick = { vm.draft = d.copy(time = null) }) { Icon(Icons.Default.Close, null, tint = C.dim) } } }
    SetRow("Remind") { Toggle(d.remind) { vm.draft = d.copy(remind = !d.remind) } }
    Row(Modifier.padding(top = 16.dp)) {
        if (vm.editId != null && !water) { Btn("Delete", false, false, Modifier.weight(1f)) { vm.deleteDraft() }; Spacer(Modifier.width(10.dp)) }
        Btn("Save", true, false, Modifier.weight(2f)) { vm.saveDraft() } }
}
@Composable private fun MomentSheet(vm: Vm) {
    val d = vm.draft as Moment; val scope = rememberCoroutineScope()
    Head(if (vm.editId == null) "New moment" else "Edit moment", vm)
    Field("Name", d.name, { vm.draft = d.copy(name = it) }, "e.g. Temple, Airport")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("routine","place").forEach { k ->
        Btn(k, d.type == k, true, Modifier.weight(1f)) { vm.draft = d.copy(type = k) } } }
    Spacer(Modifier.height(12.dp))
    EmojiPick(d.icon) { vm.draft = d.copy(icon = it) }
    ColorPick(d.color) { vm.draft = d.copy(color = it) }
    Field("Trigger phrases (| separated)", d.aliases, { vm.draft = d.copy(aliases = it) }, "market|grocery|shop")
    Text("CHECKLIST", color = C.mut, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
    vm.mitemsOf(d.id).forEach { i -> Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        val (txt, setTxt) = rememberSynced(i.id, i.text) { v -> Repo.dao.putMItem(i.copy(text = v)) }
        OutlinedTextField(txt, setTxt,
            modifier = Modifier.weight(1f), singleLine = true, shape = RoundedCornerShape(11.dp), colors = tfColors())
        IconButton(onClick = { scope.launch { Repo.dao.delMItem(i.id); Repo.touch() } }) { Icon(Icons.Default.Close, null, tint = C.red) } } }
    Btn("+ Add item", false, true, Modifier.fillMaxWidth().padding(top = 8.dp)) {
        scope.launch { Repo.dao.putMItem(MomentItem(uid(), d.id, "", vm.mitemsOf(d.id).size)); Repo.touch() } }
    Row(Modifier.padding(top = 16.dp)) {
        if (vm.editId != null) { Btn("Delete", false, false, Modifier.weight(1f)) { vm.deleteDraft() }; Spacer(Modifier.width(10.dp)) }
        Btn("Save", true, false, Modifier.weight(2f)) { vm.saveDraft() } }
}
@Composable private fun MedSheet(vm: Vm) {
    val d = vm.draft as Medicine
    Head(if (vm.editId == null) "Add medicine" else "Edit medicine", vm)
    Field("Name", d.name, { vm.draft = d.copy(name = it) }, "e.g. Vitamin D")
    Field("Dose / note", d.dose, { vm.draft = d.copy(dose = it) }, "1 tablet after food")
    Field("Times (| separated)", d.times, { vm.draft = d.copy(times = it) }, "09:00|21:00")
    Text("SNOOZE", color = C.mut, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold); Spacer(Modifier.height(6.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf(5,10,15,30).forEach { m ->
        Btn("${m}m", d.snoozeMin == m, true, Modifier.weight(1f)) { vm.draft = d.copy(snoozeMin = m) } } }
    SetRow("Override quiet hours") { Toggle(d.overrideQuiet) { vm.draft = d.copy(overrideQuiet = !d.overrideQuiet) } }
    SetRow("Enabled", "Off = saved but no alerts") { Toggle(d.enabled) { vm.draft = d.copy(enabled = !d.enabled) } }
    Row(Modifier.padding(top = 16.dp)) {
        if (vm.editId != null) { Btn("Delete", false, false, Modifier.weight(1f)) { vm.deleteDraft() }; Spacer(Modifier.width(10.dp)) }
        Btn("Save", true, false, Modifier.weight(2f)) { vm.saveDraft() } }
}
