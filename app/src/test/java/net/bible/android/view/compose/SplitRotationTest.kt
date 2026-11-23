package net.bible.android.view.compose

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import net.bible.android.TEST_SDK
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowStateValue
import net.bible.sharedui.reading.SplitContent
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The window-metrics snapshot can still describe the previous rotation on Android. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [TEST_SDK], application = android.app.Application::class)
class SplitRotationTest {
    @get:Rule val compose = createComposeRule()

    private fun window(id: String) = WindowSnapshot(
        id = id, state = WindowStateValue.VISIBLE, weight = 1f, isVisible = true,
        isPinMode = true, isSynchronised = false, syncGroup = 0, isLinksWindow = false,
    )

    private fun configuration(landscape: Boolean) = Configuration().apply {
        orientation = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
        screenWidthDp = if (landscape) 900 else 600
        screenHeightDp = if (landscape) 600 else 900
    }

    @Test fun rotationRoundTripUsesCurrentConfigurationRatherThanPreviousWindowMetrics() =
        checkRotationRoundTrip(reverse = false)

    @Test fun reversedRotationRoundTripUsesCurrentConfigurationRatherThanPreviousWindowMetrics() =
        checkRotationRoundTrip(reverse = true)

    private fun checkRotationRoundTrip(reverse: Boolean) {
        val configuration = mutableStateOf(configuration(true))
        val metrics = mutableStateOf(IntSize(900, 600))
        val boxHeight = mutableStateOf(400.dp)
        compose.setContent {
            CompositionLocalProvider(
                LocalConfiguration provides configuration.value,
                LocalWindowInfo provides object : WindowInfo {
                    override val isWindowFocused = true
                    override val containerSize get() = metrics.value
                },
            ) {
                Box(Modifier.size(300.dp, boxHeight.value)) {
                    SplitContent(
                        WindowLayoutState(listOf(window("A"), window("B")), "A", null, reverse, true),
                        {}, { _, _, _, _ -> },
                        { id -> Box(Modifier.fillMaxSize().testTag("pane-$id")) },
                    )
                }
            }
        }
        assertAxis(horizontal = !reverse)
        compose.runOnIdle { configuration.value = configuration(false) }
        assertAxis(horizontal = reverse)
        // IME open/close changes the box's shape, not the window's orientation.
        compose.runOnIdle { boxHeight.value = 150.dp }
        assertAxis(horizontal = reverse)
        compose.runOnIdle { boxHeight.value = 400.dp }
        assertAxis(horizontal = reverse)
        compose.runOnIdle {
            metrics.value = IntSize(600, 900)
            configuration.value = configuration(true)
        }
        assertAxis(horizontal = !reverse)
    }

    @Test fun freshReverseSplitUsesLandscapeConfigurationDespitePortraitMetrics() {
        compose.setContent {
            CompositionLocalProvider(
                LocalConfiguration provides configuration(true),
                LocalWindowInfo provides object : WindowInfo {
                    override val isWindowFocused = true
                    override val containerSize = IntSize(600, 900)
                },
            ) {
                SplitContent(
                    WindowLayoutState(listOf(window("A"), window("B")), "A", null, true, true),
                    {}, { _, _, _, _ -> },
                    { id -> Box(Modifier.fillMaxSize().testTag("pane-$id")) },
                )
            }
        }
        assertAxis(horizontal = false)
    }

    private fun assertAxis(horizontal: Boolean) {
        val a = compose.onNodeWithTag("pane-A").fetchSemanticsNode().boundsInRoot
        val b = compose.onNodeWithTag("pane-B").fetchSemanticsNode().boundsInRoot
        assertTrue(
            "Expected ${if (horizontal) "side-by-side" else "stacked"} panes: A=$a B=$b",
            if (horizontal) b.left >= a.right - 1f && b.top <= a.top + 1f
            else b.top >= a.bottom - 1f && b.left <= a.left + 1f,
        )
    }
}
