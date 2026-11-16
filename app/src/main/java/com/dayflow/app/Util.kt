package com.dayflow.app

import android.graphics.Color as AColor
import androidx.compose.ui.graphics.Color
import java.util.Calendar
import java.util.Locale
import java.util.UUID

fun uid(): String = UUID.randomUUID().toString().take(18)
fun pad(n: Int) = if (n < 10) "0$n" else "$n"
fun dstr(c: Calendar): String = "${c.get(Calendar.YEAR)}-${pad(c.get(Calendar.MONTH)+1)}-${pad(c.get(Calendar.DAY_OF_MONTH))}"
fun today(): String = dstr(Calendar.getInstance())
fun nowMs() = System.currentTimeMillis()
fun addDays(n: Int): Calendar { val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, n); return c }
fun calOf(date: String): Calendar {
    val p = date.split("-"); val c = Calendar.getInstance()
    c.set(p[0].toInt(), p[1].toInt()-1, p[2].toInt(), 0, 0, 0); c.set(Calendar.MILLISECOND, 0); return c
}
fun calOfMs(ms: Long): Calendar { val c = Calendar.getInstance(); c.timeInMillis = ms; return c }
/** Minutes since midnight for "HH:mm", or null if blank/malformed (user-entered times are free text). */
fun tmin(t: String?): Int? {
    val p = t?.trim()?.split(":") ?: return null
    if (p.size != 2) return null
    val h = p[0].toIntOrNull() ?: return null; val m = p[1].toIntOrNull() ?: return null
    return if (h in 0..23 && m in 0..59) h * 60 + m else null
}
/** Canonical "HH:mm" for a valid time string, else null. */
fun normTime(t: String?): String? = tmin(t)?.let { "${pad(it / 60)}:${pad(it % 60)}" }
fun nowMin(): Int { val c = Calendar.getInstance(); return c.get(Calendar.HOUR_OF_DAY)*60 + c.get(Calendar.MINUTE) }
fun fmtMin(m: Int) = fmtT("${pad(m/60)}:${pad(m%60)}")
fun fmtT(t: String?): String {
    if (t.isNullOrBlank()) return ""
    val mins = tmin(t) ?: return t
    var h = mins / 60; val m = mins % 60
    val ap = if (h >= 12) "PM" else "AM"; h %= 12; if (h == 0) h = 12
    return "$h:${pad(m)} $ap"
}
fun fmtDur(min: Int) = "${min/60}h ${pad(min%60)}m"
fun dow(c: Calendar) = c.get(Calendar.DAY_OF_WEEK) - 1
fun last7(): List<String> = (6 downTo 0).map { dstr(addDays(-it)) }
fun weekDays(offset: Int): List<Calendar> = (6 downTo 0).map { addDays(offset*7 - it) }
fun weekKey(c: Calendar): String { val x = c.clone() as Calendar; x.add(Calendar.DAY_OF_YEAR, -dow(x)); return dstr(x) }
fun col(hex: String): Color = Color(AColor.parseColor(hex))
fun colA(hex: String, a: Float): Color = col(hex).copy(alpha = a)
fun norm(s: String) = s.lowercase(Locale.US).replace(Regex("[^a-z0-9\\s]"), " ").replace(Regex("\\s+"), " ").trim()
fun titleCase(s: String) = s.split(" ").joinToString(" ") { if (it.isEmpty()) it else it[0].uppercase() + it.substring(1) }
fun csv(l: List<String>) = l.joinToString("|")
fun uncsv(s: String) = if (s.isBlank()) emptyList() else s.split("|").filter { it.isNotBlank() }
val PALETTE = listOf("#FFB347","#39D0D8","#8B7BFF","#4ADE80","#5EA8FF","#FF5D73","#F472B6","#FF8A5C")
val EMOJIS = listOf("💧","🏋️","🚶","📖","🧘","🌙","🌅","🏢","🏡","🛒","💊","📚","🎒","🍎","🥗","🦷","☀️","🌳","🧺","💻","📞","✍️","🎵","🙏","🧹","🏃","🌿","🔑","💼","🍳","🛏️","✨")
val DOW = listOf("S","M","T","W","T","F","S")
val DOWFULL = listOf("Sun","Mon","Tue","Wed","Thu","Fri","Sat")
