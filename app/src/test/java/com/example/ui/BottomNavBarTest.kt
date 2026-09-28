package com.example.ui

import android.content.Context
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * v1.9.0 behavior tests for the phone NavigationBar: all five top
 * destinations render, taps navigate by route, and selection follows
 * the current route.
 */
@RunWith(RobolectricTestRunner::class)
class BottomNavBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `all five destinations render`() {
        composeRule.setContent { BottomNavBar(currentRoute = "home", onNavigate = {}) }
        for (res in listOf(R.string.home, R.string.library, R.string.audio, R.string.documents, R.string.manage)) {
            composeRule.onNodeWithText(context.getString(res)).assertExists()
        }
    }

    @Test
    fun `tap navigates by route`() {
        var got: String? = null
        composeRule.setContent { BottomNavBar(currentRoute = "home", onNavigate = { got = it }) }
        composeRule.onNodeWithText(context.getString(R.string.library)).performClick()
        assertEquals("library", got)
    }

    @Test
    fun `selection follows the current route`() {
        composeRule.setContent { BottomNavBar(currentRoute = "audio", onNavigate = {}) }
        composeRule.onNodeWithText(context.getString(R.string.audio)).assertIsSelected()
        composeRule.onNodeWithText(context.getString(R.string.home)).assertIsNotSelected()
    }
}
