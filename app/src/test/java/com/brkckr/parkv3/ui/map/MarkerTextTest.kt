package com.brkckr.parkv3.ui.map

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brkckr.parkv3.testutil.park
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** What TalkBack is given for a map pin: the same name, status and occupancy as the list. */
@RunWith(AndroidJUnit4::class)
class MarkerTextTest {

    private val resources: Resources
        get() = ApplicationProvider.getApplicationContext<Context>().resources

    @Test
    fun `a pin is named and describes status and occupancy`() {
        val text = resources.markerText(park(7, name = "Kadıköy Rıhtım", emptyCapacity = 30))

        assertThat(text.title).isEqualTo("Kadıköy Rıhtım")
        assertThat(text.snippet).isEqualTo("Free spaces · 30 free spaces of 100")
    }

    @Test
    fun `an unnamed park without occupancy is still described`() {
        val text = resources.markerText(park(7, name = null, capacity = null, emptyCapacity = null))

        assertThat(text.title).isEqualTo("Unnamed car park (#7)")
        assertThat(text.snippet).isEqualTo("Occupancy unknown · Occupancy not reported")
    }

    @Test
    @Config(qualifiers = "tr")
    fun `pin text follows the app language`() {
        val text = resources.markerText(park(7, name = null, emptyCapacity = 30))

        assertThat(text.title).isEqualTo("Adsız otopark (#7)")
        assertThat(text.snippet).isEqualTo("Boş yer var · 100 yerden 30 boş")
    }
}
