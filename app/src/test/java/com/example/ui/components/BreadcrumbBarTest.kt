package com.example.ui.components

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * v1.9.0 behavior tests for the remade breadcrumb trail: chips render for
 * every segment, tapping navigates with the accumulated absolute path, and
 * only the current (last) chip carries the selected flag.
 */
@RunWith(RobolectricTestRunner::class)
class BreadcrumbBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun render(onNavigate: (String) -> Unit = {}) {
        composeRule.setContent {
            BreadcrumbBar(
                rootPath = "/storage/emulated/0",
                rootLabel = "Internal",
                currentPath = "/storage/emulated/0/DCIM/Camera",
                onNavigate = onNavigate,
            )
        }
    }

    @Test
    fun `root and segment chips all render`() {
        render()
        composeRule.onNodeWithText("Internal").assertExists()
        composeRule.onNodeWithText("DCIM").assertExists()
        composeRule.onNodeWithText("Camera").assertExists()
    }

    @Test
    fun `tapping a middle segment navigates to its accumulated path`() {
        var got: String? = null
        render(onNavigate = { got = it })
        composeRule.onNodeWithText("DCIM").assertHasClickAction().performClick()
        assertEquals("/storage/emulated/0/DCIM", got)
    }

    @Test
    fun `only the current chip is selected`() {
        render()
        composeRule.onNodeWithText("Camera").assertIsSelected()
        composeRule.onNodeWithText("DCIM").assertIsNotSelected()
        composeRule.onNodeWithText("Internal").assertIsNotSelected()
    }
}
