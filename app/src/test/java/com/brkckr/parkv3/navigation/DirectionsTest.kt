package com.brkckr.parkv3.navigation

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brkckr.parkv3.domain.model.GeoPoint
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import java.util.Locale

/** Scenario 9: without Google Maps the chain falls back safely and never crashes. */
@RunWith(AndroidJUnit4::class)
class DirectionsTest {

    private val destination = GeoPoint(41.0082, 28.9784)
    private lateinit var activity: ComponentActivity
    private val defaultLocale = Locale.getDefault()

    @Before
    fun setUp() {
        activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        // Unresolvable intents now throw ActivityNotFoundException, as on a real device.
        shadowOf(activity.application).checkActivities(true)
    }

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
    }

    private fun installHandler(packageName: String, scheme: String, browsable: Boolean = false) {
        val component = ComponentName(packageName, "$packageName.Main")
        val packageManager = shadowOf(activity.packageManager)
        packageManager.addActivityIfNotPresent(component)
        packageManager.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                if (browsable) addCategory(Intent.CATEGORY_BROWSABLE)
                addDataScheme(scheme)
            },
        )
    }

    @Test
    fun `uses the Google Maps app when installed`() {
        installHandler(DirectionsIntents.GOOGLE_MAPS_PACKAGE, "https")

        assertThat(activity.openDirections(destination, "Sultanahmet")).isTrue()

        val started = shadowOf(activity).nextStartedActivity
        assertThat(started.`package`).isEqualTo(DirectionsIntents.GOOGLE_MAPS_PACKAGE)
        assertThat(started.data).isEqualTo(DirectionsIntents.webUri(destination))
    }

    @Test
    fun `falls back to another map app through a geo uri`() {
        installHandler("org.example.maps", "geo")

        assertThat(activity.openDirections(destination, "Sultanahmet Meydanı")).isTrue()

        val started = shadowOf(activity).nextStartedActivity
        assertThat(started.data?.scheme).isEqualTo("geo")
        assertThat(started.data?.toString()).contains("41.008200,28.978400")
    }

    @Test
    fun `falls back to the browser when no map app exists`() {
        installHandler("org.example.browser", "https", browsable = true)

        assertThat(activity.openDirections(destination, "X")).isTrue()

        val started = shadowOf(activity).nextStartedActivity
        assertThat(started.`package`).isNull()
        assertThat(started.data).isEqualTo(DirectionsIntents.webUri(destination))
    }

    @Test
    fun `reports failure instead of crashing when nothing can open directions`() {
        assertThat(activity.openDirections(destination, "X")).isFalse()
        assertThat(shadowOf(activity).nextStartedActivity).isNull()
    }

    @Test
    fun `coordinates keep a decimal point under a Turkish locale`() {
        Locale.setDefault(Locale.forLanguageTag("tr-TR"))

        val uri = DirectionsIntents.webUri(destination).toString()

        assertThat(uri).contains("destination=41.008200,28.978400")
    }
}
