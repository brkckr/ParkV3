package com.brkckr.parkv3.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities

/** The real monitor against Robolectric's ConnectivityManager; callbacks are invoked by hand. */
@RunWith(AndroidJUnit4::class)
class ConnectivityNetworkMonitorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val connectivity = checkNotNull(context.getSystemService(ConnectivityManager::class.java))

    private fun capabilities(validated: Boolean): NetworkCapabilities =
        ShadowNetworkCapabilities.newInstance().also {
            shadowOf(it).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            if (validated) shadowOf(it).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

    @Test
    fun `starts from the current network and follows the default network callback`() = runTest {
        val network = checkNotNull(connectivity.activeNetwork)
        shadowOf(connectivity).setNetworkCapabilities(network, capabilities(validated = true))

        ConnectivityNetworkMonitor(context).isOnline.test {
            assertThat(awaitItem()).isTrue()
            val callback = shadowOf(connectivity).networkCallbacks.single()

            callback.onLost(network)
            assertThat(awaitItem()).isFalse()

            // Connected but not yet confirmed to reach the internet: still offline.
            callback.onCapabilitiesChanged(network, capabilities(validated = false))
            expectNoEvents()

            callback.onCapabilitiesChanged(network, capabilities(validated = true))
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(shadowOf(connectivity).networkCallbacks).isEmpty()
    }

    @Test
    fun `no default network means offline`() = runTest {
        shadowOf(connectivity).setDefaultNetworkActive(false)

        ConnectivityNetworkMonitor(context).isOnline.test {
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
