package net.bible.android.control.activity

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.sharedcore.event.EventSource
import net.bible.sharedcore.log.Log
import net.bible.sharedcore.log.LogLevel
import net.bible.sharedcore.platform.AppForeground
import net.bible.sharedcore.platform.NativeNotification
import net.bible.sharedcore.platform.NativeNotifier
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class NativeNotificationPolicyTest {

    private class FakeNotifier(supportsProgress: Boolean = true) : NativeNotifier {
        private val capability = MutableStateFlow(supportsProgress)
        override var supportsProgress: Boolean
            get() = capability.value
            set(value) { capability.value = value }
        override val progressCapabilityChanges = capability.map { Unit }
        val calls = mutableListOf<String>()
        val shows = mutableListOf<NativeNotification>()
        var throwOnShow = false
        override fun show(n: NativeNotification) {
            calls += "show:${n.id}"
            shows += n
            if (throwOnShow) error("boom")
        }
        override fun remove(id: String) { calls += "remove:$id" }
        override fun setOnActivated(handler: (id: String) -> Unit) {}
    }

    private class Fixture(val scope: TestScope, supportsProgress: Boolean = true, fg: Boolean = false, on: Boolean = true) {
        val items = MutableStateFlow<List<ActivityItem>>(emptyList())
        val notifier = FakeNotifier(supportsProgress)
        val foreground = MutableStateFlow(fg)
        val enabled = MutableStateFlow(on)
        val policy = NativeNotificationPolicy(
            items, notifier, { foreground.value }, enabled, { scope.currentTime }, scope.backgroundScope,
            { "Localized permission" }, { "Finished" },
        ).also { it.start() }

        fun setForeground(v: Boolean) { foreground.value = v; policy.onForegroundChanged(v) }
        fun set(vararg i: ActivityItem) { items.value = i.toList() }
    }

    private fun TestScope.fixture(supportsProgress: Boolean = true, fg: Boolean = false, on: Boolean = true) =
        Fixture(this, supportsProgress, fg, on).also { runCurrent() }

    private fun TestScope.advance(ms: Long) { advanceTimeBy(ms); runCurrent() }

    private fun item(
        id: String = "a", state: ActivityState = ActivityState.RUNNING, kind: ActivityKind = ActivityKind.DOWNLOAD,
        progress: Int? = null, started: Long = 0, finished: Long? = null, message: String? = null,
    ) = ActivityItem(id, kind, "T-$id", state, progress, message, null, started, finished, true)

    @Test
    fun attentionInBackgroundNotifiesOnceAndClears() = runTest {
        val f = fixture()
        val waiting = item(kind = ActivityKind.AI_AGENT, message = "Allow tool?").copy(attention = true)
        f.set(waiting); runCurrent()
        assertEquals(1, f.notifier.shows.size)
        f.notifier.shows.single().let { assertTrue(it.urgent); assertEquals("Localized permission", it.title); assertEquals("Allow tool?", it.body) }
        advance(20_000)
        assertEquals(1, f.notifier.shows.size)
        f.set(waiting.copy(attention = false)); runCurrent()
        assertEquals("remove:andbible-a", f.notifier.calls.last())
        f.set(waiting); runCurrent()
        assertEquals(2, f.notifier.shows.size)
    }

    @Test
    fun agentAttentionNotifiesWithoutProgressSupportLikeIos() = runTest {
        val f = fixture(supportsProgress = false)
        val running = item(kind = ActivityKind.AI_AGENT, message = "Working")
        f.set(running); runCurrent()
        advance(20_000)
        assertTrue(f.notifier.shows.isEmpty())

        f.set(running.copy(attention = true, message = "Allow tool?")); runCurrent()
        val notification = f.notifier.shows.single()
        assertTrue(notification.urgent)
        assertNull(notification.progress)
        assertEquals("Localized permission", notification.title)
        assertEquals("Allow tool?", notification.body)
        f.set(running); runCurrent()
        assertEquals("remove:andbible-a", f.notifier.calls.last())
    }

    @Test
    fun progressIsReplacedByAttentionAndResumesAfterResolution() = runTest {
        val f = fixture()
        val running = item(kind = ActivityKind.AI_AGENT, progress = 25, message = "Working")
        f.set(running); runCurrent()
        advance(10_000)
        f.notifier.shows.single().let { assertEquals(25, it.progress); assertTrue(!it.urgent) }

        f.set(running.copy(attention = true, message = "Allow tool?")); runCurrent()
        assertEquals(listOf("show:andbible-a", "show:andbible-a"), f.notifier.calls)
        f.notifier.shows.last().let {
            assertEquals("andbible-a", it.id); assertTrue(it.urgent)
            assertNull(it.progress); assertEquals("Allow tool?", it.body)
        }
        advance(70_000)
        assertEquals(2, f.notifier.shows.size)

        f.set(running.copy(progress = 50)); runCurrent()
        assertEquals("remove:andbible-a", f.notifier.calls.last())
        advance(9_999)
        assertEquals(2, f.notifier.shows.size)
        advance(1)
        f.notifier.shows.last().let { assertEquals(50, it.progress); assertTrue(!it.urgent) }
        assertEquals(3, f.notifier.shows.size)
    }

    @Test
    fun pendingAttentionNotifiesOnBackgroundAndIsRemovedOnClose() = runTest {
        val f = fixture(fg = true)
        f.set(item(kind = ActivityKind.AI_AGENT, message = "Allow?").copy(attention = true)); runCurrent()
        assertTrue(f.notifier.shows.isEmpty())
        f.setForeground(false); runCurrent()
        assertEquals(1, f.notifier.shows.size)
        f.policy.close()
        assertEquals("remove:andbible-a", f.notifier.calls.last())
    }

    @Test
    fun attentionFailureReplacesItAndPreferenceDoesNotReplay() = runTest {
        val f = fixture()
        val waiting = item(kind = ActivityKind.AI_AGENT, message = "Allow?").copy(attention = true)
        f.set(waiting); runCurrent()
        f.set(waiting.copy(state = ActivityState.FAILED, attention = false, message = "failed")); runCurrent()
        assertEquals(listOf("show:andbible-a", "show:andbible-a"), f.notifier.calls)
        f.policy.close()
        assertEquals(2, f.notifier.calls.size)

        val g = fixture()
        g.set(waiting); runCurrent()
        g.enabled.value = false; runCurrent()
        g.enabled.value = true; runCurrent()
        assertEquals(listOf("show:andbible-a", "remove:andbible-a"), g.notifier.calls)
    }

    @Test
    fun attentionDisappearsOnCancellationOrShortSuccess() = runTest {
        val f = fixture()
        val waiting = item(kind = ActivityKind.AI_AGENT, message = "Allow?").copy(attention = true)
        f.set(waiting); runCurrent()
        f.set(waiting.copy(state = ActivityState.CANCELLED)); runCurrent()
        assertEquals(listOf("show:andbible-a", "remove:andbible-a"), f.notifier.calls)
        f.set(waiting); runCurrent()
        f.set(waiting.copy(state = ActivityState.SUCCEEDED, finishedAt = 100)); runCurrent()
        assertEquals(listOf("show:andbible-a", "remove:andbible-a", "show:andbible-a", "remove:andbible-a"), f.notifier.calls)
    }

    @Test
    fun failureNotifiesUrgentEvenInForeground() = runTest {
        val f = fixture(fg = true)
        f.set(item(state = ActivityState.FAILED, message = "disk full", finished = 5))
        runCurrent()
        assertEquals(listOf("show:andbible-a"), f.notifier.calls)
        f.notifier.shows.single().let {
            assertTrue(it.urgent); assertEquals("T-a", it.title); assertEquals("disk full", it.body); assertNull(it.progress)
        }
    }

    @Test
    fun routedForegroundFailureDoesNotNotifyButBackgroundFailureDoes() = runTest {
        val center = ActivityCenter(backgroundScope) { currentTime }
        val source = SyncActivitySource(center, EventSource<Boolean>()) { "Sync" }
        val router = BackgroundErrorRouter({ true }, source) { it }
        val notifier = FakeNotifier()
        var foreground = true
        val policy = NativeNotificationPolicy(center.items, notifier, { foreground }, MutableStateFlow(true), { currentTime }, backgroundScope, { "Finished" })
        policy.start(); runCurrent()
        router.route("foreground", false, ActivityKind.SYNC); runCurrent()
        assertTrue(notifier.shows.isEmpty())
        router.route("background", true, ActivityKind.SYNC); runCurrent()
        assertEquals(listOf("background"), notifier.shows.map { it.title })
        assertEquals("", notifier.shows.single().body)
        foreground = false
        policy.onForegroundChanged(false); runCurrent()
        router.route("away", false, ActivityKind.SYNC); runCurrent()
        assertEquals(listOf("background", "away"), notifier.shows.map { it.title })
        policy.close(); source.close()
    }

    @Test
    fun longSuccessInBackgroundNotifiesFinished() = runTest {
        val f = fixture()
        f.set(item(state = ActivityState.SUCCEEDED, started = 1000, finished = 11_000))
        runCurrent()
        f.notifier.shows.single().let { assertEquals("Finished", it.body); assertTrue(!it.urgent); assertNull(it.progress) }
    }

    @Test
    fun successAt9999msIsSilent() = runTest {
        val f = fixture()
        f.set(item(state = ActivityState.SUCCEEDED, started = 0, finished = 9_999))
        runCurrent()
        assertTrue(f.notifier.calls.isEmpty())
    }

    @Test
    fun successInForegroundIsSilent() = runTest {
        val f = fixture(fg = true)
        f.set(item(state = ActivityState.SUCCEEDED, started = 0, finished = 60_000))
        runCurrent()
        assertTrue(f.notifier.calls.isEmpty())
    }

    @Test
    fun syncSuccessIsSilentAndRemovesProgress() = runTest {
        val f = fixture()
        f.set(item(kind = ActivityKind.SYNC, progress = 10))
        runCurrent()
        advance(10_000)
        assertEquals(listOf("show:andbible-a"), f.notifier.calls)
        f.set(item(kind = ActivityKind.SYNC, state = ActivityState.SUCCEEDED, started = 0, finished = 20_000))
        runCurrent()
        assertEquals(listOf("show:andbible-a", "remove:andbible-a"), f.notifier.calls)
    }

    @Test
    fun longSuccessReplacesProgressInPlace() = runTest {
        val f = fixture()
        f.set(item(progress = 50)); runCurrent()
        advance(10_000)
        f.set(item(state = ActivityState.SUCCEEDED, progress = 100, finished = 12_000)); runCurrent()
        assertEquals(listOf("show:andbible-a", "show:andbible-a"), f.notifier.calls)
        assertEquals("Finished", f.notifier.shows.last().body)
    }

    @Test
    fun progressOnlyWhenSupportedBackgroundAndAfter10s() = runTest {
        val f = fixture()
        f.set(item(progress = 5))
        advance(9_999)
        assertTrue(f.notifier.calls.isEmpty())
        advance(1) // reaches 10_000 with no new emission: the time trigger shows it
        assertEquals(listOf("show:andbible-a"), f.notifier.calls)
        f.notifier.shows.single().let { assertEquals(5, it.progress); assertEquals("", it.body); assertTrue(!it.indeterminate) }

        val g = fixture(supportsProgress = false)
        g.set(item(id = "b", progress = 5, started = currentTime))
        advance(30_000)
        assertTrue(g.notifier.calls.isEmpty())

        val h = fixture(fg = true)
        h.set(item(id = "c", progress = 5, started = currentTime))
        advance(30_000)
        assertTrue(h.notifier.calls.isEmpty())
    }

    @Test
    fun indeterminateProgressShowsEmptyBody() = runTest {
        val f = fixture()
        f.set(item(progress = null)); runCurrent()
        advance(10_000)
        f.notifier.shows.single().let { assertTrue(it.indeterminate); assertEquals("", it.body); assertNull(it.progress) }
    }

    @Test
    fun progressThrottledToOnePerSecondAndOnlyOnChange() = runTest {
        val f = fixture()
        f.set(item(progress = 10)); runCurrent()
        advance(10_000)
        assertEquals(1, f.notifier.shows.size)
        f.set(item(progress = 10)); runCurrent()
        advance(2_000)
        assertEquals(1, f.notifier.shows.size)
        f.set(item(progress = 20)); runCurrent()
        assertEquals(2, f.notifier.shows.size)
        f.set(item(progress = 30)); runCurrent() // throttled
        f.set(item(progress = 40)); runCurrent()
        assertEquals(2, f.notifier.shows.size)
        advance(999)
        assertEquals(2, f.notifier.shows.size)
        advance(1) // trailing update carries the newest value
        assertEquals(3, f.notifier.shows.size)
        assertEquals(40, f.notifier.shows.last().progress)
    }

    @Test
    fun progressReturnsAfterCapabilityRecoveryWithoutActivityUpdates() = runTest {
        val f = fixture(supportsProgress = false)
        f.set(item(progress = null)); runCurrent()
        advance(20_000)
        assertTrue(f.notifier.shows.isEmpty())
        f.notifier.supportsProgress = true
        advance(10_000)
        assertEquals(1, f.notifier.shows.size)
        f.notifier.supportsProgress = false
        advance(60_000)
        f.notifier.supportsProgress = true
        advance(10_000)
        assertEquals(2, f.notifier.shows.size)
    }

    @Test
    fun downloadFailureNotifiesOnlyWhenAppIsAway() = runTest {
        val center = ActivityCenter(backgroundScope) { currentTime }
        val events = EventSource<net.bible.android.control.download.DocumentStatus>()
        val source = DownloadActivitySource(center, events, { it }, { "$it failed" })
        val notifier = FakeNotifier()
        val policy = NativeNotificationPolicy(center.items, notifier, { true }, MutableStateFlow(true), { currentTime }, backgroundScope, { "Finished" })
        policy.start(); runCurrent()
        events.emit(net.bible.android.control.download.DocumentStatus("one", net.bible.android.control.download.DocumentStatus.DocumentInstallStatus.ERROR_DOWNLOADING, 0)); runCurrent()
        assertTrue(notifier.shows.isEmpty())
        policy.onForegroundChanged(false); runCurrent()
        events.emit(net.bible.android.control.download.DocumentStatus("two", net.bible.android.control.download.DocumentStatus.DocumentInstallStatus.ERROR_DOWNLOADING, 0)); runCurrent()
        assertEquals(listOf("two"), notifier.shows.map { it.title })
        policy.close(); source.close()
    }

    @Test
    fun unchangedProgressIsRefreshedBeforeExpiry() = runTest {
        val f = fixture()
        f.set(item(progress = 10)); runCurrent()
        advance(10_000)
        assertEquals(1, f.notifier.shows.size)
        advance(60_000)
        assertEquals(2, f.notifier.shows.size)
        advance(120_000)
        assertEquals(4, f.notifier.shows.size)
    }

    @Test
    fun messageOnlyProgressChangeGetsTrailingUpdate() = runTest {
        val f = fixture()
        f.set(item(progress = 10, message = "first")); runCurrent()
        advance(10_000)
        f.set(item(progress = 10, message = "second")); runCurrent()
        advance(1_000)
        assertEquals(2, f.notifier.shows.size)
        assertEquals("second", f.notifier.shows.last().body)
    }

    @Test
    fun repeatedFailureFromCenterNotifiesAgainAfterReadAndWithoutRead() = runTest {
        val center = ActivityCenter(backgroundScope) { currentTime }
        val notifier = FakeNotifier()
        val policy = NativeNotificationPolicy(
            center.items, notifier, { false }, MutableStateFlow(true), { currentTime }, backgroundScope, { "Finished" },
        )
        policy.start(); runCurrent()
        center.startOrFail("same", ActivityKind.SYNC, "Sync", "offline"); runCurrent()
        center.markAllRead(); runCurrent()
        advance(1)
        center.startOrFail("same", ActivityKind.SYNC, "Sync", "offline"); runCurrent()
        advance(1)
        center.startOrFail("same", ActivityKind.SYNC, "Sync", "offline"); runCurrent()
        assertEquals(3, notifier.shows.size)
        center.markAllRead(); runCurrent()
        policy.onForegroundChanged(true); runCurrent()
        assertEquals(3, notifier.shows.size)
        policy.close()
    }

    @Test
    fun repeatedUnreadFailuresWithinSameMillisecondNotifyAgain() = runTest {
        val center = ActivityCenter(backgroundScope) { 42L }
        val notifier = FakeNotifier()
        val policy = NativeNotificationPolicy(center.items, notifier, { false }, MutableStateFlow(true), { currentTime }, backgroundScope, { "Finished" })
        policy.start(); runCurrent()
        center.startOrFail("same", ActivityKind.SYNC, "Sync", "offline"); runCurrent()
        center.startOrFail("same", ActivityKind.SYNC, "Sync", "offline"); runCurrent()
        assertEquals(2, notifier.shows.size)
        policy.close()
    }

    @Test
    fun sameFailureIdTwiceUpdatesInPlace() = runTest {
        val f = fixture()
        f.set(item(state = ActivityState.FAILED, message = "one")); runCurrent()
        f.set(item(state = ActivityState.RUNNING)); runCurrent()
        f.set(item(state = ActivityState.FAILED, message = "two")); runCurrent()
        assertEquals(setOf("andbible-a"), f.notifier.shows.map { it.id }.toSet())
        assertEquals(listOf("one", "two"), f.notifier.shows.map { it.body })
        assertTrue(f.notifier.calls.none { it.startsWith("remove") })
    }

    @Test
    fun closeRemovesShownProgress() = runTest {
        val f = fixture()
        f.set(item(id = "p", progress = 1), item(id = "f", state = ActivityState.FAILED)); runCurrent()
        advance(10_000)
        f.notifier.calls.clear()
        f.policy.close(); runCurrent()
        assertEquals(listOf("remove:andbible-p"), f.notifier.calls)
        f.set(item(id = "q", state = ActivityState.FAILED)); runCurrent()
        assertEquals(listOf("remove:andbible-p"), f.notifier.calls)
    }

    @Test
    fun vanishRulesDependOnLastState() = runTest {
        val f = fixture()
        f.set(
            item(id = "run", progress = 1),
            item(id = "fail", state = ActivityState.FAILED),
            item(id = "ok", state = ActivityState.SUCCEEDED, started = 0, finished = 20_000),
        ); runCurrent()
        advance(10_000)
        f.notifier.calls.clear()
        f.set(); runCurrent()
        assertEquals(setOf("remove:andbible-run", "remove:andbible-fail"), f.notifier.calls.toSet())
        assertEquals(2, f.notifier.calls.size)
    }

    @Test
    fun cancelledItemIsRemovedLikeVanishedRunning() = runTest {
        val f = fixture()
        f.set(item(progress = 1)); runCurrent()
        advance(10_000)
        f.set(item(state = ActivityState.CANCELLED)); runCurrent()
        assertEquals(listOf("show:andbible-a", "remove:andbible-a"), f.notifier.calls)
    }

    @Test
    fun foregroundRemovesProgress() = runTest {
        val f = fixture()
        f.set(item(progress = 1)); runCurrent()
        advance(10_000)
        f.setForeground(true); runCurrent()
        assertEquals(listOf("show:andbible-a", "remove:andbible-a"), f.notifier.calls)
        f.set(item(progress = 2)); advance(5_000)
        assertEquals(2, f.notifier.calls.size)
        f.setForeground(false); runCurrent() // shown again for the same id after the window loses focus
        assertEquals("show:andbible-a", f.notifier.calls.last())
    }

    @Test
    fun preferenceOffRemovesAllAndOnDoesNotReplay() = runTest {
        val f = fixture()
        f.set(item(id = "p", progress = 1), item(id = "f", state = ActivityState.FAILED)); runCurrent()
        advance(10_000)
        f.notifier.calls.clear()
        f.enabled.value = false; runCurrent()
        assertEquals(setOf("remove:andbible-p", "remove:andbible-f"), f.notifier.calls.toSet())
        f.notifier.calls.clear()
        f.set(item(id = "p", progress = 2), item(id = "f", state = ActivityState.FAILED), item(id = "g", state = ActivityState.FAILED))
        advance(5_000)
        assertTrue(f.notifier.calls.isEmpty())
        f.enabled.value = true; runCurrent()
        // the failures are baseline (no replay); the still-running item resumes progress updates
        assertEquals(listOf("show:andbible-p"), f.notifier.calls)
        f.set(item(id = "p", progress = 2), item(id = "f", state = ActivityState.FAILED), item(id = "g", state = ActivityState.FAILED),
            item(id = "h", state = ActivityState.FAILED)); runCurrent()
        assertEquals("show:andbible-h", f.notifier.calls.last())
    }

    @Test
    fun notifierExceptionsDoNotStopThePolicyAndAreLoggedOncePerKind() = runTest {
        val logged = mutableListOf<String>()
        val old = Log.sinkOverride
        Log.sinkOverride = { level, _, msg, _ -> if (level == LogLevel.WARN) logged += msg }
        try {
            val f = fixture()
            f.notifier.throwOnShow = true
            f.set(item(id = "x", state = ActivityState.FAILED)); runCurrent()
            f.set(item(id = "x", state = ActivityState.FAILED), item(id = "y", state = ActivityState.FAILED)); runCurrent()
            f.set(item(id = "x", state = ActivityState.FAILED), item(id = "y", state = ActivityState.FAILED), item(id = "z", state = ActivityState.FAILED)); runCurrent()
            assertEquals(listOf("show:andbible-x", "show:andbible-y", "show:andbible-z"), f.notifier.calls)
            assertEquals(1, logged.size)
        } finally {
            Log.sinkOverride = old
        }
    }

    @Test
    fun closeIsSynchronousEvenIfScopeIsCancelledRightAfter() = runTest {
        val f = fixture()
        f.set(item(id = "p", progress = 1)); runCurrent()
        advance(10_000)
        f.notifier.calls.clear()
        f.policy.close()
        backgroundScope.cancel()
        assertEquals(listOf("remove:andbible-p"), f.notifier.calls)
        f.policy.close() // idempotent
        assertEquals(1, f.notifier.calls.size)
    }

    @Test
    fun shortOrForegroundNonSyncSuccessRemovesShownProgress() = runTest {
        val f = fixture()
        f.set(item(id = "short", progress = 1, started = 0), item(id = "fgd", progress = 1, started = 0)); runCurrent()
        advance(10_000)
        f.notifier.calls.clear()
        f.set(item(id = "short", state = ActivityState.SUCCEEDED, started = 0, finished = 9_000), item(id = "fgd", progress = 1, started = 0)); runCurrent()
        assertEquals(listOf("remove:andbible-short"), f.notifier.calls)
        f.notifier.calls.clear()
        f.setForeground(true); runCurrent()
        f.notifier.calls.clear()
        f.set(item(id = "short", state = ActivityState.SUCCEEDED, started = 0, finished = 9_000), item(id = "fgd", state = ActivityState.SUCCEEDED, started = 0, finished = 50_000)); runCurrent()
        assertTrue(f.notifier.calls.isEmpty()) // progress was already removed by entering the foreground
    }

    @Test
    fun neverShownRunningItemVanishingMakesNoCall() = runTest {
        val f = fixture()
        f.set(item(progress = 1, started = 0)); runCurrent() // under 10 s: not shown
        f.set(); runCurrent()
        assertTrue(f.notifier.calls.isEmpty())
    }

    @Test
    fun progressGateCountsFromFirstSightNotFromStartedAt() = runTest {
        val f = fixture()
        advance(3_000)
        f.set(item(progress = 5, started = 987_654_321)); runCurrent() // foreign time base: must not matter
        advance(9_999)
        assertTrue(f.notifier.calls.isEmpty())
        advance(1)
        assertEquals(listOf("show:andbible-a"), f.notifier.calls)
    }

    @Test
    fun failureBodyDropsRepeatedTitlePrefix() = runTest {
        val f = fixture()
        f.set(item(id = "a", state = ActivityState.FAILED, message = "T-a: disk full")); runCurrent()
        f.set(item(id = "a", state = ActivityState.FAILED, message = "T-a: disk full"), item(id = "b", state = ActivityState.FAILED, message = "other")); runCurrent()
        assertEquals(listOf("disk full", "other"), f.notifier.shows.map { it.body })
    }

    @Test
    fun activityIdOfStripsPrefix() = runTest {
        val f = fixture()
        assertEquals("abc", f.policy.activityIdOf("andbible-abc"))
        assertNull(f.policy.activityIdOf("other-abc"))
    }
}
