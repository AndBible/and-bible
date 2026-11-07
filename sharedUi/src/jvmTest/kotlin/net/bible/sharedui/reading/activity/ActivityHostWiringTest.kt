package net.bible.sharedui.reading.activity

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlinx.coroutines.CompletableDeferred
import net.bible.service.cloudsync.CloudSyncPort
import net.bible.service.cloudsync.SyncNow
import net.bible.sharedcore.event.EventSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.runtime.Composable
import kotlin.test.Test
import net.bible.sharedui.MemoryAppSettings
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.bible.android.control.activity.ActivityCenter
import net.bible.android.control.activity.ActivityKind
import net.bible.android.control.activity.ActivityMenuPreference
import net.bible.sharedcore.platform.ActivityMenuDefault
import net.bible.sharedcore.platform.AppSettings
import net.bible.sharedcore.reading.ToolbarState
import net.bible.sharedui.reading.ReadingToolbar
import net.bible.sharedui.reading.ReadingToolbarCallbacks
import net.bible.sharedui.reading.ReadingToolbarIcons
import net.bible.sharedui.strings.LocalStrings
import net.bible.sharedui.strings.iosStrings
import net.bible.sharedui.theme.AbTheme

/** The host glue (#4007): preference + [ActivityCenter] flows -> the toolbar's [ActivityMenuState]. */
@OptIn(ExperimentalTestApi::class)
class ActivityHostWiringTest {
    private val strings = iosStrings("en")
    private val painter = ColorPainter(Color.Gray)
    private val icons = ReadingToolbarIcons(painter, painter, painter, painter, painter, painter, painter, painter, painter)
    private val callbacks = ReadingToolbarCallbacks({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
    private val toolbarState = ToolbarState(
        pageTitle = "John 3", documentTitle = "KJV", syncRunning = false, showBible = false, showCommentary = false,
        showStrongs = false, strongsMode = 0, searchable = false, speakable = false, speakStopped = true,
    )

    private class Fixture(default: Boolean) {
        var now = 0L
        val center = ActivityCenter(CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)) { now }
        val preference = ActivityMenuPreference(MemoryAppSettings(), ActivityMenuDefault { default })
        val wiring = ActivityHostWiring(center, preference, onOpenItem = {})

        /** A running item, past the 1 s debounce so the center shows it. */
        fun runningItem() {
            center.start("dl", ActivityKind.DOWNLOAD, "Downloading KJV")
            now = 5_000L
            center.update("dl", 40)
        }
    }

    private fun androidx.compose.ui.test.ComposeUiTest.host(
        wiring: ActivityHostWiring?, expanded: Boolean = false, seen: MutableList<ActivityMenuState?> = mutableListOf(),
        syncRunning: Boolean = false,
    ) = setContent {
        AbTheme(disableAnimations = true) {
            CompositionLocalProvider(LocalStrings provides strings) {
                ActivityHost(wiring) { activity ->
                    seen += activity
                    ReadingToolbar(toolbarState.copy(syncRunning = syncRunning), icons, callbacks, overflowExpanded = expanded, activity = activity)
                }
            }
        }
    }

    @Test fun togglingPreferenceShowsCurrentWork() = runComposeUiTest {
        val f = Fixture(default = false)
        f.runningItem()
        host(f.wiring, syncRunning = true)
        fun pixels() = onRoot().captureToImage().toPixelMap()
        fun same(a: PixelMap, b: PixelMap) = a.width == b.width && a.height == b.height &&
            (0 until a.width).all { x -> (0 until a.height).all { y -> a[x, y] == b[x, y] } }
        onAllNodesWithTag(ACTIVITY_RING_TAG, useUnmergedTree = true).assertCountEquals(0)
        val offBefore = pixels() // title-side sync glyph shown

        f.preference.set(true)
        waitForIdle()
        onAllNodesWithTag(ACTIVITY_RING_TAG, useUnmergedTree = true).assertCountEquals(1)
        assertFalse(same(offBefore, pixels()), "ring appears and the sync glyph is replaced")

        f.preference.set(false)
        waitForIdle()
        onAllNodesWithTag(ACTIVITY_RING_TAG, useUnmergedTree = true).assertCountEquals(0)
        assertTrue(same(offBefore, pixels()), "ring gone, sync glyph back exactly as before")
    }

    @Test fun menuOpenMarksRead() = runComposeUiTest {
        val f = Fixture(default = true)
        f.center.start("x", ActivityKind.DOWNLOAD, "KJV")
        f.center.fail("x", "boom")
        host(f.wiring, expanded = true)
        waitForIdle()
        assertEquals(0, f.center.unreadFailures.value)
        assertEquals(1, f.center.items.value.size, "the row stays, only the badge goes")
        onAllNodesWithTag(ACTIVITY_BADGE_TAG, useUnmergedTree = true).assertCountEquals(0)
    }

    /** Android's default is off: its host must render today's toolbar until the user opts in. */
    @Test fun androidHostUnchangedWhenPrefOff() = runComposeUiTest {
        val f = Fixture(default = false)
        f.runningItem()
        val seen = mutableListOf<ActivityMenuState?>()
        host(f.wiring, seen = seen)
        waitForIdle()
        assertTrue(seen.isNotEmpty())
        assertTrue(seen.all { it == null }, "off -> ReadingToolbar(activity = null)")
        onAllNodesWithTag(ACTIVITY_RING_TAG, useUnmergedTree = true).assertCountEquals(0)
        onAllNodesWithTag(ACTIVITY_BADGE_TAG, useUnmergedTree = true).assertCountEquals(0)
    }

    @Test fun noWiringIsNull() = runComposeUiTest {
        val seen = mutableListOf<ActivityMenuState?>()
        host(null, seen = seen)
        assertTrue(seen.all { it == null })
    }

    @Test fun stateIsStableWhileNothingChanges() = runComposeUiTest {
        val f = Fixture(default = true)
        f.runningItem()
        val seen = mutableListOf<ActivityMenuState?>()
        host(f.wiring, seen = seen)
        waitForIdle()
        val first = assertNotNull(seen.last())
        f.center.update("dl", 40) // same progress: no new item list value worth a new state
        waitForIdle()
        assertEquals(first.onShowAll, seen.last()!!.onShowAll, "lambdas are remembered, so equality can hold")
        assertEquals(first.onMenuOpened, seen.last()!!.onMenuOpened)
    }

    @Test fun showAllOpensListAndPreferenceOffClosesIt() = runComposeUiTest {
        val f = Fixture(default = true)
        f.runningItem()
        val seen = mutableListOf<ActivityMenuState?>()
        host(f.wiring, seen = seen)
        waitForIdle()
        seen.last()!!.onShowAll()
        waitForIdle()
        onAllNodesWithTag(ACTIVITY_LIST_POPOVER_TAG, useUnmergedTree = true).assertCountEquals(1)
        f.preference.set(false)
        waitForIdle()
        onAllNodesWithTag(ACTIVITY_LIST_POPOVER_TAG, useUnmergedTree = true).assertCountEquals(0)
        assertNull(seen.last())
    }

    /** Counts how often [ProbeContent] is recomposed. */
    private class Counter { var n = 0 }

    /**
     * Stands for the reading screen: it only forwards the state to a reader (the overflow button). It must not
     * recompose when the items change, only the reader may.
     */
    @Composable
    private fun ProbeContent(activity: ActivityMenuState?, counter: Counter) {
        SideEffect { counter.n++ }
        ActivityOverflowButton(icon = painter, contentDescription = "more", activity = activity, onClick = {})
    }

    private fun progressOfRing(t: androidx.compose.ui.test.ComposeUiTest): Float? =
        t.onNodeWithTag(ACTIVITY_RING_TAG, useUnmergedTree = true).fetchSemanticsNode().config
            .getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.current

    private fun androidx.compose.ui.test.ComposeUiTest.probe(
        wiring: ActivityHostWiring, counter: Counter, hostCounter: Counter = Counter(),
    ) = setContent {
        AbTheme(disableAnimations = true) {
            CompositionLocalProvider(LocalStrings provides strings) {
                ActivityHostImpl(wiring, onHostComposed = { hostCounter.n++ }) { activity -> ProbeContent(activity, counter) }
            }
        }
    }

    @Test fun progressUpdatesDoNotRecomposeTheContentButTheReaderSeesThem() = runComposeUiTest {
        val f = Fixture(default = true)
        f.runningItem()
        val counter = Counter()
        val hostCounter = Counter()
        probe(f.wiring, counter, hostCounter)
        waitForIdle()
        assertEquals(0.4f, progressOfRing(this)!!, 0.001f)
        val before = counter.n
        val hostBefore = hostCounter.n
        for (p in listOf(50, 60, 70)) {
            f.center.update("dl", p)
            waitForIdle()
        }
        assertEquals(0.7f, progressOfRing(this)!!, 0.001f, "the reader of the state shows the new progress")
        assertEquals(before, counter.n, "the content must not recompose on a progress update")
        assertEquals(hostBefore, hostCounter.n, "ActivityHost itself must not read the items either")
    }

    @Test fun menuOpenRefreshesSyncAvailability() = runComposeUiTest {
        val f = Fixture(default = true)
        var available = false
        val cloud = object : CloudSyncPort {
            override val signedIn = true
            override suspend fun signIn() = true
            override fun start() = CompletableDeferred(true)
            override suspend fun waitUntilFinished() = Unit
            override val runningChanged = EventSource<Boolean>()
        }
        val sync = SyncNow(cloud, EventSource(), { available }, {}, { true }, { false }, {}, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        val seen = mutableListOf<ActivityMenuState?>()
        host(ActivityHostWiring(f.center, f.preference, syncNow = sync, onOpenItem = {}), seen = seen)
        waitForIdle()
        assertFalse(seen.last()!!.syncNow!!.available.value)
        available = true
        seen.last()!!.onMenuOpened()
        waitForIdle()
        assertTrue(seen.last()!!.syncNow!!.available.value)
        sync.close()
    }

    @Test fun runningChangeDoesNotRecomposeReadingContent() = runComposeUiTest {
        val f = Fixture(default = true)
        val events = EventSource<Boolean>()
        val cloud = object : CloudSyncPort {
            override val signedIn = true
            override suspend fun signIn() = true
            override fun start() = CompletableDeferred(true)
            override suspend fun waitUntilFinished() = Unit
            override val runningChanged = events
        }
        val sync = SyncNow(cloud, EventSource(), { true }, {}, { true }, { false }, {}, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        val counter = Counter()
        val hostCounter = Counter()
        val wiring = ActivityHostWiring(f.center, f.preference, syncNow = sync, onOpenItem = {})
        setContent {
            AbTheme { CompositionLocalProvider(LocalStrings provides strings) {
                ActivityHostImpl(wiring, onHostComposed = { hostCounter.n++ }) { activity ->
                    SyncProbeContent(activity, counter)
                }
            } }
        }
        onNodeWithTag(SYNC_NOW_ROW_TAG).assertIsEnabled()
        val before = counter.n
        val hostBefore = hostCounter.n
        events.emit(true)
        onNodeWithTag(SYNC_NOW_ROW_TAG).assertIsNotEnabled()
        assertEquals(before, counter.n)
        assertEquals(hostBefore, hostCounter.n)
        sync.close()
    }

    @Composable
    private fun SyncProbeContent(activity: ActivityMenuState?, counter: Counter) {
        SideEffect { counter.n++ }
        if (activity != null) ActivityMenuSection(activity)
    }

    @Test fun nothingReadsTheItemsWhilePreferenceIsOff() = runComposeUiTest {
        val f = Fixture(default = false)
        f.runningItem()
        val counter = Counter()
        probe(f.wiring, counter)
        waitForIdle()
        val before = counter.n
        for (p in listOf(50, 60, 70)) {
            f.center.update("dl", p)
            waitForIdle()
        }
        assertEquals(before, counter.n, "preference off: progress events never reach the reading content")
        onAllNodesWithTag(ACTIVITY_RING_TAG, useUnmergedTree = true).assertCountEquals(0)
    }

    private fun routeItem(kind: ActivityKind, state: net.bible.android.control.activity.ActivityState) =
        net.bible.android.control.activity.ActivityItem("x", kind, "t", state, null, null, null, 0L, null, true)

    @Test fun rowsLeadToExistingScreens() {
        val running = net.bible.android.control.activity.ActivityState.RUNNING
        val failed = net.bible.android.control.activity.ActivityState.FAILED
        assertEquals(net.bible.sharedcore.nav.NavRoutes.download(), activityItemRoute(routeItem(ActivityKind.DOWNLOAD, running)))
        assertNull(activityItemRoute(routeItem(ActivityKind.INSTALL, failed)))
        assertEquals(net.bible.sharedcore.nav.NavRoutes.SYNC_SETTINGS, activityItemRoute(routeItem(ActivityKind.SYNC, failed)))
        assertNull(activityItemRoute(routeItem(ActivityKind.SYNC, running)))
        assertNull(activityItemRoute(routeItem(ActivityKind.INDEX, failed)))
        assertNull(activityItemRoute(routeItem(ActivityKind.OTHER, running)))
    }
}
