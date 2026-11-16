package com.dayflow.app

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Color
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.dayflow.app.widget.HabitsReceiver
import com.dayflow.app.widget.MomentsReceiver
import com.dayflow.app.widget.TodayReceiver
import com.dayflow.app.widget.WaterReceiver

/**
 * Debug-only harness: hosts the real app widgets at exact dp sizes so every responsive size can be inspected.
 * Needs `adb shell appwidget grantbind --package com.dayflow.app`.
 * Launch: `am start -n com.dayflow.app/.WidgetGallery --es w water --es s "110x110,180x110"`
 */
class WidgetGallery : Activity() {
    private lateinit var host: AppWidgetHost
    private val ids = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val provider = when (intent.getStringExtra("w")) {
            "moments" -> MomentsReceiver::class.java; "habits" -> HabitsReceiver::class.java
            "today" -> TodayReceiver::class.java; else -> WaterReceiver::class.java
        }
        val sizes = (intent.getStringExtra("s") ?: "110x110").split(",").map { s ->
            s.split("x").let { it[0].toFloat() to it[1].toFloat() } }
        val mgr = AppWidgetManager.getInstance(this)
        host = AppWidgetHost(this, 4242).also { it.startListening() }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16f), dp(40f), dp(16f), dp(40f)) }
        sizes.forEach { (w, h) ->
            col.addView(TextView(this).apply { text = "${w.toInt()} × ${h.toInt()} dp"; setTextColor(Color.WHITE); setPadding(0, dp(10f), 0, dp(4f)) })
            val id = host.allocateAppWidgetId(); ids += id
            val ok = mgr.bindAppWidgetIdIfAllowed(id, ComponentName(this, provider))
            if (!ok) { col.addView(TextView(this).apply { text = "bind not allowed (run appwidget grantbind)"; setTextColor(Color.RED) }); return@forEach }
            val view = host.createView(this, id, mgr.getAppWidgetInfo(id))
            view.updateAppWidgetSize(Bundle(), listOf(SizeF(w, h)))
            col.addView(FrameLayout(this).apply { addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)) },
                LinearLayout.LayoutParams(dp(w), dp(h)))
        }
        // mid-tone "wallpaper" so both the widget edges and its contents are visible
        setContentView(ScrollView(this).apply { setBackgroundColor(Color.rgb(70, 78, 96)); addView(col, MATCH_PARENT, WRAP_CONTENT) })
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

    override fun onDestroy() { ids.forEach { host.deleteAppWidgetId(it) }; host.stopListening(); super.onDestroy() }
}
