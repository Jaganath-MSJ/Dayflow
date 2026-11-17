package com.dayflow.app

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.dayflow.app.data.Repo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain
        // POST_NOTIFICATIONS only exists on Android 13+; on Android 12 there is nothing to grant
        .outerRule(if (android.os.Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
            else org.junit.rules.TestRule { base, _ -> base })
        .around(compose)

    @Before fun reset() = runBlocking {
        Repo.init(androidx.test.core.app.ApplicationProvider.getApplicationContext())
        Repo.resetAll(); Repo.setWater(3)
    }

    private fun waitText(t: String) = compose.waitUntil(5_000) {
        compose.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    /** DB writes must be reflected on screen without any other UI interaction. */
    @Test fun addGlassUpdatesWaterCount() {
        waitText("+ Glass")
        compose.onNodeWithText("+ Glass").performClick()
        compose.waitUntil(5_000) { runBlocking { Repo.logVal(today(), "water") } == 4 }
        waitText("4")  // the counter on the water card
    }

    @Test fun allTabsRender() {
        listOf("Tasks" to "Notes & Checklists", "Moments" to "Moments & Places",
            "Stats" to "Your progress", "More" to "Settings", "Today" to "Today's habits").forEach { (tab, header) ->
            compose.onAllNodesWithText(tab).onLast().performClick()
            waitText(header)
        }
    }

    /** Regression: clearing the Goal field used to snap back to "1", so typing 5 produced 15. */
    @Test fun habitGoalFieldCanBeClearedAndRetyped() {
        waitText("+ New habit")
        compose.onNodeWithText("+ New habit").performScrollTo().performClick()
        waitText("New habit")
        // the Today screen's command box is also a text field, so address the sheet's fields from the end:
        // tick habit -> [.., Name]; count habit -> [.., Name, Goal, Unit]
        fun fields() = compose.onAllNodes(hasSetTextAction())
        fun count() = fields().fetchSemanticsNodes().size
        fields()[count() - 1].performTextInput("Pages")
        compose.onNodeWithText("Count", substring = true).performScrollTo().performClick()
        compose.waitForIdle()
        val goal = fields()[count() - 2]
        goal.assertTextEquals("1")   // tick -> count starts from the default goal
        goal.performTextClearance()
        goal.performTextInput("5")
        goal.assertTextEquals("5")
        androidx.test.espresso.Espresso.closeSoftKeyboard()   // the keyboard would cover Save
        compose.waitForIdle()
        compose.onNodeWithText("Save").performScrollTo().performClick()
        compose.waitUntil(5_000) { runBlocking { Repo.dao.habitsOnce().any { it.name == "Pages" } } }
        assertEquals(5, runBlocking { Repo.dao.habitsOnce().first { it.name == "Pages" }.goal })
    }

    @Test fun routineChipOpensRunnerAndCompletes() {
        waitText("Back home")
        compose.onAllNodesWithText("Back home").onFirst().performClick()
        waitText("Complete ✓")
        compose.onNodeWithText("Complete ✓").performClick()
        waitText("Complete anyway")
        compose.onNodeWithText("Complete anyway").performClick()
        compose.waitUntil(5_000) { runBlocking { Repo.dao.activeOnce() } == null }
    }
}
