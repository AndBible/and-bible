package net.bible.sharedui.reading

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import kotlin.test.Test
import kotlin.test.assertEquals
import net.bible.sharedcore.window.WindowLayoutState
import net.bible.sharedcore.window.WindowSnapshot
import net.bible.sharedcore.window.WindowStateValue
import net.bible.sharedcore.window.buildWindowTabBar
import net.bible.sharedui.reading.session.ReadingOffsets
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme

/** Exercises the real rail and the offset notification consumed by document hosts. */
@OptIn(ExperimentalTestApi::class)
class SplitRailMeasurementTest {
    @Test fun expandedRailSpansAllThreeBottomWindows() = checkRail(Check.FullWidth)
    @Test fun collapsingRailPushesSystemOnlyOffsetsToEveryDocument() = checkRail(Check.Collapse)
    @Test fun removingRailPushesSystemOnlyOffsetsEvenWithRestorePreferenceOn() = checkRail(Check.Hide)
    @Test fun stackedWindowsReserveOnlyTheBottomStrip() = checkRail(Check.Stacked)
    @Test fun maximisingWindowClearsTheRailReservation() = checkRail(Check.Maximise)
    @Test fun closingOtherWindowsClearsTheRailReservation() = checkRail(Check.Single)
    @Test fun leavingReadingCompositionReleasesTheStrip() = checkRail(Check.Dispose)
    @Test fun darkRailStillReleasesTheStrip() = checkRail(Check.Collapse, dark = true)
    @Test fun monochromeRailStillReleasesTheStrip() = checkRail(Check.Collapse, mono = true)
    @Test fun darkMonochromeRailStillReleasesTheStrip() = checkRail(Check.Collapse, dark = true, mono = true)
    @Test fun animatedRailStillReleasesTheStrip() = checkRail(Check.Collapse, animations = true)

    @Test fun sideBySideLayoutOverridesAHostThatThinksItIsStacked() = checkRail(Check.HostSaysStacked)
    @Test fun stackedLayoutOverridesAHostThatThinksItIsSideBySide() = checkRail(Check.HostSaysSideBySide)

    private enum class Check { HostSaysStacked, HostSaysSideBySide,
        FullWidth, Collapse, Hide, Stacked, Maximise, Single, Dispose }

    private fun checkRail(check: Check, dark: Boolean = false, mono: Boolean = false, animations: Boolean = false) = runSkikoComposeUiTest(
        size = Size(900f, 600f), density = Density(1f),
    ) {
        val expanded = mutableStateOf(true)
        val hidden = mutableStateOf(false)
        val reverse = mutableStateOf(false)
        val maximised = mutableStateOf(false)
        val single = mutableStateOf(false)
        val disposed = mutableStateOf(false)
        val paneBounds = mutableMapOf<String, Rect>()
        var railBounds = Rect.Zero
        var pushed: Map<String, Float> = emptyMap()
        var changes = 0
        val ids = listOf("left", "middle", "right")
        lateinit var offsets: ReadingOffsets
        // The host's own portrait rule; the two Host* checks make it disagree with the real layout.
        val hostRule: (String) -> Boolean = when (check) {
            Check.HostSaysStacked -> { id -> id == "right" }
            Check.HostSaysSideBySide -> { _ -> true }
            else -> { id -> !reverse.value || id == "right" }
        }
        offsets = ReadingOffsets(restoreButtonsVisible = { expanded.value }) {
            changes++
            pushed = ids.associateWith { offsets.bottomForWindow(it, hostRule(it)) }
        }
        offsets.setSystemBottomInset(34f)
        val windows = ids.map {
            WindowSnapshot(it, WindowStateValue.VISIBLE, 1f, true, true, false, 0, false)
        }
        setContent {
            AbTheme(
                darkTheme = dark,
                colorMode = if (mono) net.bible.service.common.DisplayColorMode.MONOCHROME else net.bible.service.common.DisplayColorMode.NORMAL,
                disableAnimations = !animations,
            ) {
                CompositionLocalProvider(LocalStrings provides iosStrings("en")) {
                    val layout = WindowLayoutState(
                        if (single.value) windows.take(1) else windows, "left",
                        if (maximised.value) "left" else null, reverse.value, expanded.value,
                    )
                    if (!disposed.value) SplitContent(
                        layout = layout,
                        onWindowActivated = {}, onSeparatorCommitted = { _, _, _, _ -> },
                        pane = { id -> Box(Modifier.fillMaxSize().onGloballyPositioned { paneBounds[id] = it.boundsInRoot() }) },
                        railOverlay = if (hidden.value) null else { {
                            WindowTabBar(
                                model = buildWindowTabBar(layout),
                                onRestore = {}, onWindowLongPress = {}, onAddWindow = {}, onUnMaximise = {},
                                onToggleCollapse = { expanded.value = !expanded.value }, windowLabel = { it.id },
                                modifier = Modifier.onGloballyPositioned { railBounds = it.boundsInRoot() },
                            )
                        } },
                        onRailLayoutChanged = offsets::setRailLayout,
                    )
                }
            }
        }
        waitForIdle()
        val panesBefore = paneBounds.toMap()
        val before = changes
        when (check) {
            Check.HostSaysStacked -> {
                assertEquals(mapOf("left" to 74f, "middle" to 74f, "right" to 74f), pushed)
                onNodeWithContentDescription("Expand/collapse").performClick()
                waitForIdle()
                assertEquals(mapOf("left" to 34f, "middle" to 34f, "right" to 34f), pushed)
            }
            Check.HostSaysSideBySide -> {
                runOnIdle { reverse.value = true }
                waitForIdle()
                assertEquals(mapOf("left" to 0f, "middle" to 0f, "right" to 74f), pushed)
            }
            Check.FullWidth -> {
                assertEquals(0f, railBounds.left)
                assertEquals(900f, railBounds.right)
                assertEquals(mapOf("left" to 74f, "middle" to 74f, "right" to 74f), pushed)
            }
            Check.Stacked -> {
                runOnIdle { reverse.value = true }
                waitForIdle()
                assertEquals(mapOf("left" to 0f, "middle" to 0f, "right" to 74f), pushed)
                onNodeWithContentDescription("Expand/collapse").performClick()
                waitForIdle()
                assertEquals(mapOf("left" to 0f, "middle" to 0f, "right" to 34f), pushed)
            }
            else -> {
                if (check == Check.Collapse) onNodeWithContentDescription("Expand/collapse").performClick()
                else runOnIdle {
                    when (check) {
                        Check.Collapse -> error("handled by the real rail control")
                        Check.Hide -> hidden.value = true
                        Check.Maximise -> maximised.value = true
                        Check.Single -> single.value = true
                        Check.Dispose -> disposed.value = true
                        else -> error("handled above")
                    }
                }
                waitForIdle()
                assertEquals(before + 1, changes, "the visibility transition must resend document offsets exactly once")
                // Closed windows are no longer in the layout, so only the survivor touches the bottom edge.
                val expected = if (check == Check.Single) 0f else 34f
                assertEquals(mapOf("left" to 34f, "middle" to expected, "right" to expected), pushed)
                if (check == Check.Collapse || check == Check.Hide) {
                    assertEquals(panesBefore, paneBounds, "rail visibility must not resize the documents")
                    if (check == Check.Collapse) onNodeWithContentDescription("Expand/collapse").performClick()
                    else runOnIdle { hidden.value = false }
                    waitForIdle()
                    assertEquals(mapOf("left" to 74f, "middle" to 74f, "right" to 74f), pushed)
                }
            }
        }
    }
}
