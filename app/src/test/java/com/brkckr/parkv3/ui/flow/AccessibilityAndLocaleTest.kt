package com.brkckr.parkv3.ui.flow

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.brkckr.parkv3.ui.main.MainTestTags
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brkckr.parkv3.domain.ParkFilters
import com.brkckr.parkv3.domain.ParkListQuery
import com.brkckr.parkv3.domain.model.GeoPoint
import com.brkckr.parkv3.domain.model.OpenState
import com.brkckr.parkv3.domain.model.SyncInfo
import com.brkckr.parkv3.testutil.park
import com.brkckr.parkv3.ui.main.MainActions
import com.brkckr.parkv3.ui.main.MainScreen
import com.brkckr.parkv3.ui.main.MainUiState
import com.brkckr.parkv3.ui.map.MapStatus
import com.brkckr.parkv3.ui.theme.ParkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Stateless screen checks: Turkish UI, very large font, labelled controls. */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class AccessibilityAndLocaleTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val parks = listOf(
        park(1, name = "Kadıköy Rıhtım Otoparkı", district = "KADIKÖY", location = GeoPoint(40.99, 29.02), emptyCapacity = 30),
        park(2, name = "Şişli Merkez Katlı", district = "ŞİŞLİ", openState = OpenState.CLOSED),
        park(3, name = "Beşiktaş Sahil", district = "BEŞİKTAŞ", openState = OpenState.UNKNOWN),
    )

    private fun state() = MainUiState(
        items = ParkListQuery.apply(parks, setOf(1), "", ParkFilters(), null),
        totalCount = parks.size,
        favoriteCount = 1,
        sync = SyncInfo(lastSuccessAtMillis = System.currentTimeMillis() - 120_000, lastAttemptAtMillis = System.currentTimeMillis()),
        isCacheLoaded = true,
        mapStatus = MapStatus.NO_API_KEY,
    )

    private fun render() = composeRule.setContent { ParkTheme { MainScreen(state(), MainActions()) } }

    @Test
    @Config(qualifiers = "tr-w411dp-h891dp")
    fun turkishLabelsAndStatusesAreShown() {
        render()

        composeRule.onNodeWithText("Boş yeri olan").assertIsDisplayed()
        composeRule.onNodeWithText("Kapalı (kaynağa göre)").assertIsDisplayed()
        composeRule.onNodeWithText("Durum bilinmiyor").assertIsDisplayed()
        composeRule.onNodeWithText("100 yerden 30 boş").assertIsDisplayed()
        composeRule.onNodeWithText("Son güncelleme", substring = true).assertIsDisplayed()
    }

    @Test
    fun largeFontKeepsContentAndTouchTargetsUsable() {
        RuntimeEnvironment.setFontScale(2.0f)
        render()

        // Filter chips wrap instead of being cut off; controls keep 48dp touch targets.
        composeRule.onNodeWithText("Has free spaces").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Refresh").assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
        composeRule.onNodeWithTag(MainTestTags.PARK_LIST).performScrollToNode(hasText("Kadıköy Rıhtım Otoparkı"))
        composeRule.onNodeWithText("Kadıköy Rıhtım Otoparkı").assertIsDisplayed()
        composeRule.onNodeWithTag(MainTestTags.favoriteToggle(1)).assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
    }

    @Test
    fun iconOnlyControlsHaveLabels() {
        render()

        composeRule.onAllNodesWithContentDescription("Refresh").onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithContentDescription("Add to favorites").onFirst().assert(hasClickAction())
        composeRule.onAllNodesWithContentDescription("Remove from favorites").onFirst().assert(hasClickAction())
    }
}
