package net.bible.sharedui.reading.activity

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import net.bible.android.control.activity.ActivityItem
import net.bible.android.control.activity.ActivityKind
import net.bible.android.control.activity.ActivityState
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedui.reading.ReadingToolbar
import net.bible.sharedui.reading.ReadingToolbarCallbacks
import net.bible.sharedui.reading.ReadingToolbarIcons
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme

@OptIn(ExperimentalTestApi::class)
class ActivityMenuTest {
    private val strings = iosStrings("en")
    private val painter = ColorPainter(Color.Gray)
    private val icons = ReadingToolbarIcons(painter, painter, painter, painter, painter, painter, painter, painter, painter)
    private val callbacks = ReadingToolbarCallbacks(
        {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {},
    )

    private fun toolbarState(syncRunning: Boolean = false) = ToolbarState(
        pageTitle = "John 3", documentTitle = "KJV", syncRunning = syncRunning, showBible = false, showCommentary = false,
        showStrongs = false, strongsMode = 0, searchable = false, speakable = false, speakStopped = true,
    )

    private fun item(
        id: String, state: ActivityState = ActivityState.RUNNING, progress: Int? = null, message: String? = null,
    ) = ActivityItem(
        id = id, kind = ActivityKind.DOWNLOAD, title = "Item $id", state = state, progress = progress,
        message = message, cancel = null, startedAt = 1000L, finishedAt = if (state == ActivityState.RUNNING) null else 2000L,
        read = state != ActivityState.FAILED,
    )

    private fun menu(items: List<ActivityItem>, unread: Int = 0, onShowAll: () -> Unit = {}, onOpened: () -> Unit = {}) =
        ActivityMenuState(items, unread, onShowAll, onOpened)

    private fun androidx.compose.ui.test.ComposeUiTest.toolbar(
        activity: ActivityMenuState?, syncRunning: Boolean = false, noAnim: Boolean = false, expanded: Boolean = false,
    ) = setContent {
        AbTheme(disableAnimations = noAnim) {
            CompositionLocalProvider(LocalStrings provides strings) {
                ReadingToolbar(
                    state = toolbarState(syncRunning), icons = icons, callbacks = callbacks,
                    overflowExpanded = expanded, activity = activity,
                )
            }
        }
    }

    private fun ring(t: androidx.compose.ui.test.ComposeUiTest) = t.onNodeWithTag(ACTIVITY_RING_TAG, useUnmergedTree = true)
    private fun rangeOf(n: SemanticsNodeInteraction) = n.fetchSemanticsNode().config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)

    @Test fun nullActivityRendersTodaysToolbar() {
        fun tree(withArgument: Boolean): String {
            var out = ""
            runComposeUiTest {
                setContent {
                    AbTheme { CompositionLocalProvider(LocalStrings provides strings) {
                        if (withArgument) {
                            ReadingToolbar(toolbarState(true), icons, callbacks, activity = null)
                        } else {
                            ReadingToolbar(toolbarState(true), icons, callbacks)
                        }
                    } }
                }
                // Node ids differ between separate compositions; the structure must not.
                out = onRoot(useUnmergedTree = true).printToString().replace(Regex("#\\d+"), "#")
            }
            return out
        }
        val baseline = tree(withArgument = false)
        assertEquals(baseline, tree(withArgument = true))
        assertFalse(baseline.contains(ACTIVITY_RING_TAG) || baseline.contains(ACTIVITY_BADGE_TAG))
    }

    @Test fun activityHidesTitleSyncGlyphAndNullKeepsIt() {
        fun render(activity: ActivityMenuState?, syncRunning: Boolean): androidx.compose.ui.graphics.PixelMap {
            lateinit var pixels: androidx.compose.ui.graphics.PixelMap
            runComposeUiTest {
                toolbar(activity = activity, syncRunning = syncRunning, noAnim = true)
                pixels = onRoot().captureToImage().toPixelMap()
            }
            return pixels
        }
        val baseline = render(null, syncRunning = false)
        assertFalse(samePixels(baseline, render(null, syncRunning = true)), "preference off keeps the sync glyph")
        assertTrue(samePixels(baseline, render(menu(emptyList()), syncRunning = true)), "preference on hides the glyph")
    }

    @Test fun ringShownWhileRunning() = runComposeUiTest {
        toolbar(menu(listOf(item("a"))))
        ring(this).assertExists()
    }

    @Test fun noRingWhenNothingRuns() = runComposeUiTest {
        toolbar(menu(listOf(item("a", ActivityState.SUCCEEDED), item("b", ActivityState.FAILED, message = "x")), unread = 1))
        onAllNodesWithTag(ACTIVITY_RING_TAG, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun determinateWhenSingleItemHasPercent() = runComposeUiTest {
        toolbar(menu(listOf(item("a", progress = 40))))
        val range = rangeOf(ring(this))!!
        assertEquals(0.4f, range.current, 0.001f)
    }

    @Test fun indeterminateWhenTwoRunning() = runComposeUiTest {
        toolbar(menu(listOf(item("a", progress = 40), item("b", progress = 10))))
        assertEquals(ProgressBarRangeInfo.Indeterminate, rangeOf(ring(this)))
    }

    @Test fun indeterminateWhenSingleItemHasNoPercent() = runComposeUiTest {
        toolbar(menu(listOf(item("a"))))
        assertEquals(ProgressBarRangeInfo.Indeterminate, rangeOf(ring(this)))
    }

    @Test fun ringIsDescribedForAccessibility() = runComposeUiTest {
        toolbar(menu(listOf(item("a"))))
        assertEquals(
            listOf(strings.activityRunningDescription),
            ring(this).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription),
        )
    }

    @Test fun badgeShowsUnreadCount() = runComposeUiTest {
        toolbar(menu(listOf(item("a", ActivityState.FAILED, message = "x"), item("b", ActivityState.FAILED, message = "y")), unread = 2))
        onNodeWithContentDescription("2 failed", useUnmergedTree = true).assertExists()
        onNodeWithText("2", useUnmergedTree = true).assertExists()
    }

    @Test fun noBadgeWithoutUnreadFailures() = runComposeUiTest {
        toolbar(menu(listOf(item("a", ActivityState.FAILED, message = "x")), unread = 0))
        onAllNodesWithTag(ACTIVITY_BADGE_TAG, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun menuSectionCapsAtThreeRows() = runComposeUiTest {
        setContent { Section(menu((1..5).map { item("$it") })) }
        onAllNodesWithTag(ACTIVITY_ROW_TAG).assertCountEquals(3)
        onNodeWithText("Show all (5)").assertExists()
    }

    @Test fun twentyRunningItemsShowRingAndThreeRows() = runComposeUiTest {
        val items = (1..20).map { item("$it", progress = 50) }
        setContent {
            AbTheme { CompositionLocalProvider(LocalStrings provides strings) {
                Column {
                    ReadingToolbar(toolbarState(), icons, callbacks, activity = menu(items))
                    ActivityMenuSection(menu(items))
                }
            } }
        }
        assertEquals(ProgressBarRangeInfo.Indeterminate, rangeOf(ring(this)))
        onAllNodesWithTag(ACTIVITY_ROW_TAG).assertCountEquals(3)
        onNodeWithText("Show all (20)").assertExists()
    }

    @Test fun showAllShownWhenAnyFailure() = runComposeUiTest {
        setContent { Section(menu(listOf(item("a", ActivityState.FAILED, message = "boom")), unread = 1)) }
        onAllNodesWithTag(ACTIVITY_ROW_TAG).assertCountEquals(1)
        onNodeWithText("boom").assertExists()
        onNodeWithText("Show all (1)").assertExists()
    }

    @Test fun showAllShownWhenFinishedItemsAreOnlyInTheList() = runComposeUiTest {
        setContent {
            Section(menu((1..3).map { item("r$it") } + (1..4).map { item("s$it", ActivityState.SUCCEEDED) }))
        }
        onAllNodesWithTag(ACTIVITY_ROW_TAG).assertCountEquals(3)
        onNodeWithText("Show all (7)").assertExists()
    }

    @Test fun showAllCountIgnoresCancelledLikeTheList() = runComposeUiTest {
        setContent { Section(menu(listOf(item("a"), item("b"), item("c", ActivityState.CANCELLED)))) }
        onAllNodesWithTag(ACTIVITY_ROW_TAG).assertCountEquals(2)
        assertEquals(0, onAllNodesWithText("Show all", substring = true).fetchSemanticsNodes().size,
            "a cancelled item is not in the list, so nothing is hidden from the menu")
    }

    @Test fun showAllCountExcludesCancelledWhenShown() = runComposeUiTest {
        setContent {
            Section(menu((1..4).map { item("r$it") } + item("c", ActivityState.CANCELLED)))
        }
        onNodeWithText("Show all (4)").assertExists()
    }

    @Test fun showAllHiddenForTwoRunningRows() = runComposeUiTest {
        setContent { Section(menu(listOf(item("a"), item("b")))) }
        onAllNodesWithTag(ACTIVITY_ROW_TAG).assertCountEquals(2)
        assertEquals(0, onAllNodesWithText("Show all (2)").fetchSemanticsNodes().size)
    }

    @Test fun runningRowsComeBeforeFailuresInTheCappedRows() = runComposeUiTest {
        val items = listOf(
            item("f1", ActivityState.FAILED, message = "m1"), item("f2", ActivityState.FAILED, message = "m2"),
            item("f3", ActivityState.FAILED, message = "m3"), item("run"),
        )
        setContent { Section(menu(items, unread = 3)) }
        onNodeWithText("Item run").assertExists()
        assertEquals(0, onAllNodesWithText("Item f3").fetchSemanticsNodes().size)
    }

    @Test fun syncNowIsFirstRowOfSection() = runComposeUiTest {
        val sync = SyncNowMenuState(mutableStateOf(true), mutableStateOf(false), {})
        setContent { Section(ActivityMenuState(listOf(item("a")), 0, {}, {}, sync)) }
        val syncTop = onNodeWithTag(SYNC_NOW_ROW_TAG).fetchSemanticsNode().boundsInRoot.top
        val activityTop = onNodeWithTag(ACTIVITY_ROW_TAG).fetchSemanticsNode().boundsInRoot.top
        assertTrue(syncTop < activityTop)
    }

    @Test fun syncNowRowDisabledWhileRunning() = runComposeUiTest {
        val running = mutableStateOf(false)
        setContent { Section(ActivityMenuState(emptyList(), 0, {}, {}, SyncNowMenuState(mutableStateOf(true), running, {}))) }
        onNodeWithTag(SYNC_NOW_ROW_TAG).assertIsEnabled()
        running.value = true
        onNodeWithTag(SYNC_NOW_ROW_TAG).assertIsNotEnabled()
    }

    @Test fun syncNowRowTapStartsAndClosesMenu() = runComposeUiTest {
        val actions = mutableListOf<String>()
        val activity = ActivityMenuState(emptyList(), 0, {}, {}, SyncNowMenuState(mutableStateOf(true), mutableStateOf(false)) { actions += "start" })
        setContent { AbTheme { CompositionLocalProvider(LocalStrings provides strings) {
            ReadingToolbar(toolbarState(), icons, callbacks, overflowExpanded = true, onOverflowDismiss = { actions += "close" }, activity = activity)
        } } }
        onNodeWithTag(SYNC_NOW_ROW_TAG).performClick()
        assertEquals(listOf("close", "start"), actions)
    }

    @Test fun sectionShowsOnlySyncNowWhenNoItems() = runComposeUiTest {
        setContent { Section(ActivityMenuState(emptyList(), 0, {}, {}, SyncNowMenuState(mutableStateOf(true), mutableStateOf(false), {}))) }
        onNodeWithTag(ACTIVITY_SECTION_TAG).assertExists()
        onNodeWithTag(SYNC_NOW_ROW_TAG).assertExists()
        onAllNodesWithTag(ACTIVITY_ROW_TAG).assertCountEquals(0)
        onAllNodesWithText("Show all", substring = true).assertCountEquals(0)
    }

    @Test fun noSectionWhenSyncNotSetUpAndNoItems() = runComposeUiTest {
        setContent { Section(ActivityMenuState(emptyList(), 0, {}, {}, SyncNowMenuState(mutableStateOf(false), mutableStateOf(false), {}))) }
        onAllNodesWithTag(ACTIVITY_SECTION_TAG).assertCountEquals(0)
    }

    @Test fun emptyItemsRenderNothing() = runComposeUiTest {
        setContent { Section(menu(emptyList())) }
        onAllNodesWithTag(ACTIVITY_ROW_TAG).assertCountEquals(0)
        onAllNodesWithTag(ACTIVITY_SECTION_TAG).assertCountEquals(0)
    }

    @Test fun showAllRowCallsOnShowAll() = runComposeUiTest {
        var calls = 0
        setContent { Section(menu((1..4).map { item("$it") }, onShowAll = { calls++ })) }
        onNodeWithText("Show all (4)").performClick()
        assertEquals(1, calls)
    }

    @Test fun showAllInTheOverflowMenuClosesTheMenuAndOpensTheList() = runComposeUiTest {
        var showAll = 0
        var dismissed = 0
        val activity = menu((1..4).map { item("$it") }, onShowAll = { showAll++ })
        setContent {
            AbTheme { CompositionLocalProvider(LocalStrings provides strings) {
                ReadingToolbar(
                    toolbarState(), icons, callbacks, overflowExpanded = true, onOverflowDismiss = { dismissed++ },
                    activity = activity,
                )
            } }
        }
        onNodeWithText("Show all (4)").performClick()
        assertEquals(1, dismissed, "the overflow menu must be closed")
        assertEquals(1, showAll)
    }

    @Test fun menuSectionDropsSucceededRows() = runComposeUiTest {
        setContent {
            Section(menu(listOf(item("ok", ActivityState.SUCCEEDED), item("run"), item("bad", ActivityState.FAILED, message = "m")), unread = 1))
        }
        onAllNodesWithTag(ACTIVITY_ROW_TAG).assertCountEquals(2)
        assertEquals(0, onAllNodesWithText("Item ok").fetchSemanticsNodes().size)
        // The count is what the full list shows: the succeeded row is in it too.
        onNodeWithText("Show all (3)").assertExists()
    }

    @Test fun onlySucceededRowsRenderNoSection() = runComposeUiTest {
        setContent { Section(menu(listOf(item("ok", ActivityState.SUCCEEDED)))) }
        onAllNodesWithTag(ACTIVITY_SECTION_TAG).assertCountEquals(0)
    }

    @Test fun openingMenuCallsOnMenuOpened() = runComposeUiTest {
        var opened = 0
        var expanded by mutableStateOf(false)
        val activity = menu(listOf(item("a")), onOpened = { opened++ })
        setContent {
            AbTheme { CompositionLocalProvider(LocalStrings provides strings) {
                ReadingToolbar(toolbarState(), icons, callbacks, overflowExpanded = expanded, activity = activity)
            } }
        }
        waitForIdle()
        assertEquals(0, opened)
        expanded = true
        waitForIdle()
        assertEquals(1, opened)
    }

    @Test fun failureArrivingWhileTheMenuIsOpenIsMarkedRead() = runComposeUiTest {
        var opened = 0
        // One state instance for the whole run, like the real host: only the State values change.
        val items = mutableStateOf(listOf(item("a")))
        val unread = mutableStateOf(0)
        val activity = ActivityMenuState(items, unread, {}, { opened++ })
        setContent {
            AbTheme { CompositionLocalProvider(LocalStrings provides strings) {
                ReadingToolbar(toolbarState(), icons, callbacks, overflowExpanded = true, activity = activity)
            } }
        }
        waitForIdle()
        val afterOpen = opened
        assertTrue(afterOpen >= 1, "opening marks read")
        items.value = listOf(item("a"), item("f", ActivityState.FAILED, message = "boom"))
        unread.value = 1
        waitForIdle()
        assertTrue(opened > afterOpen, "a failure that arrives while the menu is open is marked read too")
    }

    @Test fun noAnimationsStaticRing() = runComposeUiTest {
        mainClock.autoAdvance = false
        toolbar(menu(listOf(item("a"))), noAnim = true)
        waitForIdle()
        val before = ring(this).captureToImage().toPixelMap()
        mainClock.advanceTimeBy(700)
        val after = ring(this).captureToImage().toPixelMap()
        assertSamePixels(before, after)
    }

    @Test fun animatedRingMovesSoTheStaticCheckCanFail() = runComposeUiTest {
        mainClock.autoAdvance = false
        toolbar(menu(listOf(item("a"))))
        waitForIdle()
        val before = ring(this).captureToImage().toPixelMap()
        mainClock.advanceTimeBy(700)
        val after = ring(this).captureToImage().toPixelMap()
        assertFalse(samePixels(before, after), "an indeterminate ring with animations on must move")
    }

    private fun samePixels(a: androidx.compose.ui.graphics.PixelMap, b: androidx.compose.ui.graphics.PixelMap): Boolean =
        a.width == b.width && a.height == b.height &&
            (0 until a.width).all { x -> (0 until a.height).all { y -> a[x, y] == b[x, y] } }

    private fun assertSamePixels(a: androidx.compose.ui.graphics.PixelMap, b: androidx.compose.ui.graphics.PixelMap) =
        assertTrue(samePixels(a, b), "ring changed between frames with animations disabled")

    @Test fun noAnimationsStaticIndeterminateRow() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent { Section(menu(listOf(item("a"))), noAnim = true) }
        waitForIdle()
        val row = onNodeWithTag(ACTIVITY_ROW_TAG)
        val before = row.captureToImage().toPixelMap()
        mainClock.advanceTimeBy(700)
        assertTrue(samePixels(before, row.captureToImage().toPixelMap()), "row bar changed with animations disabled")
    }

    @Test fun animatedIndeterminateRowMovesSoTheStaticCheckCanFail() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent { Section(menu(listOf(item("a")))) }
        waitForIdle()
        val row = onNodeWithTag(ACTIVITY_ROW_TAG)
        val before = row.captureToImage().toPixelMap()
        mainClock.advanceTimeBy(700)
        assertFalse(samePixels(before, row.captureToImage().toPixelMap()), "an indeterminate bar with animations on must move")
    }

    @androidx.compose.runtime.Composable
    private fun Section(activity: ActivityMenuState, noAnim: Boolean = false) {
        AbTheme(disableAnimations = noAnim) { CompositionLocalProvider(LocalStrings provides strings) { Column { ActivityMenuSection(activity) } } }
    }
}
