package com.dayflow.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.dayflow.app.*
import com.dayflow.app.data.*
import kotlinx.coroutines.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

const val CH_REM = "reminders"; const val CH_MED = "medicine"; const val CH_REV = "review"

object A {
    const val WATER = "df.WATER"; const val HABIT = "df.HABIT"; const val TASK = "df.TASK"
    const val MED = "df.MED"; const val MENU = "df.MENU"; const val SNOOZE = "df.SNOOZE"
}

object Notifier {
    fun init(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_REM, "Reminders", NotificationManager.IMPORTANCE_DEFAULT))
        nm.createNotificationChannel(NotificationChannel(CH_MED, "Medicine", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_REV, "Weekly review", NotificationManager.IMPORTANCE_LOW))
    }
    private fun pi(ctx: Context, action: String, key: String, extra: String = "", minutes: Int = 0): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java).setAction(action)
            .putExtra("key", key).putExtra("extra", extra).putExtra("minutes", minutes)
        return PendingIntent.getBroadcast(ctx, (action + key + minutes).hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    fun open(ctx: Context, momentId: String = ""): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).putExtra("moment", momentId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, momentId.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    fun post(ctx: Context, key: String, title: String, body: String, kind: String, id: String) {
        val ch = if (kind == "med") CH_MED else CH_REM
        val b = NotificationCompat.Builder(ctx, ch)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title).setContentText(body).setAutoCancel(true)
            .setContentIntent(open(ctx, if (kind == "moment") id else ""))
        when (kind) {
            "water" -> { b.addAction(0, "+1 Glass", pi(ctx, A.WATER, key)); b.addAction(0, "Snooze", pi(ctx, A.MENU, key, title)) }
            "habit" -> { b.addAction(0, "Done", pi(ctx, A.HABIT, key, id)); b.addAction(0, "Snooze", pi(ctx, A.MENU, key, title)) }
            "task" -> { b.addAction(0, "Done", pi(ctx, A.TASK, key, id)); b.addAction(0, "Snooze", pi(ctx, A.MENU, key, title)); b.addAction(0, "Open", open(ctx)) }
            "med" -> { b.addAction(0, "Taken", pi(ctx, A.MED, key, id)); b.addAction(0, "Snooze", pi(ctx, A.MENU, key, title)) }
            "moment" -> { b.addAction(0, "Start", open(ctx, id)); b.addAction(0, "Later", pi(ctx, A.MENU, key, title)) }
        }
        try { NotificationManagerCompat.from(ctx).notify(key.hashCode(), b.build()) } catch (_: SecurityException) {}
    }
    fun review(ctx: Context, headline: String) {
        val b = NotificationCompat.Builder(ctx, CH_REV).setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📊 Weekly review ready").setContentText(headline)
            .setContentIntent(open(ctx)).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(ctx).notify(9001, b) } catch (_: SecurityException) {}
    }
    fun snoozeMenu(ctx: Context, key: String, title: String) {
        val b = NotificationCompat.Builder(ctx, CH_REM).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Snooze: $title").setContentText("Choose a snooze duration").setAutoCancel(true)
        listOf(5, 10, 15, 30).forEach { m -> b.addAction(0, "${m}m", pi(ctx, A.SNOOZE, key, title, m)) }
        try { NotificationManagerCompat.from(ctx).notify(key.hashCode(), b.build()) } catch (_: SecurityException) {}
    }
    fun cancel(ctx: Context, key: String) = NotificationManagerCompat.from(ctx).cancel(key.hashCode())
}

class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val key = intent.getStringExtra("key") ?: return
        val extra = intent.getStringExtra("extra") ?: ""
        val minutes = intent.getIntExtra("minutes", 0)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Repo.init(ctx)
                when (intent.action) {
                    A.WATER -> { Repo.addWater(1); Notifier.cancel(ctx, key) }
                    // notification buttons may be stale, so they set state rather than toggle it
                    A.HABIT -> { Repo.completeHabit(extra); Notifier.cancel(ctx, key) }
                    A.TASK -> { Repo.completeTask(extra); Notifier.cancel(ctx, key) }
                    A.MED -> { val (id, tm) = extra.split("@", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                        Repo.markTaken(id, tm); Notifier.cancel(ctx, key) }
                    A.MENU -> Notifier.snoozeMenu(ctx, key, extra)
                    A.SNOOZE -> { Repo.snooze(key, minutes); Notifier.cancel(ctx, key) }
                }
            } finally { pending.finish() }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) schedule(ctx)
    }
}

class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Repo.init(applicationContext); Notifier.init(applicationContext)
        runReminders(applicationContext); return Result.success()
    }
}

fun schedule(ctx: Context) {
    val req = PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).build()
    WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("dayflow-reminders", ExistingPeriodicWorkPolicy.KEEP, req)
}

/** One-off reminder pass after a snooze, since the periodic worker only runs every 15 minutes. */
fun runAfter(ctx: Context, key: String, minutes: Int) {
    WorkManager.getInstance(ctx).enqueueUniqueWork("dayflow-snooze-$key", ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(minutes.toLong(), TimeUnit.MINUTES).build())
}

fun runNow(ctx: Context) {
    WorkManager.getInstance(ctx).enqueueUniqueWork("dayflow-now", ExistingWorkPolicy.REPLACE,
        OneTimeWorkRequestBuilder<ReminderWorker>().build())
}

suspend fun runReminders(ctx: Context) {
    val p = Repo.p(); val t = today(); val nm = nowMin(); val now = nowMs()
    Repo.rollDay()
    val snoozes = Repo.dao.snoozes().associate { it.key to it.until }
    val notified = Repo.dao.notified().map { it.key }.toSet()
    fun quiet(): Boolean {
        if (p.sleeping) return true
        if (!p.quietEnabled) return false
        val a = tmin(p.quietStart) ?: return false; val b = tmin(p.quietEnd) ?: return false
        return if (a == b) false else if (a < b) nm in a until b else nm >= a || nm < b
    }
    suspend fun fire(key: String, title: String, body: String, kind: String, id: String, force: Boolean = false) {
        if (notified.contains(key)) return
        val s = snoozes[key]; if (s != null && now < s) return
        if (quiet() && !force) return
        if (!p.notifications) return
        Repo.dao.putNotified(Notified(key, now)); Notifier.post(ctx, key, title, body, kind, id)
    }
    Repo.water()?.let { w ->
        val cnt = Repo.logVal(t, "water"); val st = tmin(p.waterStart); val en = tmin(p.waterEnd)
        if (st != null && en != null && p.waterInterval > 0 && cnt < w.goal && nm in st..en) {
            val k = (nm - st) / p.waterInterval
            fire("w:$t:$k", "💧 Water time", "Glass ${cnt + 1} of ${w.goal}", "water", "water")
        }
    }
    Repo.dao.habitsOnce().forEach { h ->
        if (h.id == "water" || h.time == null || !h.remind || !Repo.scheduled(h, Calendar.getInstance()) || Repo.doneHabit(h, t)) return@forEach
        val at = tmin(h.time) ?: return@forEach
        if (nm >= at) fire("h:${h.id}:$t", "${h.icon} ${h.name}", "Scheduled for ${fmtT(h.time)}", "habit", h.id)
    }
    Repo.dao.tasksOnce().forEach { x ->
        if (x.done || x.time == null || !x.notify || (x.date != null && x.date != t)) return@forEach
        val at = tmin(x.time) ?: return@forEach
        if (nm >= at) fire("t:${x.id}:$t", "⏰ ${x.title}", "Reminder", "task", x.id)
    }
    Repo.dao.medsOnce().filter { it.enabled }.forEach { m ->
        uncsv(m.times).forEach { tm ->
            val at = tmin(tm) ?: return@forEach
            if (nm >= at && !Repo.doseTaken(m.id, tm))
                fire(Repo.medNotifKey(m.id, t, tm), "💊 ${m.name}", "${m.dose} · ${fmtT(tm)}", "med", Repo.doseKey(m.id, tm), m.overrideQuiet)
        }
    }
    if (p.notifications && p.reviewEnabled && dow(Calendar.getInstance()) == 0 && nm >= (tmin(p.reviewTime) ?: 20 * 60)) {
        val key = "review:${weekKey(Calendar.getInstance())}"
        if (!notified.contains(key)) { Repo.dao.putNotified(Notified(key, now)); Notifier.review(ctx, Repo.generateReview().headline) }
    }
    Repo.touch()  // bumps Repo.changes so live widgets/cards re-query (time-based state may have changed)
}
