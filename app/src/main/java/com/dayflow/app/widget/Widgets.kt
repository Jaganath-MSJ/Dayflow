package com.dayflow.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.dayflow.app.MainActivity
import com.dayflow.app.data.Habit
import com.dayflow.app.data.Repo
import com.dayflow.app.data.Task
import com.dayflow.app.fmtT
import com.dayflow.app.today
import com.dayflow.app.tmin
import java.util.Calendar

private fun cp(hex: String) = ColorProvider(ComposeColor(android.graphics.Color.parseColor(hex)))
private val BG = cp("#12152B"); private val INK = cp("#F4EEDF"); private val MUT = cp("#9AA0C7")
private val ACC = cp("#FFB347"); private val AQUA = cp("#39D0D8"); private val GRN = cp("#4ADE80")
private val DARK = cp("#101223"); private val SURF = cp("#20254A")
private val ID = ActionParameters.Key<String>("id")
private val DELTA = ActionParameters.Key<Int>("delta")
/** Delivered as the "moment" intent extra, which MainActivity.handleIntent reads. */
private val MOMENT = ActionParameters.Key<String>("moment")

suspend fun refreshAll(ctx: Context) {
    try {
        WaterWidget().updateAll(ctx); MomentsWidget().updateAll(ctx)
        HabitsWidget().updateAll(ctx); TodayWidget().updateAll(ctx)
    } catch (_: Exception) {}
}

class WaterAdd : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        Repo.init(context); Repo.addWater(parameters[ID]?.toIntOrNull() ?: 1); refreshAll(context)
    }
}
class HabitTick : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ID] ?: return
        Repo.init(context); Repo.tickHabit(id); refreshAll(context)
    }
}
class HabitBump : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ID] ?: return
        Repo.init(context); Repo.bumpHabit(id, parameters[DELTA] ?: 1); refreshAll(context)
    }
}
class TaskToggle : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ID] ?: return
        Repo.init(context); Repo.toggleTask(id); refreshAll(context)
    }
}

/**
 * provideGlance runs once per session, so values captured there go stale when updateAll() recomposes a live
 * session. Load inside the composition instead and reload whenever Repo.touch() bumps Repo.changes.
 */
@Composable private fun <T> live(initial: T, load: suspend () -> T): T {
    val tick by Repo.changes.collectAsState()
    var v by remember { mutableStateOf(initial) }
    LaunchedEffect(tick) { v = load() }
    return v
}

/** compact = single-line widget (< COMPACT_H tall): same look, slimmer vertical padding so text isn't clipped. */
@Composable private fun Shell(compact: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(GlanceModifier.fillMaxSize().background(BG).cornerRadius(22.dp)
        .padding(horizontal = 12.dp, vertical = if (compact) 4.dp else 12.dp), content = content)
}
@Composable private fun Title(t: String, modifier: GlanceModifier = GlanceModifier) {
    Text(t, modifier = modifier, maxLines = 1, style = TextStyle(color = ACC, fontSize = 12.sp, fontWeight = FontWeight.Bold))
}
@Composable private fun Btn(label: String, action: Action, color: ColorProvider = ACC, modifier: GlanceModifier = GlanceModifier,
                            hPad: Dp = 10.dp, textSize: androidx.compose.ui.unit.TextUnit = 12.sp) {
    Box(modifier.background(color).cornerRadius(10.dp).padding(horizontal = hPad, vertical = 6.dp).clickable(action),
        contentAlignment = Alignment.Center) { Text(label, maxLines = 1, style = TextStyle(color = DARK, fontSize = textSize, fontWeight = FontWeight.Bold)) }
}

/*
 * Layout notes (all widgets use SizeMode.Exact so LocalSize is the real size, not the nearest of a few buckets):
 *  - RemoteViews containers render at most 10 children, so rows use padding instead of Spacer siblings.
 *  - Optional rows are dropped as the widget shrinks instead of being clipped.
 *  - List rows keep end padding so the LazyColumn scrollbar never sits on top of the buttons.
 */
private val COMPACT_H = 80.dp    // single-line layout below this height
private val NARROW_W = 150.dp    // drop secondary labels below this width

class WaterWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Repo.init(context)
        val load = suspend { (Repo.water()?.goal ?: 8) to Repo.logVal(today(), "water") }
        val first = load()
        provideContent {
            val (goal, c) = live(first, load)
            val size = LocalSize.current
            val add = actionRunCallback<WaterAdd>(actionParametersOf(ID to "1"))
            val sub = actionRunCallback<WaterAdd>(actionParametersOf(ID to "-1"))
            val narrow = size.width < NARROW_W
            Shell(compact = size.height < COMPACT_H) {
                if (size.height < COMPACT_H) {
                    // too small for a button: the whole tile adds a glass
                    Row(GlanceModifier.fillMaxSize().let { if (narrow) it.clickable(add) else it },
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(if (narrow) "💧$c/$goal" else "💧 $c/$goal", modifier = GlanceModifier.defaultWeight(), maxLines = 1,
                            style = TextStyle(color = AQUA, fontSize = if (narrow) 14.sp else 18.sp, fontWeight = FontWeight.Bold))
                        if (!narrow) Btn("+1", add)
                    }
                    return@Shell
                }
                Title("💧 WATER")
                if (size.height < 140.dp) {
                    // medium: count and the main action side by side
                    Row(GlanceModifier.fillMaxWidth().defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                        Text("$c/$goal", modifier = GlanceModifier.defaultWeight(), maxLines = 1,
                            style = TextStyle(color = AQUA, fontSize = if (narrow) 22.sp else 28.sp, fontWeight = FontWeight.Bold))
                        Btn("+1", add)
                    }
                    return@Shell
                }
                Text("$c / $goal", modifier = GlanceModifier.padding(top = 4.dp), maxLines = 1,
                    style = TextStyle(color = AQUA, fontSize = 28.sp, fontWeight = FontWeight.Bold))
                if (size.height >= 150.dp) {
                    Text("glasses today", maxLines = 1, style = TextStyle(color = MUT, fontSize = 11.sp))
                    // bars share the full width; one child per bar (spacing via padding), max 10.
                    // Narrow widgets use thinner bars/gaps so the whole goal still fits.
                    val gap = if (narrow) 2.dp else 3.dp
                    val n = minOf(goal, ((size.width.value - 24f) / (if (narrow) 8f else 13f)).toInt().coerceIn(1, 10))
                    Row(GlanceModifier.fillMaxWidth().padding(top = 8.dp)) {
                        repeat(n) { i ->
                            Box(GlanceModifier.defaultWeight().padding(end = if (i < n - 1) gap else 0.dp)) {
                                Box(GlanceModifier.fillMaxWidth().height(16.dp).cornerRadius(3.dp)
                                    .background(if (i < c) AQUA else SURF)) {}
                            }
                        }
                    }
                }
                // buttons sit right under the bars; the main action stretches to fill the row, "−1" keeps its size
                Row(GlanceModifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (narrow) Btn("+1", add, modifier = GlanceModifier.fillMaxWidth())   // "−1" doesn't fit; still in the app
                    else {
                        Box(GlanceModifier.defaultWeight().padding(end = 6.dp)) { Btn("+1 glass", add, modifier = GlanceModifier.fillMaxWidth()) }
                        Btn("−1", sub, MUT)
                    }
                }
                if (size.height >= 220.dp) {
                    Spacer(GlanceModifier.defaultWeight())   // spare height goes here; the link stays at the bottom
                    Text("Open Dayflow →", maxLines = 1, style = TextStyle(color = MUT, fontSize = 11.sp),
                        modifier = GlanceModifier.padding(top = 10.dp).clickable(actionStartActivity<MainActivity>()))
                }
            }
        }
    }
}
class WaterReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = WaterWidget() }

class MomentsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Repo.init(context)
        val load = suspend { Repo.dao.momentsOnce().filter { it.type == "routine" }.sortedBy { it.sortOrder } }
        val first = load()
        provideContent {
            val all = live(first, load)
            val size = LocalSize.current
            val compact = size.height < COMPACT_H
            // Tiles are at least 28dp wide x 32dp tall with 4dp gaps (the 18sp emoji still fits). Use as many rows
            // as fit so every routine stays reachable; rows/columns are capped at 10 (RemoteViews container limit).
            // The title is dropped only when keeping it would push a routine out.
            val innerW = size.width.value - 24f
            fun innerH(title: Boolean) = size.height.value - (if (compact) 8f else 24f) - (if (title) 30f else 0f)
            val cols = ((innerW + 4f) / 32f).toInt().coerceIn(1, 10)
            fun capacity(title: Boolean) = cols * ((innerH(title) + 4f) / 36f).toInt().coerceIn(1, 10)
            val showTitle = size.height >= 90.dp && (capacity(true) >= all.size || capacity(false) <= capacity(true))
            val maxRows = ((innerH(showTitle) + 4f) / 36f).toInt().coerceIn(1, 10)
            val rows = minOf(maxRows, (all.size + cols - 1) / cols).coerceAtLeast(1)
            val perRow = minOf(cols, (all.size + rows - 1) / rows).coerceAtLeast(1)
            val grid = all.take(rows * perRow).chunked(perRow)
            Shell(compact = compact) {
                if (showTitle) Title("⚡ MOMENTS", GlanceModifier.padding(bottom = 8.dp))
                Column(GlanceModifier.fillMaxWidth().defaultWeight()) {
                    grid.forEachIndexed { r, line ->
                        Row(GlanceModifier.fillMaxWidth().defaultWeight().padding(bottom = if (r < grid.lastIndex) 4.dp else 0.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            line.forEachIndexed { i, m ->
                                Box(GlanceModifier.defaultWeight().fillMaxHeight().padding(end = if (i < perRow - 1) 4.dp else 0.dp)) {
                                    Box(GlanceModifier.fillMaxSize().background(SURF).cornerRadius(10.dp)
                                        .clickable(actionStartActivity<MainActivity>(actionParametersOf(MOMENT to m.id))),
                                        contentAlignment = Alignment.Center) { Text(m.icon, style = TextStyle(fontSize = 18.sp)) }
                                }
                            }
                            // keep tile widths equal on a short last row
                            repeat(perRow - line.size) { i -> Box(GlanceModifier.defaultWeight().fillMaxHeight()
                                .padding(start = 4.dp)) {} }
                        }
                    }
                }
            }
        }
    }
}
class MomentsReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = MomentsWidget() }

class HabitsWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Repo.init(context)
        val load = suspend {
            val t = today(); val cal = Calendar.getInstance()
            Repo.dao.habitsOnce().filter { it.id != "water" && Repo.scheduled(it, cal) }
                .map { Triple(it, Repo.logVal(t, it.id), Repo.doneHabit(it, t)) }
        }
        val first = load()
        provideContent {
            val rows = live(first, load)
            val size = LocalSize.current; val narrow = size.width < NARROW_W
            Shell(compact = size.height < COMPACT_H) {
                if (size.height < COMPACT_H) {
                    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        Title(if (narrow) "🔥" else "🔥 HABITS", GlanceModifier.defaultWeight())
                        Text("${rows.count { it.third }}/${rows.size}", maxLines = 1,
                            style = TextStyle(color = GRN, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                    }
                    return@Shell
                }
                Title(if (narrow) "🔥 HABITS" else "🔥 TODAY'S HABITS", GlanceModifier.padding(bottom = 6.dp))
                val fit = wholeRows(size.height, header = 22.dp, row = 42.dp)
                // one-column slots (~80dp, e.g. the Android 14 Pixel launcher) clipped the "Done" button
                val tiny = size.width < 110.dp
                if (fit != null) Column(GlanceModifier.defaultWeight()) { rows.take(fit).forEach { HabitRow(it, narrow, tiny) } }
                else LazyColumn(GlanceModifier.defaultWeight()) { items(rows) { HabitRow(it, narrow, tiny) } }
            }
        }
    }
}
class HabitsReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = HabitsWidget() }

class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        Repo.init(context)
        val load = suspend {
            val t = today()
            Triple(Repo.nextReminder(), Repo.todayStats(),
                Repo.dao.tasksOnce().filter { !it.done && (it.date == t || it.date == null) }.sortedBy { tmin(it.time) ?: 9999 })
        }
        val first = load()
        provideContent {
            val (next, stats, ts) = live(first, load)
            val size = LocalSize.current; val narrow = size.width < NARROW_W
            Shell(compact = size.height < COMPACT_H) {
                Row(GlanceModifier.fillMaxWidth().let { if (size.height < COMPACT_H) it.defaultWeight() else it },
                    verticalAlignment = Alignment.CenterVertically) {
                    Title(if (narrow) "📋" else "📋 TODAY", GlanceModifier.defaultWeight())
                    Text("${stats.first}%", maxLines = 1, style = TextStyle(color = GRN, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                }
                if (size.height < COMPACT_H) return@Shell
                Text(next?.let { if (narrow) "Next: ${it.second}" else "Next: ${it.first} · ${it.second}" } ?: "All clear ✨",
                    maxLines = 1, style = TextStyle(color = ACC, fontSize = 11.sp))
                Spacer(GlanceModifier.height(6.dp))
                val fit = wholeRows(size.height, header = 46.dp, row = 28.dp)
                if (fit != null) Column(GlanceModifier.defaultWeight()) { ts.take(fit).forEach { TaskRow(it, inline = true, narrow = narrow) } }
                else LazyColumn(GlanceModifier.defaultWeight()) { items(ts) { TaskRow(it, inline = false, narrow = narrow) } }
            }
        }
    }
}
class TodayReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget = TodayWidget() }

/**
 * Short widgets show only the rows that fit completely (a scrolling list would cut the last row in half).
 * Returns that row count, or null when the widget is tall enough for the normal scrolling list.
 */
private fun wholeRows(height: Dp, header: Dp, row: Dp): Int? {
    if (height >= 150.dp) return null
    return ((height - 24.dp - header) / row).toInt().coerceIn(1, 8)   // 24dp = Shell vertical padding
}

/** tiny (< 110dp wide): no icon and slimmer button padding so the action button always fits. */
@Composable private fun HabitRow(row: Triple<Habit, Int, Boolean>, narrow: Boolean, tiny: Boolean = false) {
    val (h, v, done) = row
    // ~80dp slots leave the button only ~36dp: slimmer padding and 10sp text keep "Done" whole
    val pad = if (tiny) 4.dp else 10.dp; val ts = if (tiny) 10.sp else 12.sp
    Row(GlanceModifier.fillMaxWidth().padding(top = 5.dp, bottom = 5.dp, end = if (tiny) 4.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!tiny) Text(h.icon, modifier = GlanceModifier.padding(end = 8.dp), style = TextStyle(fontSize = 16.sp))
        Column(GlanceModifier.defaultWeight()) {
            if (!narrow) Text(h.name, maxLines = 1, style = TextStyle(color = if (done) MUT else INK, fontSize = 13.sp))
            if (h.type == "count") Text(if (narrow) "$v/${h.goal}" else "$v/${h.goal} ${h.unit}", maxLines = 1,
                style = TextStyle(color = MUT, fontSize = 10.sp))
        }
        // a finished count habit shows ✓ like a tick habit; tapping it undoes one step (5/5 -> 4/5)
        if (h.type == "count") {
            if (done) Btn("✓", actionRunCallback<HabitBump>(actionParametersOf(ID to h.id, DELTA to -1)), GRN, hPad = pad, textSize = ts)
            else Btn("+1", actionRunCallback<HabitBump>(actionParametersOf(ID to h.id, DELTA to 1)), AQUA, hPad = pad, textSize = ts)
        } else Btn(if (done) "✓" else "Done", actionRunCallback<HabitTick>(actionParametersOf(ID to h.id)), if (done) GRN else ACC, hPad = pad, textSize = ts)
    }
}

/** inline = time on the same line as the title (short widgets), otherwise stacked under it. */
@Composable private fun TaskRow(x: Task, inline: Boolean, narrow: Boolean) {
    Row(GlanceModifier.fillMaxWidth().padding(top = if (inline) 3.dp else 5.dp, bottom = if (inline) 3.dp else 5.dp, end = 8.dp)
        .clickable(actionRunCallback<TaskToggle>(actionParametersOf(ID to x.id))),
        verticalAlignment = Alignment.CenterVertically) {
        Text("☐", modifier = GlanceModifier.padding(end = 8.dp), style = TextStyle(color = MUT, fontSize = 15.sp))
        if (inline) {
            Text(x.title, modifier = GlanceModifier.defaultWeight(), maxLines = 1, style = TextStyle(color = INK, fontSize = 13.sp))
            if (x.time != null && !narrow) Text(fmtT(x.time), modifier = GlanceModifier.padding(start = 6.dp), maxLines = 1,
                style = TextStyle(color = AQUA, fontSize = 10.sp))
        } else Column(GlanceModifier.defaultWeight()) {
            Text(x.title, maxLines = 1, style = TextStyle(color = INK, fontSize = 13.sp))
            if (x.time != null) Text(fmtT(x.time), maxLines = 1, style = TextStyle(color = AQUA, fontSize = 10.sp))
        }
    }
}
