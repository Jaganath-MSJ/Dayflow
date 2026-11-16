package com.dayflow.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dayflow.app.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The app must never show demo data: fresh installs start clean, and old installs have their demo data removed. */
@RunWith(AndroidJUnit4::class)
class SampleDataTest {
    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val dao get() = Repo.dao

    @Before fun reset() = runBlocking { Repo.init(ctx); Repo.resetAll() }

    @Test fun freshInstallHasOnlyEssentialsAndNoActivity() = runBlocking {
        assertEquals(listOf("water"), dao.habitsOnce().map { it.id })
        assertEquals(setOf("m-wake", "m-office", "m-home", "m-sleep"), dao.momentsOnce().map { it.id }.toSet())
        assertTrue(dao.momentsOnce().none { it.type == "place" })
        assertTrue(dao.logsOnce().isEmpty()); assertTrue(dao.sleepsOnce().isEmpty()); assertTrue(dao.runsOnce().isEmpty())
        assertTrue(dao.tasksOnce().isEmpty()); assertTrue(dao.medsOnce().isEmpty()); assertTrue(dao.viewsOnce().isEmpty())
        assertTrue(dao.reviewsOnce().isEmpty())
        // derived stats are therefore all zero
        assertEquals(0, Repo.logVal(today(), "water"))
        assertEquals(0, Repo.streak(dao.habit("water")!!))
        assertEquals("0% habits · 0 tasks done · 0h 00m avg sleep", Repo.generateReview().headline)
    }

    @Test fun cleanupRemovesDemoButKeepsUserDataInstalledToday() = runBlocking {
        Repo.dao.run { clearForTest() }
        TestData.legacySeed(installOffset = 0)
        // what the user did after install
        Repo.addWater(2)                                     // 3 demo + 2 real = 5
        dao.putLog(HabitLog(today(), "read", 4))             // used a demo habit -> keep it
        dao.putTask(Task("u1", "My task", "note", null, null, null, "none", true, false, null, nowMs(), 9))

        Repo.removeSampleData()

        assertTrue("no fake history", dao.logsOnce().none { it.date < today() })
        assertTrue(dao.sleepsOnce().isEmpty()); assertTrue(dao.runsOnce().isEmpty())
        assertEquals("only the user's 2 glasses remain", 2, Repo.logVal(today(), "water"))
        assertEquals(0, Repo.logVal(today(), "walk")); assertEquals(0, Repo.logVal(today(), "meditate"))
        assertEquals(setOf("water", "read"), dao.habitsOnce().map { it.id }.toSet())
        assertEquals(listOf("My task"), dao.tasksOnce().map { it.title })
        assertTrue("demo checklist items gone", dao.itemsOnce().isEmpty())
        assertEquals(setOf("m-wake", "m-office", "m-home", "m-sleep"), dao.momentsOnce().map { it.id }.toSet())
        assertTrue(dao.mitemsOnce().none { it.momentId in setOf("m-market", "m-gym", "m-pharmacy") })
        assertTrue(dao.medsOnce().isEmpty()); assertTrue(dao.viewsOnce().isEmpty())
    }

    @Test fun cleanupKeepsUserActivityFromAfterAnEarlierInstall() = runBlocking {
        Repo.dao.run { clearForTest() }
        TestData.legacySeed(installOffset = -3)
        val yesterday = dstr(addDays(-1))
        dao.putLog(HabitLog(yesterday, "water", 6))                       // real entry after install
        dao.putSleep(SleepEntry("s-real", yesterday, nowMs() - 7 * 3_600_000L - 1234, nowMs() - 1234, 420))
        dao.putMoment(dao.moment("m-gym")!!.copy(name = "My Gym"))       // user renamed a demo place -> keep
        dao.putTask(Task("u2", "Gym bag", "note", null, null, "m-gym", "none", false, false, null, nowMs(), 9))

        Repo.removeSampleData()

        val installDay = dstr(addDays(-3))
        assertTrue(dao.logsOnce().none { it.date < installDay })
        assertEquals(6, Repo.logVal(yesterday, "water"))
        assertEquals(listOf("s-real"), dao.sleepsOnce().map { it.id })
        assertNotNull("renamed place kept", dao.moment("m-gym"))
        assertNull(dao.moment("m-market")); assertNull(dao.moment("m-pharmacy"))
        assertEquals(listOf("Gym bag"), dao.tasksOnce().map { it.title })
    }

    @Test fun cleanupLeavesCleanInstallsAlone() = runBlocking {
        // a real sleep that happens to last one of the demo durations must survive (end-start isn't exact)
        dao.putSleep(SleepEntry("s1", dstr(addDays(-10)), 1_000_123L, 1_000_123L + 407 * 60_000L + 999, 407))
        dao.putLog(HabitLog(dstr(addDays(-10)), "water", 4))
        Repo.removeSampleData()
        assertEquals(1, dao.sleepsOnce().size); assertEquals(4, Repo.logVal(dstr(addDays(-10)), "water"))
        assertEquals(4, dao.momentsOnce().size)
    }
}

/** Empties every table (unlike resetAll, which re-adds the essentials) so a legacy install can be recreated. */
private suspend fun Dao.clearForTest() {
    habitsOnce().forEach { delHabit(it.id); delLogsOf(it.id) }
    momentsOnce().forEach { delMoment(it.id); delMItemsOf(it.id) }
    tasksOnce().forEach { delTask(it.id); delItemsOf(it.id) }
    delLogsBefore("9999"); delSleepsBefore("9999"); delRunsBefore("9999")
}
