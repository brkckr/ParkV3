package com.brkckr.parkv3.navigation

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brkckr.parkv3.domain.model.GeoPoint
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ShareTest {

    private val location = GeoPoint(41.0246, 29.0915)
    private val defaultLocale = Locale.getDefault()

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun `text has the name, the address and a map link with a decimal point in any locale`() {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))

        val text = ShareIntents.text("15 Temmuz Otoparkı", "ÜMRANİYE 15 TEMMUZ ŞEHİTLER MEYDANI", location)

        assertThat(text.lines()).containsExactly(
            "15 Temmuz Otoparkı",
            "ÜMRANİYE 15 TEMMUZ ŞEHİTLER MEYDANI",
            "https://www.google.com/maps/search/?api=1&query=41.024600,29.091500",
        ).inOrder()
    }

    @Test
    fun `a missing or blank address is left out`() {
        assertThat(ShareIntents.text("Otopark", null, location).lines()).hasSize(2)
        assertThat(ShareIntents.text("Otopark", "  ", location).lines()).hasSize(2)
    }

    @Test
    fun `sharing opens the system chooser with plain text`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()

        assertThat(activity.sharePark("Otopark", "Adres", location, "Share car park")).isTrue()

        val chooser = shadowOf(activity).nextStartedActivity
        assertThat(chooser.action).isEqualTo(Intent.ACTION_CHOOSER)
        val send = checkNotNull(IntentCompat.getParcelableExtra(chooser, Intent.EXTRA_INTENT, Intent::class.java))
        assertThat(send.action).isEqualTo(Intent.ACTION_SEND)
        assertThat(send.type).isEqualTo("text/plain")
        assertThat(send.getStringExtra(Intent.EXTRA_SUBJECT)).isEqualTo("Otopark")
        assertThat(send.getStringExtra(Intent.EXTRA_TEXT)).isEqualTo(ShareIntents.text("Otopark", "Adres", location))
    }
}
