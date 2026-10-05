package com.brkckr.parkv3.ui.flow

import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.printToString
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brkckr.parkv3.MainActivity
import com.brkckr.parkv3.testutil.FakeConnectivity
import com.brkckr.parkv3.testutil.Fixtures
import com.brkckr.parkv3.testutil.TestServer
import com.brkckr.parkv3.ui.main.MainTestTags
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Whole-app flows on Robolectric: real Compose UI, ViewModels, repository, Room (in memory)
 * and Retrofit/OkHttp against a local server serving SYNTHETIC fixtures. No location
 * permission and no Maps key are configured, so the list is the default view.
 */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class, qualifiers = "w411dp-h891dp")
class MainFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        TestServer.reset()
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    /**
     * Waits for [condition] while draining the main looper, so results posted from Room and
     * OkHttp threads reach the ViewModels (the Robolectric test thread is the main thread).
     * On timeout the failure shows what the screen actually contained.
     */
    private fun ComposeTestRule.waitShowingTree(what: String, condition: () -> Boolean) {
        try {
            waitUntil(timeoutMillis = 15_000) {
                ShadowLooper.idleMainLooper()
                condition()
            }
        } catch (e: ComposeTimeoutException) {
            val tree = runCatching { onRoot(useUnmergedTree = true).printToString() }.getOrElse { "<no tree: $it>" }
            throw AssertionError("Timed out waiting for $what. Screen:\n$tree", e)
        }
    }

    private fun ComposeTestRule.waitForText(text: String, substring: Boolean = false) =
        waitShowingTree("text '$text'") { onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeTestRule.waitForTag(tag: String) =
        waitShowingTree("tag '$tag'") { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeTestRule.waitForTagGone(tag: String) =
        waitShowingTree("tag '$tag' to disappear") { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isEmpty() }

    /** List loaded: rows are sorted by Turkish alphabet, so Beşiktaş (104) comes first. */
    private fun waitForList() = composeRule.waitForTag(MainTestTags.parkRow(104))

    private fun scrollToRow(id: Int) =
        composeRule.onNodeWithTag(MainTestTags.PARK_LIST).performScrollToNode(hasTestTag(MainTestTags.parkRow(id)))

    @Test
    fun firstLaunchOffline_explainsAndRetryLoadsTheList() {
        FakeConnectivity.offline = true
        TestServer.always("Park", TestServer.json(Fixtures.parkList))
        launch()

        composeRule.waitForText("No internet connection")
        composeRule.onNodeWithText("The car park list hasn't been downloaded yet", substring = true).assertIsDisplayed()

        FakeConnectivity.offline = false
        composeRule.onNodeWithText("Try again").performClick()

        waitForList()
        composeRule.onNodeWithText("Beşiktaş Sahil").assertIsDisplayed()
        composeRule.onNodeWithText("Data may be out of date.").assertDoesNotExist()
    }

    @Test
    fun cachedListStaysWhenARefreshFails() {
        TestServer.always("Park", TestServer.json(Fixtures.parkList))
        launch()
        waitForList()

        FakeConnectivity.offline = true
        composeRule.onNodeWithContentDescription("Refresh").performClick()

        composeRule.waitForText("You appear to be offline", substring = true)
        composeRule.onNodeWithTag(MainTestTags.parkRow(104)).assertIsDisplayed()
        composeRule.onNodeWithText("Data may be out of date.").assertIsDisplayed()
    }

    @Test
    fun httpErrorWithHostileBody_isShownAsSafeMessage() {
        TestServer.always("Park", TestServer.json("<html>%s %d %1\$s ${"x".repeat(5_000)}</html>", code = 503))
        launch()

        composeRule.waitForText("Couldn't reach the data source")
        composeRule.onNodeWithText("error 503", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("%s", substring = true).assertDoesNotExist()
    }

    @Test
    fun searchFiltersDetailAndFavorites_withoutLocationPermission() {
        TestServer.always("Park", TestServer.json(Fixtures.parkList))
        TestServer.always("ParkDetay", TestServer.json(Fixtures.parkDetail))
        launch()
        waitForList()

        // Turkish-aware search typed on a non-Turkish keyboard.
        composeRule.onNodeWithTag(MainTestTags.SEARCH).performTextInput("kadikoy")
        composeRule.waitForTagGone(MainTestTags.parkRow(104))
        composeRule.onNodeWithTag(MainTestTags.parkRow(101)).assertIsDisplayed()
        composeRule.onNodeWithTag(MainTestTags.SEARCH).performTextClearance()
        waitForList()

        // Combined filters: open AND has free spaces.
        composeRule.onNodeWithText("Open").performClick()
        composeRule.onNodeWithText("Has free spaces").performClick()
        composeRule.waitForTagGone(MainTestTags.parkRow(104)) // isOpen missing
        composeRule.onNodeWithTag(MainTestTags.parkRow(102)).assertDoesNotExist() // full
        composeRule.onNodeWithTag(MainTestTags.parkRow(103)).assertDoesNotExist() // explicitly closed
        composeRule.onNodeWithTag(MainTestTags.parkRow(105)).assertDoesNotExist() // inconsistent capacity
        composeRule.onNodeWithTag(MainTestTags.parkRow(106)).assertDoesNotExist() // occupancy not reported
        composeRule.onNodeWithTag(MainTestTags.parkRow(107)).assertIsDisplayed()
        composeRule.onNodeWithTag(MainTestTags.parkRow(101)).assertIsDisplayed()

        // Detail: address and tariff as published; favorite from the detail screen.
        composeRule.onNodeWithTag(MainTestTags.parkRow(101)).performClick()
        composeRule.waitForText("Rıhtım Cad. No:1 Kadıköy/İstanbul")
        composeRule.waitForIdle() // let the navigation transition finish before touching shared labels
        composeRule.onNodeWithText("0-1 Saat").assertExists()
        composeRule.onNodeWithContentDescription("Add to favorites").performClick()
        composeRule.onNodeWithContentDescription("Remove from favorites").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()

        // Favorites filter now combines with the others.
        composeRule.waitForTag(MainTestTags.parkRow(107))
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Favorites").performClick()
        composeRule.waitForTagGone(MainTestTags.parkRow(107))
        composeRule.onNodeWithTag(MainTestTags.parkRow(101)).assertIsDisplayed()
    }

    @Test
    fun detailFirstLoadFailure_thenRetrySucceeds() {
        TestServer.always("Park", TestServer.json(Fixtures.parkList))
        // The first detail request fails, the retry succeeds.
        TestServer.enqueue("ParkDetay", TestServer.json("oops", code = 500))
        TestServer.always("ParkDetay", TestServer.json(Fixtures.parkDetail))
        launch()
        waitForList()

        scrollToRow(101)
        composeRule.onNodeWithTag(MainTestTags.parkRow(101)).performClick()
        composeRule.waitForText("Showing list data only", substring = true)

        composeRule.onNodeWithText("Try again").performClick()

        composeRule.waitForText("Rıhtım Cad. No:1 Kadıköy/İstanbul")
    }

    @Test
    fun noFavoritesYet_hasItsOwnEmptyScreen() {
        TestServer.always("Park", TestServer.json(Fixtures.parkList))
        launch()
        waitForList()

        composeRule.onNodeWithText("Favorites").performClick()

        composeRule.waitForText("No favorites yet")
    }

    @Test
    fun searchWithoutMatches_hasItsOwnEmptyScreen() {
        TestServer.always("Park", TestServer.json(Fixtures.parkList))
        launch()
        waitForList()

        composeRule.onNodeWithTag(MainTestTags.SEARCH).performTextInput("zzzz")

        composeRule.waitForText("No results for “zzzz”")
    }
}
