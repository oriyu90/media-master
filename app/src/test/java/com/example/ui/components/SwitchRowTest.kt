package com.example.ui.components

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * v1.9.0 behavior tests for the shared SwitchRow: the whole row carries
 * [Role.Switch], reflects the checked state, and toggles through the row
 * (not only the thumb).
 */
@RunWith(RobolectricTestRunner::class)
class SwitchRowTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `whole row has switch role and toggles on click`() {
        val seen = mutableListOf<Boolean>()
        composeRule.setContent {
            SwitchRow(
                headline = "Backup",
                supporting = "Auto",
                checked = false,
                onCheckedChange = { seen.add(it) },
            )
        }
        composeRule.onNode(
            hasText("Backup") and
                SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch),
        )
            .assertExists()
            .assertHasClickAction()
            .assertIsOff()
        composeRule.onNodeWithText("Backup").performClick()
        assertEquals(listOf(true), seen)
    }

    @Test
    fun `checked state is exposed as toggleable-on`() {
        composeRule.setContent {
            SwitchRow(headline = "Backup", checked = true, onCheckedChange = {})
        }
        composeRule.onNodeWithText("Backup").assertIsOn()
    }
}
