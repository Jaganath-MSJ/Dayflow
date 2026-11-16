package com.dayflow.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dayflow.app.data.*
import com.dayflow.app.notify.runReminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against the app's real Room DB. Each test starts from a reset app plus the TestData fixture. */
@RunWith(AndroidJUnit4::class)
class RepoTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()

    @Before fun reset() = runBlocking { Repo.init(ctx); Repo.resetAll(); TestData.legacySeed() }

    // ---- fixture sanity ----
    @Test fun fixtureCreatesExpectedData() = runBlocking {
        assertEquals(6, Repo.dao.habitsOnce().size)
        assertEquals(7, Repo.dao.momentsOnce().size)
        assertEquals(5, Repo.dao.tasksOnce().size)
        assertEquals(3, Repo.logVal(today(), "water"))
    }

    // ---- water / habits ----
    @Test fun waterIsClampedToGoal() = runBlocking {
        Repo.setWater(100); assertEquals(8, Repo.logVal(today(), "water"))
        Repo.addWater(-50); assertEquals(0, Repo.logVal(today(), "water"))
    }

    @Test fun tickHabitToggles() = runBlocking {
        Repo.dao.putLog(HabitLog(today(), "gym", 0))
        Repo.tickHabit("gym"); assertEquals(1, Repo.logVal(today(), "gym"))
        Repo.tickHabit("gym"); assertEquals(0, Repo.logVal(today(), "gym"))
    }

    @Test fun streakCountsConsecutiveDaysAndSkipsUnfinishedToday() = runBlocking {
        val h = Habit("t1", "T", "✨", "#FFB347", "tick", 1, "", "0|1|2|3|4|5|6", null, false, 99)
        Repo.dao.putHabit(h)
        Repo.dao.putLog(HabitLog(dstr(addDays(-1)), "t1", 1))
        Repo.dao.putLog(HabitLog(dstr(addDays(-2)), "t1", 1))
        assertEquals(2, Repo.streak(h))
        Repo.dao.putLog(HabitLog(today(), "t1", 1))
        assertEquals(3, Repo.streak(h))
    }

    // ---- tasks / rollDay ----
    @Test fun rollDayKeepsDailyTaskCompletedToday() = runBlocking {
        val call = Repo.dao.tasksOnce().first { it.repeat == "daily" }
        Repo.toggleTask(call.id)
        Repo.rollDay()   // runs on every app start and every reminder tick
        assertTrue("daily task completed today must stay done", Repo.dao.task(call.id)!!.done)
    }

    @Test fun rollDayResetsDailyTaskCompletedYesterday() = runBlocking {
        val call = Repo.dao.tasksOnce().first { it.repeat == "daily" }
        Repo.dao.putTask(call.copy(done = true, doneAt = addDays(-1).timeInMillis))
        Repo.rollDay()
        val t = Repo.dao.task(call.id)!!
        assertFalse(t.done); assertNull(t.doneAt); assertEquals(today(), t.date)
    }

    @Test fun rollDayDoesNotTouchNonRepeatingTasks() = runBlocking {
        val x = Repo.dao.tasksOnce().first { it.repeat == "none" }
        Repo.dao.putTask(x.copy(done = true, doneAt = addDays(-3).timeInMillis))
        Repo.rollDay()
        assertTrue(Repo.dao.task(x.id)!!.done)
    }

    // ---- snoozes (query fixed during build repair) ----
    @Test fun snoozeRoundTrip() = runBlocking {
        Repo.snooze("k1", 10)
        val s = Repo.dao.snoozes().single { it.key == "k1" }
        assertTrue(s.until > nowMs() + 9 * 60_000L)
    }

    // ---- medicine ----
    @Test fun medDueSkipsTakenAndToleratesMalformedTimes() = runBlocking {
        Repo.dao.putMed(Medicine("m1", "Vit", "1", "00:00|9am|7", 10, false, true, nowMs()))
        val due = Repo.medDue()   // must not throw
        assertEquals(listOf("00:00"), due.filter { it.first.id == "m1" }.map { it.second })
        Repo.markTaken("m1", "00:00")
        assertTrue(Repo.medDue().none { it.first.id == "m1" })
    }

    @Test fun runRemindersToleratesMalformedTimes() = runBlocking {
        Repo.dao.putMed(Medicine("m2", "Bad", "1", "9am", 10, false, true, nowMs()))
        Repo.dao.putTask(Task("bt", "bad", "note", null, "noon", null, "none", true, false, null, nowMs(), 50))
        runReminders(ctx)  // must not throw
    }

    @Test fun nextWaterHandlesBadPrefs() = runBlocking {
        Repo.prefs.waterStart("oops")
        try { Repo.nextWater() } finally { Repo.prefs.waterStart("08:00") }
        Unit
    }

    // ---- commands ----
    @Test fun parseStartsRoutineByAlias() = runBlocking {
        assertEquals(Cmd.Start("m-wake"), Commands.parse("I woke up"))
        assertEquals(Cmd.Start("m-sleep"), Commands.parse("going to bed"))
    }

    @Test fun parseStartsPlace() = runBlocking {
        assertEquals(Cmd.Start("m-market"), Commands.parse("going to market"))
        assertEquals(Cmd.Start("m-pharmacy"), Commands.parse("heading to the pharmacy"))
    }

    @Test fun parseWater() = runBlocking {
        assertEquals(Cmd.Water, Commands.parse("drank a glass of water"))
    }

    @Test fun parseChecklistAddsOnlyNewItems() = runBlocking {
        val r = Commands.parse("checklist office: Umbrella, ID card")
        assertEquals(Cmd.Added(1, "Before office"), r)
        assertTrue(Repo.dao.mitemsOnce().any { it.momentId == "m-office" && it.text == "Umbrella" })
    }

    @Test fun parseChecklistForUnknownPlaceCreates() = runBlocking {
        assertEquals(Cmd.Create("Airport", listOf("Passport", "Tickets")), Commands.parse("checklist airport: Passport, Tickets"))
    }

    @Test fun parseUnknownPlaceCreates() = runBlocking {
        assertEquals(Cmd.Create("Airport", emptyList()), Commands.parse("going to airport"))
    }

    @Test fun parseDoesNotMatchToInsideWords() = runBlocking {
        // "to"/"at" must be whole words, not the tail of "photo" / "what"
        assertEquals(Cmd.Unknown("photo booth"), Commands.parse("photo booth"))
        assertEquals(Cmd.Unknown("what ever"), Commands.parse("what ever"))
    }

    // ---- moments ----
    @Test fun runMomentRecordsUncheckedItems() = runBlocking {
        Repo.startMoment("m-gym")
        val first = Repo.dao.mitemsOnce().filter { it.momentId == "m-gym" }.minBy { it.sortOrder }
        Repo.setChecked(first.id, true)
        Repo.finishMoment()
        assertNull(Repo.dao.activeOnce())
        val run = Repo.dao.runsOnce().first { it.momentId == "m-gym" }
        assertEquals(1, run.done); assertEquals(4, run.total)
        assertEquals(3, uncsv(run.unchecked).size)
    }

    @Test fun sleepThenWakeRecordsSleep() = runBlocking {
        val before = Repo.dao.sleepsOnce().size
        Repo.startMoment("m-sleep"); assertTrue(Repo.p().sleeping)
        Repo.startMoment("m-wake"); assertFalse(Repo.p().sleeping)
        assertEquals(before + 1, Repo.dao.sleepsOnce().size)
    }

    @Test fun generateReviewProducesHeadline() = runBlocking {
        val r = Repo.generateReview()
        assertTrue(r.headline.contains("% habits"))
        assertTrue(r.pct in 0..100)
    }

    // ---- regressions from code review ----
    @Test fun resetAllWorksFromMainThread() = runBlocking {
        withContext(Dispatchers.Main) { Repo.resetAll() }  // used to throw "Cannot access database on the main thread"
        assertEquals(listOf("water"), Repo.dao.habitsOnce().map { it.id })   // reset = clean app, no demo data
    }

    @Test fun resetAllAlsoResetsPrefs() = runBlocking {
        Repo.prefs.waterGoal(12); Repo.prefs.sleeping(true, nowMs())
        Repo.resetAll()
        assertEquals(8, Repo.p().waterGoal); assertFalse(Repo.p().sleeping)
    }

    @Test fun takingOneDoseKeepsOtherDosesDue() = runBlocking {
        Repo.dao.putMed(Medicine("m3", "Twice", "1", "00:00|00:01", 10, false, true, nowMs()))
        Repo.markTaken("m3", "00:00")
        val due = Repo.medDue().filter { it.first.id == "m3" }.map { it.second }
        if (nowMin() >= 1) assertEquals(listOf("00:01"), due)
    }

    @Test fun snoozedDoseIsNotDue() = runBlocking {
        Repo.dao.putMed(Medicine("m4", "Snz", "1", "00:00", 10, false, true, nowMs()))
        Repo.snooze(Repo.medNotifKey("m4", today(), "00:00"), 10)
        assertTrue(Repo.medDue().none { it.first.id == "m4" })
    }

    @Test fun completeHabitNeverWipesProgress() = runBlocking {
        Repo.dao.putLog(HabitLog(today(), "read", 5))
        Repo.completeHabit("read"); assertEquals(20, Repo.logVal(today(), "read"))
        Repo.completeHabit("read"); assertEquals(20, Repo.logVal(today(), "read"))
    }

    @Test fun completeTaskIsIdempotent() = runBlocking {
        val x = Repo.dao.tasksOnce().first()
        Repo.completeTask(x.id); Repo.completeTask(x.id)
        assertTrue(Repo.dao.task(x.id)!!.done)
    }

    @Test fun rollDayCarriesMissedDailyTaskForward() = runBlocking {
        val call = Repo.dao.tasksOnce().first { it.repeat == "daily" }
        Repo.dao.putTask(call.copy(done = false, doneAt = null, date = dstr(addDays(-2))))
        Repo.rollDay()
        assertEquals(today(), Repo.dao.task(call.id)!!.date)
    }

    @Test fun regeneratingReviewReplacesThisWeeksRow() = runBlocking {
        Repo.generateReview(); Repo.generateReview()
        assertEquals(1, Repo.dao.reviewsOnce().size)
    }

    @Test fun nonWaterPhrasesDoNotLogWater() = runBlocking {
        assertNotEquals(Cmd.Water, Commands.parse("took my vitamins"))
        assertEquals(Cmd.Water, Commands.parse("taken water"))
    }

    @Test fun streakMatchesAfterSingleQueryRewrite() = runBlocking {
        val h = Repo.dao.habit("walk")!!
        Repo.dao.putLog(HabitLog(today(), "walk", 1))
        assertTrue(Repo.streak(h) >= 1)
    }
}
