package com.brkckr.parkv3.ui.flow

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextClearance
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brkckr.parkv3.MainActivity
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

    private fun ComposeTestRule.waitForText(text: String, substring: Boolean = false) =
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }

    private fun ComposeTestRule.waitForTag(tag: String) =
        waitUntil(timeoutMillis = 10_000) { onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty() }

    @Test
    fun firstLaunchOffline_explainsAndRetryLoadsTheList() {
        // Every attempt fails until the "network" comes back (robust to OkHttp's own retry).
        TestServer.always("Park", TestServer.disconnect())
        launch()

        composeRule.waitForText("No internet connection")
        composeRule.onNodeWithText("The car park list hasn't been downloaded yet", substring = true).assertIsDisplayed()

        TestServer.always("Park", TestServer.json(Fixtures.parkList))
        composeRule.onNodeWithText("Try again").performClick()

        composeRule.waitForTag(MainTestTags.parkRow(101))
        composeRule.onNodeWithText("Kadıköy Rıhtım Otoparkı").assertIsDisplayed()
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
        composeRule.waitForTag(MainTestTags.parkRow(101))

        // Turkish-aware search typed on a non-Turkish keyboard.
        composeRule.onNodeWithTag(MainTestTags.SEARCH).performTextInput("kadikoy")
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasTestTag(MainTestTags.parkRow(103))).fetchSemanticsNodes().isEmpty() }
        composeRule.onNodeWithTag(MainTestTags.parkRow(101)).assertIsDisplayed()
        composeRule.onNodeWithTag(MainTestTags.SEARCH).performTextClearance()

        // Combined filters: open AND has free spaces.
        composeRule.onNodeWithText("Open").performClick()
        composeRule.onNodeWithText("Has free spaces").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasTestTag(MainTestTags.parkRow(102))).fetchSemanticsNodes().isEmpty() }
        composeRule.onNodeWithTag(MainTestTags.parkRow(103)).assertDoesNotExist() // explicitly closed
        composeRule.onNodeWithTag(MainTestTags.parkRow(104)).assertDoesNotExist() // isOpen missing
        composeRule.onNodeWithTag(MainTestTags.parkRow(105)).assertDoesNotExist() // inconsistent capacity
        composeRule.onNodeWithTag(MainTestTags.parkRow(101)).assertIsDisplayed()
        composeRule.onNodeWithTag(MainTestTags.PARK_LIST).performScrollToNode(hasTestTag(MainTestTags.parkRow(107)))

        // Detail: address and tariff as published; favorite from the detail screen.
        composeRule.onNodeWithTag(MainTestTags.parkRow(101)).performClick()
        composeRule.waitForText("Rıhtım Cad. No:1 Kadıköy/İstanbul")
        composeRule.onNodeWithText("0-1 Saat").assertExists()
        composeRule.onNodeWithContentDescription("Add to favorites").performClick()
        composeRule.onNodeWithContentDescription("Remove from favorites").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back").performClick()

        // Favorites filter now combines with the others.
        composeRule.waitForTag(MainTestTags.SEARCH)
        composeRule.onNodeWithText("Favorites").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodes(hasTestTag(MainTestTags.parkRow(107))).fetchSemanticsNodes().isEmpty() }
        composeRule.onNodeWithTag(MainTestTags.parkRow(101)).assertIsDisplayed()
    }

    @Test
    fun detailFirstLoadFailure_thenRetrySucceeds() {
        TestServer.always("Park", TestServer.json(Fixtures.parkList))
        // The first detail request fails, the retry succeeds.
        TestServer.enqueue("ParkDetay", TestServer.json("oops", code = 500))
        TestServer.always("ParkDetay", TestServer.json(Fixtures.parkDetail))
        launch()
        composeRule.waitForTag(MainTestTags.parkRow(101))

        composeRule.onNodeWithTag(MainTestTags.parkRow(101)).performClick()
        composeRule.waitForText("Showing list data only", substring = true)

        composeRule.onNodeWithText("Try again").performClick()

        composeRule.waitForText("Rıhtım Cad. No:1 Kadıköy/İstanbul")
    }

    @Test
    fun noFavoritesYet_hasItsOwnEmptyScreen() {
        TestServer.always("Park", TestServer.json(Fixtures.parkList))
        launch()
        composeRule.waitForTag(MainTestTags.parkRow(101))

        composeRule.onNodeWithText("Favorites").performClick()

        composeRule.waitForText("No favorites yet")
    }

    @Test
    fun searchWithoutMatches_hasItsOwnEmptyScreen() {
        TestServer.always("Park", TestServer.json(Fixtures.parkList))
        launch()
        composeRule.waitForTag(MainTestTags.parkRow(101))

        composeRule.onNodeWithTag(MainTestTags.SEARCH).performTextInput("zzzz")

        composeRule.waitForText("No results for “zzzz”")
    }
}
