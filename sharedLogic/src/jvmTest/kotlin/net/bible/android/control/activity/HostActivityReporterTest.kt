package net.bible.android.control.activity

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.bible.service.cloudsync.host.HostErrorReportHooks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class HostActivityReporterTest {
    private fun item(cause: Throwable = IllegalStateException("network")) = ActivityItem(
        id = "kjv", kind = ActivityKind.DOWNLOAD, title = "KJV", state = ActivityState.FAILED,
        progress = null, message = "Download failed", cancel = null, startedAt = 0,
        finishedAt = 1, read = false, cause = cause,
    )

    @Test
    fun missingHookIsUnavailableAndDoesNotCrash() = runTest {
        val old = HostErrorReportHooks.report
        try {
            HostErrorReportHooks.report = null
            val reporter = HostActivityReporter(this)
            assertFalse(reporter.available.value)
            reporter.report(item())
            runCurrent()
        } finally {
            HostErrorReportHooks.report = old
        }
    }

    @Test
    fun availabilityTracksHookInstallationAndRemoval() = runTest {
        val old = HostErrorReportHooks.report
        try {
            HostErrorReportHooks.report = null
            val reporter = HostActivityReporter(this)
            assertFalse(reporter.available.value)
            HostErrorReportHooks.report = { }
            assertTrue(reporter.available.value)
            HostErrorReportHooks.report = null
            assertFalse(reporter.available.value)
        } finally {
            HostErrorReportHooks.report = old
        }
    }

    @Test
    fun dispatchIncludesMessageAndOriginalCause() = runTest {
        val old = HostErrorReportHooks.report
        try {
            var received: Throwable? = null
            HostErrorReportHooks.report = { received = it }
            val cause = IllegalArgumentException("original")
            HostActivityReporter(this).report(item(cause))
            runCurrent()
            val report = assertIs<ActivityFailureReport>(received)
            assertEquals("DOWNLOAD failed: KJV: Download failed", report.message)
            assertSame(cause, report.cause)
        } finally {
            HostErrorReportHooks.report = old
        }
    }

    @Test
    fun duplicateIsIgnoredUntilSuspendedReportCompletesThenRetryWorks() = runTest {
        val old = HostErrorReportHooks.report
        try {
            val release = CompletableDeferred<Unit>()
            var calls = 0
            HostErrorReportHooks.report = { calls++; release.await() }
            val reporter = HostActivityReporter(this)
            reporter.report(item())
            reporter.report(item())
            runCurrent()
            assertEquals(1, calls)
            reporter.report(item())
            runCurrent()
            assertEquals(1, calls)
            release.complete(Unit)
            runCurrent()
            reporter.report(item())
            runCurrent()
            assertEquals(2, calls)
        } finally {
            HostErrorReportHooks.report = old
        }
    }

    @Test
    fun cancellationReleasesGuardForRetry() = runTest {
        val old = HostErrorReportHooks.report
        try {
            var calls = 0
            val started = CompletableDeferred<Unit>()
            HostErrorReportHooks.report = {
                calls++
                if (calls == 1) {
                    started.complete(Unit)
                    throw kotlinx.coroutines.CancellationException("dismissed")
                }
            }
            val reporter = HostActivityReporter(this)
            reporter.report(item())
            started.await()
            runCurrent()
            reporter.report(item())
            runCurrent()
            assertEquals(2, calls)
        } finally {
            HostErrorReportHooks.report = old
        }
    }
}
