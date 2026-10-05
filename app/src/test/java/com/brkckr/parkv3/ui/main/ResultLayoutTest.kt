package com.brkckr.parkv3.ui.main

import androidx.compose.ui.unit.dp
import com.brkckr.parkv3.ui.map.MapStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ResultLayoutTest {

    private val phone = 411.dp
    private val tablet = 1000.dp

    @Test
    fun `wide windows with a working map show list and map together`() {
        assertThat(resultLayout(tablet, MapStatus.AVAILABLE, ViewMode.LIST, ListContent.Items)).isEqualTo(ResultLayout.LIST_AND_MAP)
        assertThat(resultLayout(tablet, MapStatus.AVAILABLE, ViewMode.MAP, ListContent.Items)).isEqualTo(ResultLayout.LIST_AND_MAP)
        assertThat(resultLayout(TWO_PANE_MIN_WIDTH, MapStatus.AVAILABLE, ViewMode.LIST, ListContent.Items))
            .isEqualTo(ResultLayout.LIST_AND_MAP)
        assertThat(showsViewModeToggle(tablet, MapStatus.AVAILABLE)).isFalse()
    }

    @Test
    fun `narrow windows show one pane chosen by the toggle`() {
        assertThat(resultLayout(phone, MapStatus.AVAILABLE, ViewMode.MAP, ListContent.Items)).isEqualTo(ResultLayout.MAP)
        assertThat(resultLayout(phone, MapStatus.AVAILABLE, ViewMode.LIST, ListContent.Items)).isEqualTo(ResultLayout.LIST)
        assertThat(resultLayout(TWO_PANE_MIN_WIDTH - 1.dp, MapStatus.AVAILABLE, ViewMode.LIST, ListContent.Items))
            .isEqualTo(ResultLayout.LIST)
        assertThat(showsViewModeToggle(phone, MapStatus.AVAILABLE)).isTrue()
    }

    @Test
    fun `without a working map the list is shown alone at any width`() {
        for (status in listOf(MapStatus.NO_API_KEY, MapStatus.NO_PLAY_SERVICES)) {
            assertThat(resultLayout(tablet, status, ViewMode.MAP, ListContent.Items)).isEqualTo(ResultLayout.LIST)
            assertThat(showsViewModeToggle(tablet, status)).isFalse()
            assertThat(showsViewModeToggle(phone, status)).isFalse()
        }
    }

    @Test
    fun `empty and error states replace the results at any width`() {
        for (width in listOf(phone, tablet)) {
            assertThat(resultLayout(width, MapStatus.AVAILABLE, ViewMode.MAP, ListContent.NoFavorites)).isEqualTo(ResultLayout.MESSAGE)
            assertThat(resultLayout(width, MapStatus.AVAILABLE, ViewMode.LIST, ListContent.Loading)).isEqualTo(ResultLayout.MESSAGE)
        }
    }

    @Test
    fun `list pane takes 40 percent within readable bounds`() {
        assertThat(listPaneWidth(1000.dp)).isEqualTo(400.dp)
        assertThat(listPaneWidth(TWO_PANE_MIN_WIDTH)).isEqualTo(336.dp)
        assertThat(listPaneWidth(2000.dp)).isEqualTo(440.dp)
        assertThat(listPaneWidth(600.dp)).isEqualTo(320.dp)
    }
}
