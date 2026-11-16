package com.dayflow.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity("habits") data class Habit(
    @PrimaryKey val id: String, val name: String, val icon: String, val color: String,
    val type: String, val goal: Int, val unit: String, val days: String,
    val time: String?, val remind: Boolean, val sortOrder: Int)
@Entity("habit_logs", primaryKeys = ["date","habitId"]) data class HabitLog(val date: String, val habitId: String, val value: Int)
@Entity("tasks") data class Task(
    @PrimaryKey val id: String, val title: String, val kind: String, val date: String?, val time: String?,
    val placeId: String?, val repeat: String, val notify: Boolean, val done: Boolean,
    val doneAt: Long?, val createdAt: Long, val sortOrder: Int)
@Entity("task_items") data class TaskItem(@PrimaryKey val id: String, val taskId: String, val text: String, val done: Boolean, val sortOrder: Int)
@Entity("moments") data class Moment(
    @PrimaryKey val id: String, val mkey: String?, val type: String, val name: String, val icon: String,
    val color: String, val aliases: String, val runs: Int, val lastRun: Long?, val sortOrder: Int)
@Entity("moment_items") data class MomentItem(@PrimaryKey val id: String, val momentId: String, val text: String, val sortOrder: Int)
@Entity("moment_runs") data class MomentRun(
    @PrimaryKey val id: String, val momentId: String, val date: String, val ts: Long,
    val done: Int, val total: Int, val unchecked: String, val note: String)
@Entity("sleep") data class SleepEntry(@PrimaryKey val id: String, val date: String, val start: Long, val end: Long, val minutes: Int)
@Entity("medicines") data class Medicine(
    @PrimaryKey val id: String, val name: String, val dose: String, val times: String,
    val snoozeMin: Int, val overrideQuiet: Boolean, val enabled: Boolean, val createdAt: Long)
@Entity("med_logs", primaryKeys = ["date","medId"]) data class MedLog(val date: String, val medId: String, val takenAt: Long?, val snoozedUntil: Long?)
@Entity("reviews") data class Review(
    @PrimaryKey val id: String, val week: String, val date: String, val headline: String, val pct: Int,
    val tasksDone: Int, val waterAvg: String, val sleepAvg: Int, val runs: Int, val best: Int, val createdAt: Long)
@Entity("saved_views") data class SavedView(@PrimaryKey val id: String, val name: String, val filter: String, val place: String, val q: String, val sort: String)
@Entity("active") data class Active(@PrimaryKey val id: String = "active", val momentId: String, val startedAt: Long, val checked: String, val note: String)
@Entity("notified") data class Notified(
    @PrimaryKey @ColumnInfo(name = "nkey") val key: String,
    val ts: Long
)
@Entity("snoozes") data class Snooze(
    @PrimaryKey @ColumnInfo(name = "skey") val key: String,
    @ColumnInfo(name = "untilTs") val until: Long
)

@androidx.room.Dao interface Dao {
    @Query("SELECT * FROM habits ORDER BY sortOrder") fun habits(): Flow<List<Habit>>
    @Query("SELECT * FROM habits ORDER BY sortOrder") suspend fun habitsOnce(): List<Habit>
    @Query("SELECT * FROM habits WHERE id=:id") suspend fun habit(id: String): Habit?
    @Upsert suspend fun putHabit(h: Habit)
    @Query("DELETE FROM habits WHERE id=:id") suspend fun delHabit(id: String)

    @Query("SELECT * FROM habit_logs") fun logs(): Flow<List<HabitLog>>
    @Query("SELECT * FROM habit_logs WHERE date=:d AND habitId=:h") suspend fun log(d: String, h: String): HabitLog?
    @Query("SELECT * FROM habit_logs WHERE habitId=:h") suspend fun logsFor(h: String): List<HabitLog>
    @Upsert suspend fun putLog(l: HabitLog)

    @Query("SELECT * FROM tasks ORDER BY sortOrder") fun tasks(): Flow<List<Task>>
    @Query("SELECT * FROM tasks ORDER BY sortOrder") suspend fun tasksOnce(): List<Task>
    @Query("SELECT * FROM tasks WHERE id=:id") suspend fun task(id: String): Task?
    @Upsert suspend fun putTask(t: Task)
    @Query("DELETE FROM tasks WHERE id=:id") suspend fun delTask(id: String)

    @Query("SELECT * FROM task_items ORDER BY sortOrder") fun items(): Flow<List<TaskItem>>
    @Query("SELECT * FROM task_items ORDER BY sortOrder") suspend fun itemsOnce(): List<TaskItem>
    @Upsert suspend fun putItem(i: TaskItem)
    @Query("DELETE FROM task_items WHERE id=:id") suspend fun delItem(id: String)
    @Query("DELETE FROM task_items WHERE taskId=:id") suspend fun delItemsOf(id: String)

    @Query("SELECT * FROM moments ORDER BY sortOrder") fun moments(): Flow<List<Moment>>
    @Query("SELECT * FROM moments ORDER BY sortOrder") suspend fun momentsOnce(): List<Moment>
    @Query("SELECT * FROM moments WHERE id=:id") suspend fun moment(id: String): Moment?
    @Upsert suspend fun putMoment(m: Moment)
    @Query("DELETE FROM moments WHERE id=:id") suspend fun delMoment(id: String)

    @Query("SELECT * FROM moment_items ORDER BY sortOrder") fun mitems(): Flow<List<MomentItem>>
    @Query("SELECT * FROM moment_items ORDER BY sortOrder") suspend fun mitemsOnce(): List<MomentItem>
    @Upsert suspend fun putMItem(i: MomentItem)
    @Query("DELETE FROM moment_items WHERE id=:id") suspend fun delMItem(id: String)
    @Query("DELETE FROM moment_items WHERE momentId=:id") suspend fun delMItemsOf(id: String)

    @Query("SELECT * FROM moment_runs ORDER BY ts DESC") fun runs(): Flow<List<MomentRun>>
    @Query("SELECT * FROM moment_runs ORDER BY ts DESC") suspend fun runsOnce(): List<MomentRun>
    @Upsert suspend fun putRun(r: MomentRun)

    @Query("SELECT * FROM sleep ORDER BY end DESC") fun sleeps(): Flow<List<SleepEntry>>
    @Query("SELECT * FROM sleep ORDER BY end DESC") suspend fun sleepsOnce(): List<SleepEntry>
    @Upsert suspend fun putSleep(s: SleepEntry)

    @Query("SELECT * FROM medicines ORDER BY createdAt") fun meds(): Flow<List<Medicine>>
    @Query("SELECT * FROM medicines ORDER BY createdAt") suspend fun medsOnce(): List<Medicine>
    @Upsert suspend fun putMed(m: Medicine)
    @Query("DELETE FROM medicines WHERE id=:id") suspend fun delMed(id: String)

    @Query("SELECT * FROM med_logs") fun medLogs(): Flow<List<MedLog>>
    @Query("SELECT * FROM med_logs WHERE date=:d AND medId=:m") suspend fun medLog(d: String, m: String): MedLog?
    @Upsert suspend fun putMedLog(l: MedLog)

    @Query("SELECT * FROM reviews ORDER BY createdAt DESC") fun reviews(): Flow<List<Review>>
    @Upsert suspend fun putReview(r: Review)
    @Query("SELECT * FROM reviews") suspend fun reviewsOnce(): List<Review>

    @Query("SELECT * FROM saved_views") fun views(): Flow<List<SavedView>>
    @Query("SELECT * FROM saved_views") suspend fun viewsOnce(): List<SavedView>
    @Upsert suspend fun putView(v: SavedView)
    @Query("DELETE FROM saved_views WHERE id=:id") suspend fun delView(id: String)

    @Query("SELECT * FROM active WHERE id='active'") fun active(): Flow<Active?>
    @Query("SELECT * FROM active WHERE id='active'") suspend fun activeOnce(): Active?
    @Upsert suspend fun putActive(a: Active)
    @Query("DELETE FROM active") suspend fun clearActive()

    @Query("SELECT * FROM notified") suspend fun notified(): List<Notified>
    @Upsert suspend fun putNotified(n: Notified)
    @Query("DELETE FROM notified WHERE nkey = :k") suspend fun delNotified(k: String)
    @Query("DELETE FROM notified") suspend fun wipeNotified()
    @Query("SELECT * FROM snoozes") suspend fun snoozes(): List<Snooze>
    @Upsert suspend fun putSnooze(s: Snooze)
    @Query("DELETE FROM snoozes WHERE skey = :k") suspend fun delSnooze(k: String)
    @Query("DELETE FROM notified WHERE ts < :cut") suspend fun pruneNotified(cut: Long)
    @Query("DELETE FROM snoozes WHERE untilTs < :cut") suspend fun pruneSnoozes(cut: Long)
    @Query("SELECT COUNT(*) FROM habits") suspend fun count(): Int

    // ---- one-time removal of the old built-in sample data (see Repo.removeSampleData) ----
    @Query("SELECT * FROM habit_logs") suspend fun logsOnce(): List<HabitLog>
    @Query("DELETE FROM habit_logs WHERE date < :d") suspend fun delLogsBefore(d: String)
    @Query("DELETE FROM habit_logs WHERE date = :d AND habitId = :h") suspend fun delLog(d: String, h: String)
    @Query("DELETE FROM habit_logs WHERE habitId = :h") suspend fun delLogsOf(h: String)
    @Query("DELETE FROM sleep WHERE date < :d") suspend fun delSleepsBefore(d: String)
    @Query("DELETE FROM moment_runs WHERE date < :d") suspend fun delRunsBefore(d: String)
    @Query("DELETE FROM med_logs WHERE medId = :id OR medId LIKE :id || '@%'") suspend fun delMedLogsOf(id: String)
    @Query("DELETE FROM reviews") suspend fun delReviews()
}

@Database(entities = [Habit::class, HabitLog::class, Task::class, TaskItem::class, Moment::class,
    MomentItem::class, MomentRun::class, SleepEntry::class, Medicine::class, MedLog::class,
    Review::class, SavedView::class, Active::class, Notified::class, Snooze::class],
    version = 1, exportSchema = false)
abstract class Db : RoomDatabase() {
    abstract fun dao(): Dao
    companion object {
        fun build(ctx: Context): Db =
            Room.databaseBuilder(ctx, Db::class.java, "dayflow.db").fallbackToDestructiveMigration(dropAllTables = true).build()
    }
}
