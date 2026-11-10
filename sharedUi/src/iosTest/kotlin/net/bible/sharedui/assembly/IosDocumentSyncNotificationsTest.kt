@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package net.bible.sharedui.assembly

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.android.control.activity.ActivityCenter
import net.bible.android.control.activity.ActivityListRequests
import net.bible.android.control.activity.SystemNotificationsPreference
import net.bible.service.cloudsync.CloudSyncProvider
import net.bible.service.cloudsync.DocumentSyncProvider
import net.bible.service.cloudsync.documents.DocumentSyncProgressTracker
import net.bible.service.cloudsync.host.HostDocumentSyncStarter
import net.bible.sharedlogic.settings.DatabaseAppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Executes on Native: real tracker observation, runtime lifecycle and iOS notifier adapter. */
class IosDocumentSyncNotificationsTest {
    private class Center : UserNotificationCenterApi {
        val added = mutableListOf<List<Any>>()
        val removed = mutableListOf<String>()
        var denied = false
        var attempts = 0
        override fun add(id: String, title: String, body: String, sound: Boolean) {
            attempts++
            if (denied) error("Notifications denied")
            added += listOf(id, title, body, sound)
        }
        override fun removeDelivered(id: String) { removed += id }
        override fun removePending(id: String) = Unit
    }

    private class Fixture(val test: TestScope, enabled: Boolean = true, queueAlreadyRunning: Boolean = false) {
        val tracker = DocumentSyncProgressTracker()
        // Deliberately pause the producer dispatcher: public start/stop still accept tracker edges
        // synchronously, while no network, database or UIKit background task is started by the test.
        val producer = CoroutineScope(SupervisorJob() + StandardTestDispatcher(TestCoroutineScheduler()))
        val starter = HostDocumentSyncStarter(
            scope = producer, documents = DocumentSyncProvider { error("paused producer") },
            cloudSync = CloudSyncProvider { error("paused producer") }, syncErrorMessage = { "Error" },
            begin = { 1uL }, end = {}, invalidTask = ULong.MAX_VALUE, tracker = tracker,
        )
        val center = Center()
        val notifier = IosNativeNotifier(center, IosNotificationActivations())
        val activities = ActivityCenter(test.backgroundScope) { test.currentTime }
        val preference = SystemNotificationsPreference(DatabaseAppSettings({ error("database not open") }, { false }), enabled)
        var foreground = false
        var focusChanged: (() -> Unit)? = null
        var observerCloses = 0
        init { if (queueAlreadyRunning) start() }
        val runtime = IosNotificationRuntime(
            center = activities, notifier = notifier, requests = ActivityListRequests(), preference = preference,
            observerFactory = IosForegroundObserverFactory { callback ->
                focusChanged = callback
                AutoCloseable { observerCloses++ }
            },
            foregroundQuery = { foreground }, tracker = tracker,
            documentSyncNoticeText = { "Queue ${it.status}: ${it.processed}/${it.total}" },
            policyScope = test.backgroundScope, clock = { test.currentTime }, finishedText = { "Finished" },
        )

        fun start() = starter.pushDocuments(listOf("PRIVATE https://user:secret@host/private /private/path"))
        fun close() { runtime.close(); starter.stop(); producer.cancel() }
    }

    @Test
    fun runtimeObservesAcceptedQueueWithoutProgressCapabilityAndRemovesOnStop() = awaitStartupOnMain {
        runTest {
            val f = Fixture(this)
            try {
                f.runtime.policy.start(); runCurrent()
                f.start(); runCurrent()
                assertFalse(f.notifier.supportsProgress)
                advanceTimeBy(9_999); runCurrent()
                assertTrue(f.center.added.isEmpty())
                advanceTimeBy(1); runCurrent()
                assertEquals(1, f.center.added.size)
                assertEquals("andbible-document-sync:1", f.center.added.single()[0])
                assertEquals("Queue RUNNING: 0/1", f.center.added.single()[2])
                assertEquals(false, f.center.added.single()[3])
                assertTrue(f.center.added.single().none { "PRIVATE" in it.toString() || "secret" in it.toString() })
                f.starter.stop(); runCurrent()
                assertEquals(listOf("andbible-document-sync:1"), f.center.removed)
                assertEquals(1, f.center.added.size)
            } finally { f.close() }
        }
    }

    @Test
    fun runtimeReplaysRunningBaselineAndForegroundObserverResetsThreshold() = awaitStartupOnMain {
        runTest {
            val f = Fixture(this, queueAlreadyRunning = true)
            try {
                // Queue acceptance precedes runtime construction; observe must replay its baseline.
                f.runtime.policy.start(); runCurrent()
                advanceTimeBy(9_000); runCurrent()
                f.foreground = true; f.focusChanged!!.invoke()
                f.foreground = false; f.focusChanged!!.invoke()
                runCurrent(); advanceTimeBy(9_999); runCurrent()
                assertTrue(f.center.added.isEmpty())
                advanceTimeBy(1); runCurrent()
                assertEquals(1, f.center.added.size)
            } finally { f.close() }
        }
    }

    @Test
    fun runtimeCloseRemovesStartAndRejectsNewQueuesAndQueuedForegroundCallbacks() = awaitStartupOnMain {
        runTest {
            val f = Fixture(this)
            try {
                f.runtime.policy.start(); f.start(); runCurrent()
                advanceTimeBy(10_000); runCurrent()
                assertEquals(1, f.center.added.size)
                f.runtime.close()
                assertEquals(listOf("andbible-document-sync:1"), f.center.removed)
                assertEquals(1, f.observerCloses)
                f.starter.stop(); f.start()
                f.focusChanged!!.invoke()
                runCurrent(); advanceTimeBy(60_000); runCurrent()
                assertEquals(1, f.center.added.size)
                f.runtime.close()
                assertEquals(1, f.observerCloses)
            } finally { f.close() }
        }
    }

    @Test
    fun deniedOsDeliveryDoesNotRetryOrHideInAppQueueState() = awaitStartupOnMain {
        runTest {
            val f = Fixture(this)
            try {
                f.center.denied = true
                f.runtime.policy.start(); f.start(); runCurrent()
                advanceTimeBy(10_000); runCurrent()
                assertEquals(1, f.center.attempts)
                assertTrue(f.center.added.isEmpty())
                assertEquals(net.bible.sharedcore.cloud.DocumentSyncProgressStatus.RUNNING, f.tracker.state.value.status)
                advanceTimeBy(120_000); runCurrent()
                assertEquals(1, f.center.attempts, "OS denial must not cause progress refresh/repeated prompts")
                f.starter.stop(); runCurrent()
                assertEquals(net.bible.sharedcore.cloud.DocumentSyncProgressStatus.CANCELLED, f.tracker.state.value.status)
            } finally { f.close() }
        }
    }

    @Test
    fun disabledNativeRuntimeKeepsTrackerStateWithoutNoticeOrHistoricalReplay() = awaitStartupOnMain {
        runTest {
            val f = Fixture(this, enabled = false)
            try {
                f.runtime.policy.start(); f.start(); runCurrent()
                advanceTimeBy(20_000); runCurrent()
                assertEquals(net.bible.sharedcore.cloud.DocumentSyncProgressStatus.RUNNING, f.tracker.state.value.status)
                assertTrue(f.center.added.isEmpty())
                f.starter.stop(); runCurrent()
                assertEquals(net.bible.sharedcore.cloud.DocumentSyncProgressStatus.CANCELLED, f.tracker.state.value.status)
                assertTrue(f.center.added.isEmpty())
            } finally { f.close() }
        }
    }
}
