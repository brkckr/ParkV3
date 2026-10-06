package com.brkckr.parkv3.ui.components

import android.view.accessibility.AccessibilityManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

@RunWith(AndroidJUnit4::class)
class TouchExplorationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var enabled: Boolean? = null
    private lateinit var manager: AccessibilityManager

    private fun render() = composeRule.setContent {
        manager = LocalContext.current.getSystemService(AccessibilityManager::class.java)
        enabled = rememberTouchExplorationEnabled()
    }

    @Test
    fun followsTalkBackBeingTurnedOnAndOff() {
        render()
        assertThat(enabled).isFalse()

        shadowOf(manager).setTouchExplorationEnabled(true)
        composeRule.waitForIdle()
        assertThat(enabled).isTrue()

        shadowOf(manager).setTouchExplorationEnabled(false)
        composeRule.waitForIdle()
        assertThat(enabled).isFalse()
    }
}
