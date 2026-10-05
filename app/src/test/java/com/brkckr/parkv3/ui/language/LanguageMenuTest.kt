package com.brkckr.parkv3.ui.language

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.brkckr.parkv3.ui.main.MainActions
import com.brkckr.parkv3.ui.main.MainScreen
import com.brkckr.parkv3.ui.main.MainUiState
import com.brkckr.parkv3.ui.theme.ParkTheme
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w411dp-h891dp")
class LanguageMenuTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val chosen = mutableListOf<AppLanguage>()

    private fun render(current: AppLanguage) = composeRule.setContent {
        ParkTheme {
            MainScreen(
                state = MainUiState(isCacheLoaded = true),
                actions = MainActions(onChangeLanguage = { chosen += it }),
                language = current,
            )
        }
    }

    @Test
    fun menuListsTheLanguagesAndMarksTheCurrentOne() {
        render(AppLanguage.TURKISH)

        composeRule.onNodeWithContentDescription("Language").performClick()

        composeRule.onNodeWithText("System default").assertIsDisplayed().assertIsNotSelected()
        composeRule.onNodeWithText("Türkçe").assertIsDisplayed().assertIsSelected()
        composeRule.onNodeWithText("English").assertIsDisplayed().assertIsNotSelected()
    }

    @Test
    fun choosingAnotherLanguageReportsIt() {
        render(AppLanguage.SYSTEM)

        composeRule.onNodeWithContentDescription("Language").performClick()
        composeRule.onNodeWithText("English").performClick()

        assertThat(chosen).containsExactly(AppLanguage.ENGLISH)
        composeRule.onNodeWithText("Türkçe").assertDoesNotExist() // the menu closed
    }

    @Test
    fun choosingTheCurrentLanguageDoesNothing() {
        render(AppLanguage.ENGLISH)

        composeRule.onNodeWithContentDescription("Language").performClick()
        composeRule.onNodeWithText("English").performClick()

        assertThat(chosen).isEmpty()
    }
}
