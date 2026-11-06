package net.bible.sharedui.notifications

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
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

    @Test fun requestErrorIsMarkedAndNotRetried() = runTest {
        var asked = false
        var requests = 0
        val permission = NotificationPermissionLogic({ asked }, { asked = true }, { true }) { requests++; error("failure") }
        var logged = 0
        val original = Log.sinkOverride
        Log.sinkOverride = { _, tag, _, _ -> if (tag == "NotificationPermission") logged++ }
        try {
            permission.ensureAsked()
            permission.ensureAsked()
            assertTrue(asked)
            assertEquals(1, requests)
            assertEquals(1, logged)
        } finally {
            Log.sinkOverride = original
        }
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
        second.await()
        assertTrue(asked)
        assertEquals(1, requests)
        release.complete(Unit)
        first.await()
    }

    @Test fun cancellationPropagates() = runTest {
        val permission = NotificationPermissionLogic({ false }, {}, { true }) { throw CancellationException("cancel") }
        assertFailsWith<CancellationException> { permission.ensureAsked() }
    }
}
