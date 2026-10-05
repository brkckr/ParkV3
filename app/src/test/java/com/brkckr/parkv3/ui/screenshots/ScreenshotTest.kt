package com.brkckr.parkv3.ui.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brkckr.parkv3.domain.ParkFilters
import com.brkckr.parkv3.domain.ParkListQuery
import com.brkckr.parkv3.domain.model.Clock
import com.brkckr.parkv3.domain.model.GeoPoint
import com.brkckr.parkv3.domain.model.OpenState
import com.brkckr.parkv3.domain.model.ParkDetail
import com.brkckr.parkv3.domain.model.RefreshError
import com.brkckr.parkv3.domain.model.SourceTimestamp
import com.brkckr.parkv3.domain.model.SyncInfo
import com.brkckr.parkv3.domain.model.TariffLine
import com.brkckr.parkv3.testutil.park
import com.brkckr.parkv3.ui.components.LocalClock
import com.brkckr.parkv3.ui.detail.DetailScreen
import com.brkckr.parkv3.ui.detail.DetailUiState
import com.brkckr.parkv3.ui.main.MainActions
import com.brkckr.parkv3.ui.main.MainScreen
import com.brkckr.parkv3.ui.main.MainUiState
import com.brkckr.parkv3.ui.map.MapStatus
import com.brkckr.parkv3.ui.theme.ParkTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone

/**
 * Screenshots of the main states (docs/TESTING.md). Captures happen only when Roborazzi
 * records or verifies; in the normal unit test run these tests just compose the screens.
 * Clock and time zone are fixed so relative times and the freshness band never change.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = PHONE)
class ScreenshotTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val defaultTimeZone = TimeZone.getDefault()

    @Before
    fun fixTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Istanbul"))
    }

    @After
    fun restoreTimeZone() {
        TimeZone.setDefault(defaultTimeZone)
    }

    private val parks = listOf(
        park(1, name = "Kadıköy Rıhtım Otoparkı", district = "KADIKÖY", location = GeoPoint(40.9903, 29.0236), emptyCapacity = 30),
        park(2, name = "Moda Sahil Açık Otoparkı", district = "KADIKÖY", location = GeoPoint(40.9810, 29.0260), emptyCapacity = 0),
        park(3, name = "Şişli Merkez Katlı Otoparkı", district = "ŞİŞLİ", location = GeoPoint(41.0602, 28.9877), openState = OpenState.CLOSED),
        park(4, name = "Beşiktaş Sahil", district = "BEŞİKTAŞ", location = GeoPoint(41.0422, 29.0083), openState = OpenState.UNKNOWN),
        park(5, name = null, district = "FATİH", location = GeoPoint(41.0082, 28.9784), capacity = null, emptyCapacity = null),
    )

    private fun listState(mapStatus: MapStatus = MapStatus.NO_API_KEY) = MainUiState(
        items = ParkListQuery.apply(parks, setOf(1), "", ParkFilters(), null),
        totalCount = parks.size,
        favoriteCount = 1,
        sync = SyncInfo(lastSuccessAtMillis = NOW - 3 * 60_000, lastAttemptAtMillis = NOW - 3 * 60_000),
        isCacheLoaded = true,
        mapStatus = mapStatus,
    )

    private fun capture(darkTheme: Boolean = false, content: @Composable () -> Unit) {
        composeRule.setContent {
            CompositionLocalProvider(LocalClock provides Clock { NOW }) {
                ParkTheme(darkTheme = darkTheme, content = content)
            }
        }
        composeRule.onRoot().captureRoboImage()
    }

    @Test
    fun mainList() = capture { MainScreen(listState(), MainActions()) }

    @Test
    fun mainListDark() = capture(darkTheme = true) { MainScreen(listState(), MainActions()) }

    @Test
    @Config(qualifiers = "tr-$PHONE")
    fun mainListTurkishLargeFont() {
        RuntimeEnvironment.setFontScale(1.5f)
        capture { MainScreen(listState(), MainActions()) }
    }

    @Test
    fun mainOfflineWithoutCache() = capture {
        MainScreen(
            MainUiState(isCacheLoaded = true, sync = SyncInfo(lastAttemptAtMillis = NOW, lastError = RefreshError.Network)),
            MainActions(),
        )
    }

    @Test
    fun detail() = capture {
        DetailScreen(
            state = DetailUiState(
                parkId = 1,
                park = parks[0],
                isListed = true,
                detail = ParkDetail(
                    parkId = 1,
                    name = "Kadıköy Rıhtım Otoparkı",
                    district = "KADIKÖY",
                    address = "Rıhtım Cad. No:1 Kadıköy/İstanbul",
                    parkType = "AÇIK OTOPARK",
                    workHours = "24 Saat",
                    location = GeoPoint(40.9903, 29.0236),
                    capacity = 100,
                    emptyCapacity = 30,
                    freeTime = 15,
                    monthlyFee = 2500.0,
                    tariffLines = listOf(TariffLine("0-1 Saat", "40,00"), TariffLine("1-2 Saat", "60,00"), TariffLine("Tam Gün", "200,00")),
                    sourceUpdatedAt = SourceTimestamp("05.10.2026 16:02:30", NOW - 5 * 60_000),
                    fetchedAtMillis = NOW - 60_000,
                ),
                isFavorite = true,
                listUpdatedAtMillis = NOW - 3 * 60_000,
                isLoaded = true,
            ),
            onBack = {},
            onRetry = {},
            onToggleFavorite = {},
            onDirections = {},
            onShowOnMap = {},
            onShare = {},
        )
    }

    @Test
    @Config(qualifiers = TABLET)
    fun tabletListAndMap() = capture {
        MainScreen(
            listState(mapStatus = MapStatus.AVAILABLE),
            MainActions(),
            // Robolectric cannot draw a Google map; a plain surface stands in for it.
            mapPane = { modifier -> Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)) },
        )
    }

    private companion object {
        /** 2026-10-05 16:07:30 in Istanbul. */
        const val NOW = 1_791_205_650_000L
    }
}

private const val PHONE = "w411dp-h891dp-xxhdpi"
private const val TABLET = "w1280dp-h800dp-xhdpi"
