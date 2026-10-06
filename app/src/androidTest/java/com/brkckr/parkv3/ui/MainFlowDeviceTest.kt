package com.brkckr.parkv3.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brkckr.parkv3.MainActivity
import com.brkckr.parkv3.testutil.Fixtures
import com.brkckr.parkv3.testutil.TestServer
import com.brkckr.parkv3.ui.main.MainTestTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Main flow on a real device/emulator against a local fake server serving SYNTHETIC
 * fixtures (never the live API). Run: ./gradlew connectedDebugAndroidTest
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainFlowDeviceTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        TestServer.reset()
        TestServer.always("Park", TestServer.json(Fixtures.parkList))
        TestServer.always("ParkDetay", TestServer.json(Fixtures.parkDetail))
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    @Test
    fun listSearchDetailFavoriteAndBack() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        // Sorted by Turkish alphabet: Beşiktaş (104) is the first row.
        composeRule.waitUntil(15_000) { composeRule.onAllNodes(hasTestTag(MainTestTags.parkRow(104))).fetchSemanticsNodes().isNotEmpty() }

        composeRule.onNodeWithTag(MainTestTags.SEARCH).performTextInput("kadikoy")
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasTestTag(MainTestTags.parkRow(104))).fetchSemanticsNodes().isEmpty() }

        composeRule.onNodeWithTag(MainTestTags.parkRow(101)).performClick()
        composeRule.waitUntil(15_000) { composeRule.onAllNodes(hasText("Rıhtım Cad. No:1 Kadıköy/İstanbul")).fetchSemanticsNodes().isNotEmpty() }
        composeRule.waitForIdle()

        composeRule.onNodeWithContentDescription("Add to favorites").performClick()
        // The write goes through Room on a background thread before the toggle flips; Compose's
        // idling does not wait for it.
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasContentDescription("Remove from favorites")).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithContentDescription("Remove from favorites").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Favorites").performClick()
        composeRule.onNodeWithTag(MainTestTags.parkRow(101)).assertIsDisplayed()
    }
}
