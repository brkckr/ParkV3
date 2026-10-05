package com.brkckr.parkv3.ui.flow

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brkckr.parkv3.domain.ParkFilters
import com.brkckr.parkv3.domain.ParkListQuery
import com.brkckr.parkv3.domain.model.GeoPoint
import com.brkckr.parkv3.domain.model.SyncInfo
import com.brkckr.parkv3.testutil.park
import com.brkckr.parkv3.ui.main.MainActions
import com.brkckr.parkv3.ui.main.MainScreen
import com.brkckr.parkv3.ui.main.MainTestTags
import com.brkckr.parkv3.ui.main.MainUiState
import com.brkckr.parkv3.ui.main.ViewMode
import com.brkckr.parkv3.ui.map.MapStatus
import com.brkckr.parkv3.ui.theme.ParkTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Phone and tablet layouts of the main screen. Robolectric cannot draw a Google map, so a
 * stand-in fills the map slot; what is checked is which panes are shown and the toggle.
 */
@RunWith(AndroidJUnit4::class)
class AdaptiveLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val parks = listOf(
        park(1, name = "Kadıköy Rıhtım Otoparkı", district = "KADIKÖY", location = GeoPoint(40.99, 29.02)),
        park(2, name = "Şişli Merkez Katlı", district = "ŞİŞLİ", location = GeoPoint(41.06, 28.99)),
    )

    private fun state(mapStatus: MapStatus = MapStatus.AVAILABLE, viewMode: ViewMode = ViewMode.LIST) = MainUiState(
        items = ParkListQuery.apply(parks, emptySet(), "", ParkFilters(), null),
        totalCount = parks.size,
        sync = SyncInfo(lastSuccessAtMillis = System.currentTimeMillis()),
        isCacheLoaded = true,
        mapStatus = mapStatus,
        viewMode = viewMode,
    )

    private fun render(state: MainUiState) = composeRule.setContent {
        ParkTheme {
            MainScreen(state, MainActions(), mapPane = { modifier -> Box(modifier.fillMaxSize().testTag(MAP_STAND_IN)) })
        }
    }

    @Test
    @Config(qualifiers = "w1000dp-h700dp")
    fun tabletShowsListAndMapSideBySideWithoutTheToggle() {
        render(state())

        composeRule.onNodeWithTag(MainTestTags.PARK_LIST).assertIsDisplayed()
        composeRule.onNodeWithTag(MAP_STAND_IN).assertIsDisplayed()
        composeRule.onNodeWithText("Map").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w1000dp-h700dp")
    fun tabletWithoutAWorkingMapShowsTheListAlone() {
        render(state(mapStatus = MapStatus.NO_PLAY_SERVICES))

        composeRule.onNodeWithTag(MainTestTags.PARK_LIST).assertIsDisplayed()
        composeRule.onNodeWithTag(MAP_STAND_IN).assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun phoneShowsOnePaneAndTheToggle() {
        render(state(viewMode = ViewMode.LIST))

        composeRule.onNodeWithTag(MainTestTags.PARK_LIST).assertIsDisplayed()
        composeRule.onNodeWithTag(MAP_STAND_IN).assertDoesNotExist()
        composeRule.onNodeWithText("Map").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w411dp-h891dp")
    fun phoneInMapModeShowsOnlyTheMap() {
        render(state(viewMode = ViewMode.MAP))

        composeRule.onNodeWithTag(MAP_STAND_IN).assertIsDisplayed()
        composeRule.onNodeWithTag(MainTestTags.PARK_LIST).assertDoesNotExist()
    }

    private companion object {
        const val MAP_STAND_IN = "map_stand_in"
    }
}
