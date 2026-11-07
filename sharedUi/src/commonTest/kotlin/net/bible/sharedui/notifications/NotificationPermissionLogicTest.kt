package net.bible.sharedui.notifications

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.coroutines.CancellationException
import net.bible.sharedcore.log.Log
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class NotificationPermissionLogicTest {
    @Test fun asksOnce() = runTest {
        var asked = false
        var requests = 0
        val permission = NotificationPermissionLogic({ asked }, { asked = true }, { true }) { requests++; false }
        permission.ensureAsked()
        permission.ensureAsked()
        assertTrue(asked)
        assertEquals(1, requests)
    }

    @Test fun settingOffNeverAsks() = runTest {
        var asked = false
        var requests = 0
        val permission = NotificationPermissionLogic({ asked }, { asked = true }, { false }) { requests++; true }
        permission.ensureAsked()
        assertFalse(asked)
        assertEquals(0, requests)
    }

    @Test fun requestErrorDoesNotConsumeAskAndCanBeRetried() = runTest {
        var asked = false
        var requests = 0
        val permission = NotificationPermissionLogic({ asked }, { asked = true }, { true }) {
            requests++
            if (requests == 1) error("failure")
            true
        }
        var logged = 0
        val original = Log.sinkOverride
        Log.sinkOverride = { _, tag, _, _ -> if (tag == "NotificationPermission") logged++ }
        try {
            permission.ensureAsked()
            assertFalse(asked)
            permission.ensureAsked()
            assertTrue(asked)
            assertEquals(2, requests)
            assertEquals(1, logged)
        } finally {
            Log.sinkOverride = original
        }
    }

    @Test fun settingsFailuresAreIsolatedAndLoggedOnce() = runTest {
        for (failingStep in listOf("enabled", "asked", "mark")) {
            var requests = 0
            var logged = 0
            val original = Log.sinkOverride
            Log.sinkOverride = { _, tag, _, _ -> if (tag == "NotificationPermission") logged++ }
            try {
                val permission = NotificationPermissionLogic(
                    asked = { if (failingStep == "asked") error("asked") else false },
                    markAsked = { if (failingStep == "mark") error("mark") },
                    enabled = { if (failingStep == "enabled") error("enabled") else true },
                    request = { requests++; true },
                )
                repeat(3) { permission.ensureAsked() }
                assertEquals(if (failingStep == "mark") 3 else 0, requests, failingStep)
                assertEquals(1, logged, failingStep)
            } finally {
                Log.sinkOverride = original
            }
        }
    }

    @Test fun settingsCancellationPropagates() = runTest {
        val permission = NotificationPermissionLogic({ false }, {}, { throw CancellationException("cancel") }) { true }
        assertFailsWith<CancellationException> { permission.ensureAsked() }
    }

    @Test fun concurrentCallsAskOnce() = runTest {
        var asked = false
        var requests = 0
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val permission = NotificationPermissionLogic({ asked }, { asked = true }, { true }) {
            requests++
            entered.complete(Unit)
            release.await()
            true
        }
        val first = async { permission.ensureAsked() }
        entered.await()
        val second = async { permission.ensureAsked() }
        try {
            yield()
            assertFalse(second.isCompleted)
            assertFalse(asked)
            assertEquals(1, requests)
        } finally {
            release.complete(Unit)
        }
        first.await()
        second.await()
        assertTrue(asked)
        assertEquals(1, requests)
    }

    @Test fun cancellationPropagatesWithoutConsumingAsk() = runTest {
        var asked = false
        var requests = 0
        val permission = NotificationPermissionLogic({ asked }, { asked = true }, { true }) {
            requests++
            if (requests == 1) throw CancellationException("cancel")
            true
        }
        assertFailsWith<CancellationException> { permission.ensureAsked() }
        assertFalse(asked)
        permission.ensureAsked()
        assertTrue(asked)
        assertEquals(2, requests)
    }
}
