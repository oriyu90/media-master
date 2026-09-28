package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import com.example.ui.theme.MediaMasterTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * v1.9.0 component screenshots (Roborazzi on Robolectric): remade shared
 * components in light + dark. Reference images live under
 * `src/test/screenshots/`; the first run records them, CI verifies.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RemadeComponentScreenshotTest {

    @Test
    fun emptyState_light() {
        captureRoboImage {
            MediaMasterTheme(darkTheme = false) {
                EmptyState(icon = Icons.Default.Description, title = "Title", description = "Description")
            }
        }
    }

    @Test
    fun emptyState_dark() {
        captureRoboImage {
            MediaMasterTheme(darkTheme = true) {
                EmptyState(icon = Icons.Default.Description, title = "Title", description = "Description")
            }
        }
    }

    @Test
    fun errorState_light() {
        captureRoboImage {
            MediaMasterTheme(darkTheme = false) {
                ErrorState(message = "Something broke", retryLabel = "Retry", onRetry = {})
            }
        }
    }

    @Test
    fun switchRow_off_light() {
        captureRoboImage {
            MediaMasterTheme(darkTheme = false) {
                SwitchRow(headline = "Backup", supporting = "Auto", checked = false, onCheckedChange = {})
            }
        }
    }

    @Test
    fun switchRow_on_dark() {
        captureRoboImage {
            MediaMasterTheme(darkTheme = true) {
                SwitchRow(headline = "Backup", supporting = "Auto", checked = true, onCheckedChange = {})
            }
        }
    }

    @Test
    fun statusPill_dark() {
        captureRoboImage {
            MediaMasterTheme(darkTheme = true) {
                StatusPill(text = "3 duplicates found")
            }
        }
    }
}
