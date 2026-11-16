package com.dayflow.app.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dayflow.app.col
import com.dayflow.app.fmtT
import com.dayflow.app.tmin
import java.util.Calendar
import kotlinx.coroutines.launch

object C {
    val bg = col("#0C0E1C"); val bg2 = col("#12152B"); val surf = col("#191D38"); val surf2 = col("#20254A")
    val line = col("#2C3160"); val line2 = col("#3A4080")
    val ink = col("#F4EEDF"); val mut = col("#9AA0C7"); val dim = col("#6B7199")
    val aqua = col("#39D0D8"); val vio = col("#8B7BFF"); val grn = col("#4ADE80"); val blu = col("#5EA8FF")
    val red = col("#FF5D73"); val mag = col("#F472B6"); val input = col("#0F1230")
}
val LocalAcc = staticCompositionLocalOf { col("#FFB347") to col("#FF8A5C") }

@Composable fun DayflowTheme(accent: String, content: @Composable () -> Unit) {
    val pair = when (accent) {
        "aqua" -> col("#39D0D8") to col("#5EA8FF")
        "violet" -> col("#8B7BFF") to col("#F472B6")
        "green" -> col("#4ADE80") to col("#39D0D8")
        else -> col("#FFB347") to col("#FF8A5C")
    }
    CompositionLocalProvider(LocalAcc provides pair) {
        MaterialTheme(colorScheme = darkColorScheme(
            primary = pair.first, secondary = pair.second, background = C.bg, surface = C.surf,
            onPrimary = col("#1A1206"), onBackground = C.ink, onSurface = C.ink), content = content)
    }
}
@Composable fun DCard(mod: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(mod.fillMaxWidth().clip(RoundedCornerShape(20.dp))
        .background(Brush.verticalGradient(listOf(C.surf, C.bg2)))
        .border(1.dp, C.line, RoundedCornerShape(20.dp)).padding(16.dp), content = content)
}
@Composable fun Sec(title: String, count: String? = null, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = C.ink, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
        if (count != null) { Spacer(Modifier.width(8.dp)); Tag(count) }
        Spacer(Modifier.weight(1f))
        if (action != null) Text(action, color = LocalAcc.current.first, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onAction?.invoke() }.padding(6.dp))
    }
}
@Composable fun Tag(text: String, color: Color = C.mut) {
    Box(Modifier.clip(CircleShape).background(C.bg2).border(1.dp, color.copy(alpha = .35f), CircleShape)
        .padding(horizontal = 8.dp, vertical = 2.dp)) { Text(text, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
}
@Composable fun Btn(text: String, primary: Boolean = true, small: Boolean = false, mod: Modifier = Modifier, onClick: () -> Unit) {
    val a = LocalAcc.current; val r = if (small) 10.dp else 13.dp
    Box(mod.clip(RoundedCornerShape(r))
        .background(if (primary) Brush.horizontalGradient(listOf(a.first, a.second)) else SolidColor(C.surf))
        .border(if (primary) 0.dp else 1.dp, C.line2, RoundedCornerShape(r))
        .clickable(onClick = onClick)
        .padding(horizontal = if (small) 14.dp else 18.dp, vertical = if (small) 8.dp else 12.dp),
        contentAlignment = Alignment.Center) {
        Text(text, color = if (primary) col("#1A1206") else C.ink, fontSize = if (small) 12.sp else 14.sp, fontWeight = FontWeight.ExtraBold)
    }
}
@Composable fun Ck(on: Boolean, onClick: () -> Unit) {
    val a = LocalAcc.current
    Box(Modifier.size(28.dp).clip(RoundedCornerShape(9.dp))
        .background(if (on) Brush.horizontalGradient(listOf(a.first, a.second)) else SolidColor(Color.Transparent))
        .border(2.dp, if (on) Color.Transparent else C.dim, RoundedCornerShape(9.dp))
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (on) Text("✓", color = col("#1A1206"), fontSize = 15.sp, fontWeight = FontWeight.Black)
    }
}
@Composable fun Stepper(value: String, color: Color, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(C.surf2).border(1.dp, C.line2, RoundedCornerShape(9.dp)).clickable(onClick = onMinus), contentAlignment = Alignment.Center) { Text("−", color = C.ink, fontWeight = FontWeight.Bold) }
        Text(value, color = color, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 9.dp))
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(C.surf2).border(1.dp, C.line2, RoundedCornerShape(9.dp)).clickable(onClick = onPlus), contentAlignment = Alignment.Center) { Text("+", color = C.ink, fontWeight = FontWeight.Bold) }
    }
}
@Composable fun Ring(pct: Int, size: Int = 74) {
    val a = LocalAcc.current
    Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size.dp)) {
            val sw = 7.dp.toPx(); val d = size.dp.toPx() - sw
            drawArc(Color.White.copy(alpha = .2f), 0f, 360f, false, Offset(sw/2, sw/2), Size(d, d), style = Stroke(sw))
            drawArc(Brush.sweepGradient(listOf(a.first, a.second, a.first)), -90f, 360f * pct / 100f, false,
                Offset(sw/2, sw/2), Size(d, d), style = Stroke(sw, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$pct%", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
            Text("TODAY", color = Color.White.copy(alpha = .8f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
    }
}
@Composable fun Empty(icon: String, title: String, sub: String) {
    Column(Modifier.fillMaxWidth().padding(34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 40.sp); Spacer(Modifier.height(8.dp))
        Text(title, color = C.mut, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
        Text(sub, color = C.dim, fontSize = 12.sp)
    }
}
@Composable fun tfColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LocalAcc.current.first, unfocusedBorderColor = C.line,
    focusedContainerColor = C.input, unfocusedContainerColor = C.input,
    focusedTextColor = C.ink, unfocusedTextColor = C.ink, cursorColor = LocalAcc.current.first)
@Composable fun Field(label: String, value: String, onChange: (String) -> Unit, placeholder: String = "") {
    Column(Modifier.padding(bottom = 12.dp)) {
        Text(label.uppercase(), color = C.mut, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(bottom = 6.dp))
        OutlinedTextField(value, onChange, placeholder = { Text(placeholder, color = C.dim) },
            modifier = Modifier.fillMaxWidth(), colors = tfColors(), shape = RoundedCornerShape(11.dp), singleLine = true)
    }
}
@Composable fun Toggle(on: Boolean, onClick: () -> Unit) {
    val a = LocalAcc.current
    Box(Modifier.width(48.dp).height(27.dp).clip(CircleShape)
        .background(if (on) Brush.horizontalGradient(listOf(a.first, a.second)) else SolidColor(C.surf2))
        .border(1.dp, if (on) Color.Transparent else C.line2, CircleShape).clickable(onClick = onClick)) {
        Box(Modifier.padding(start = if (on) 24.dp else 3.dp, top = 3.dp).size(19.dp).clip(CircleShape)
            .background(if (on) col("#1A1206") else C.dim))
    }
}
@Composable fun SetRow(title: String, sub: String? = null, right: @Composable () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = C.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                if (sub != null) Text(sub, color = C.dim, fontSize = 11.sp)
            }
            right()
        }
        HorizontalDivider(color = C.line, thickness = 1.dp)
    }
}
@Composable fun TimeBtn(value: String, onChange: (String) -> Unit) {
    val ctx = LocalContext.current
    Box(Modifier.clip(RoundedCornerShape(11.dp)).background(C.input).border(1.dp, C.line, RoundedCornerShape(11.dp))
        .clickable {
            val c = Calendar.getInstance(); val m = tmin(value)
            if (m != null) { c.set(Calendar.HOUR_OF_DAY, m / 60); c.set(Calendar.MINUTE, m % 60) }
            TimePickerDialog(ctx, { _, h, mi -> onChange("${if (h<10) "0$h" else "$h"}:${if (mi<10) "0$mi" else "$mi"}") },
                c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), false).show()
        }.padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(if (value.isBlank()) "Set time" else fmtT(value), color = if (value.isBlank()) C.dim else C.ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
@Composable fun BarChart(values: List<Float>, max: Float, colors: List<Color>, goal: Float? = null) {
    Canvas(Modifier.fillMaxWidth().height(150.dp)) {
        val n = values.size; if (n == 0) return@Canvas
        val gap = size.width / (n * 2.4f); val bw = (size.width - gap * (n + 1)) / n
        val h = size.height - 8.dp.toPx()
        goal?.let { g -> val y = h - (g / max) * h
            drawLine(col("#39D0D8").copy(alpha = .6f), Offset(0f, y), Offset(size.width, y), 2.dp.toPx()) }
        values.forEachIndexed { i, v ->
            val x = gap + i * (bw + gap)
            // no data -> faint neutral baseline, never a coloured stub that reads as a recorded value
            val empty = v <= 0f
            val bh = if (empty) 3.dp.toPx() else ((v / max).coerceIn(0f, 1f) * h).coerceAtLeast(3.dp.toPx())
            drawRoundRect(if (empty) C.line else colors[i % colors.size], Offset(x, h - bh), Size(bw, bh), CornerRadius(7.dp.toPx()))
        }
    }
}

/** Serialises background writes in submission order and outlives the composable/sheet that issued them. */
object Writes {
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate)
    private val lock = kotlinx.coroutines.sync.Mutex()
    fun submit(block: suspend () -> Unit) {
        scope.launch { lock.lock(); try { block() } finally { lock.unlock() } }
    }
}

/**
 * Text bound to stored data. The field shows local state (so the async DB/DataStore round-trip can't
 * overwrite what's being typed) and every edit is persisted in order via [Writes].
 */
@Composable fun rememberSynced(key: Any?, stored: String, save: suspend (String) -> Unit): Pair<String, (String) -> Unit> {
    var text by remember(key) { mutableStateOf(stored) }
    return text to { v: String -> text = v; Writes.submit { save(v) } }
}
